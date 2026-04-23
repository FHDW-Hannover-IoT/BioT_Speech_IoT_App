package com.fhdw.biot.speech.iot.llm;

import android.util.Log;
import androidx.lifecycle.MutableLiveData;
import com.fhdw.biot.speech.iot.BuildConfig;
import com.fhdw.biot.speech.iot.voice.ILlmQueryHandler;
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
 * LlmQueryHandler — pure HTTP client that sends voice transcripts to the LLM
 * backend and publishes results as LiveData.
 *
 * No Activity reference is held here. All side-effects (TTS, navigation,
 * MQTT publish, filter broadcast) are dispatched by the observing Activity
 * when it reacts to {@link #LIVE_ACTION}.
 *
 * Observer pattern:
 *   1. {@link #handleQuery(String)} submits an HTTP task and returns immediately.
 *   2. Background thread calls the LLM endpoint, parses {@link LlmAction}.
 *   3. {@code liveAction.postValue(action)} — LiveData notifies all observers.
 *   4. {@code llmLoading.postValue(false)} — loading indicator clears.
 *
 * Application-scoped: created once in {@code AppContainer.initApplicationScope}.
 * Rotation-safe because no Activity context is stored.
 */
public class LlmQueryHandler implements ILlmQueryHandler {

    private static final String TAG = "LlmQueryHandler";

    private static final String FALLBACK_TTS =
            "Sorry, I didn't catch that. Could you repeat your question?";

    private final MutableLiveData<LlmAction> liveAction;
    private final MutableLiveData<Boolean>   llmLoading;
    private final String                     chatEndpointUrl;
    private final ExecutorService            http;
    private final int                        connectTimeoutMs;
    private final int                        readTimeoutMs;

    public LlmQueryHandler(
            MutableLiveData<LlmAction> liveAction,
            MutableLiveData<Boolean>   llmLoading,
            String                     chatEndpointUrl) {
        this.liveAction      = liveAction;
        this.llmLoading      = llmLoading;
        this.chatEndpointUrl = chatEndpointUrl;
        this.connectTimeoutMs = BuildConfig.LLM_CONNECT_TIMEOUT_MS;
        this.readTimeoutMs    = BuildConfig.LLM_READ_TIMEOUT_MS;
        this.http = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "llm-query-http");
            t.setDaemon(true);
            return t;
        });
        Log.i(TAG, "Ready, endpoint=" + chatEndpointUrl);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ILlmQueryHandler
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void handleQuery(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            postFallback();
            return;
        }
        Log.i(TAG, "handleQuery: " + transcript);
        llmLoading.postValue(true);

        http.execute(() -> {
            try {
                String reply = postChat(transcript);
                Log.i(TAG, "Reply: " + (reply == null ? "null"
                        : reply.substring(0, Math.min(200, reply.length()))));
                LlmAction action = LlmAction.parse(reply);
                liveAction.postValue(action);
            } catch (Exception e) {
                Log.e(TAG, "LLM call failed: " + e.getMessage(), e);
                postFallback();
            } finally {
                llmLoading.postValue(false);
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
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("message", message);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int status = conn.getResponseCode();
            InputStream stream = (status >= 200 && status < 300)
                    ? conn.getInputStream() : conn.getErrorStream();

            if (stream == null) throw new RuntimeException("HTTP " + status + " with no body");

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            if (status < 200 || status >= 300) {
                throw new RuntimeException("HTTP " + status + ": " + sb);
            }

            JSONObject envelope = new JSONObject(sb.toString());
            return envelope.optString("reply", sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    private void postFallback() {
        liveAction.postValue(LlmAction.answer(FALLBACK_TTS));
    }
}
