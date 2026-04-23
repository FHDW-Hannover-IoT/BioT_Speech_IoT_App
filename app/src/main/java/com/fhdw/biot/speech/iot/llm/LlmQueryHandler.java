package com.fhdw.biot.speech.iot.llm;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.MainGraphActivity;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.mqtt.IMqttPublisher;
import com.fhdw.biot.speech.iot.sensor.AccelActivity;
import com.fhdw.biot.speech.iot.sensor.GyroActivity;
import com.fhdw.biot.speech.iot.sensor.MagnetActivity;
import com.fhdw.biot.speech.iot.settings.SettingsActivity;
import com.fhdw.biot.speech.iot.voice.ILlmQueryHandler;
import com.fhdw.biot.speech.iot.voice.TtsManager;
import com.fhdw.biot.speech.iot.voice.VoiceCommandExecutor;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LlmQueryHandler — GitHub Issue #11
 * ─────────────────────────────────────────────────────────────────────────────
 * Concrete implementation of {@link ILlmQueryHandler} that:
 *   1. Sends the raw transcript to LLM_App's POST /chat endpoint.
 *   2. Parses the structured JSON response into an {@link LlmAction}.
 *   3. Dispatches the action: navigate / mqtt_publish / apply_filter / clear_filter / answer.
 *   4. Always speaks the {@code tts} field via {@link TtsManager} (with a fallback prompt
 *      if the LLM returned UNKNOWN_INTENT or invalid JSON).
 *
 * Threading:
 *   • HTTP runs on a background single-thread executor.
 *   • UI dispatch (Toast, startActivity, broadcast) runs on the main thread.
 *
 * Dependencies (all injected via constructor — easy to swap in tests):
 *   • Activity context (for navigation + Toasts)
 *   • TtsManager (for read-back)
 *   • IMqttPublisher (for mqtt_publish actions)
 *   • Endpoint URL (e.g. http://10.0.2.2:8001/chat) — comes from BuildConfig
 *
 * Acceptance criteria (Issue #11):
 *   ✓ Parses LLM JSON output, extracting intent and parameters
 *   ✓ Routes specific intents via switch on {@link LlmAction.Type}
 *   ✓ On UNKNOWN_INTENT or invalid JSON, speaks the predefined fallback prompt
 */
public class LlmQueryHandler implements ILlmQueryHandler {

    private static final String TAG = "LlmQueryHandler";

    /** How long to wait for the LLM_App to respond. Long enough for a slow first-token. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 30_000;

    /** What we say when the LLM gives us nothing useful (Issue #11 fallback). */
    private static final String FALLBACK_TTS =
            "Sorry, I didn't catch that. Could you repeat your question?";

    private final Activity        activity;
    private final TtsManager      tts;
    private final IMqttPublisher  mqtt;
    private final String          chatEndpointUrl;
    private final ExecutorService http;
    private final Handler         mainHandler;

    public LlmQueryHandler(Activity activity,
                           TtsManager tts,
                           IMqttPublisher mqtt,
                           String chatEndpointUrl) {
        this.activity        = activity;
        this.tts             = tts;
        this.mqtt            = mqtt;
        this.chatEndpointUrl = chatEndpointUrl;
        this.http            = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "llm-query-http");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "LlmQueryHandler ready, endpoint=" + chatEndpointUrl);
    }

    /** Call from {@link Activity#} to stop the HTTP executor. */
    public void shutdown() {
        http.shutdownNow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ILlmQueryHandler
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void handleQuery(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            speakFallback();
            return;
        }
        Log.i(TAG, "handleQuery: " + transcript);

        // Visual feedback that we're thinking
        mainHandler.post(() ->
                Toast.makeText(activity, "Asking BioT…", Toast.LENGTH_SHORT).show());

        http.execute(() -> {
            try {
                String reply = postChat(transcript);
                Log.i(TAG, "Reply: " + (reply == null ? "null" : reply.substring(0, Math.min(200, reply.length()))));
                LlmAction action = LlmAction.parse(reply);
                mainHandler.post(() -> dispatch(action));
            } catch (Exception e) {
                Log.e(TAG, "LLM call failed: " + e.getMessage(), e);
                mainHandler.post(this::speakFallback);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP
    // ─────────────────────────────────────────────────────────────────────────

    private String postChat(String message) throws Exception {
        URL url = new URL(chatEndpointUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("message", message);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int status = conn.getResponseCode();
            InputStream stream = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (stream == null) {
                throw new RuntimeException("HTTP " + status + " with no body");
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            if (status < 200 || status >= 300) {
                throw new RuntimeException("HTTP " + status + ": " + sb);
            }

            // Server returns { "reply": "..." }. Extract that — LlmAction.parse handles
            // both raw prose and JSON-shaped replies, so we don't need to do more here.
            JSONObject envelope = new JSONObject(sb.toString());
            return envelope.optString("reply", sb.toString());

        } finally {
            conn.disconnect();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Action dispatch  (always on the main thread)
    // ─────────────────────────────────────────────────────────────────────────

    private void dispatch(LlmAction action) {
        Log.i(TAG, "Dispatching action=" + action.type + " tts=" + action.tts);

        // 1. Always speak the tts field first (or fallback if empty).
        speak(action.tts);

        // 2. Perform the side-effect for the action type.
        switch (action.type) {
            case NAVIGATE:
                handleNavigate(action.screen);
                break;

            case MQTT_PUBLISH:
                handleMqttPublish(action.topic, action.payload);
                break;

            case APPLY_FILTER:
                broadcastFilter(action.minutes);
                break;

            case CLEAR_FILTER:
                broadcastFilter(0);
                break;

            case ANSWER:
            default:
                // Pure data answer — nothing to do beyond the TTS read-out.
                break;
        }
    }

    private void handleNavigate(String screenName) {
        Class<?> target = resolveScreen(screenName);
        if (target == null) {
            Log.w(TAG, "Unknown screen: " + screenName);
            return;
        }
        activity.startActivity(new Intent(activity, target));
    }

    private void handleMqttPublish(String topic, String payload) {
        if (mqtt == null || !mqtt.isConnected()) {
            Toast.makeText(activity, "MQTT not connected — command not sent", Toast.LENGTH_SHORT).show();
            return;
        }
        if (topic == null || payload == null) {
            Log.w(TAG, "mqtt_publish missing topic or payload");
            return;
        }
        mqtt.publish(topic, payload, true);
    }

    private void broadcastFilter(int minutes) {
        Intent broadcast = new Intent("com.fhdw.biot.speech.iot.FILTER_ACTION");
        broadcast.putExtra(VoiceCommandExecutor.EXTRA_FILTER_MINUTES, minutes);
        activity.sendBroadcast(broadcast);
    }

    private void speak(String text) {
        if (tts == null) return;
        if (text == null || text.trim().isEmpty()) {
            tts.speak(FALLBACK_TTS);
        } else {
            tts.speak(text);
        }
    }

    private void speakFallback() {
        Toast.makeText(activity, FALLBACK_TTS, Toast.LENGTH_SHORT).show();
        if (tts != null) tts.speak(FALLBACK_TTS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen name → Activity class
    // ─────────────────────────────────────────────────────────────────────────

    private static Class<?> resolveScreen(String screenName) {
        if (screenName == null) return null;
        switch (screenName) {
            case "MainActivity":      return MainActivity.class;
            case "AccelActivity":     return AccelActivity.class;
            case "GyroActivity":      return GyroActivity.class;
            case "MagnetActivity":    return MagnetActivity.class;
            case "MainGraphActivity": return MainGraphActivity.class;
            case "EreignisActivity":  return EreignisActivity.class;
            case "SettingsActivity":  return SettingsActivity.class;
            default:                  return null;
        }
    }
}
