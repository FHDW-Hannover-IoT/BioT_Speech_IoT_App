package com.fhdw.biot.speech.iot.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * VoiceInputManager
 * ─────────────────────────────────────────────────────────────────────────────
 * Wraps Android's {@link SpeechRecognizer} for push-to-talk voice input
 * WITHOUT showing the Google dialog popup.
 *
 * Why not use startActivityForResult?
 * ─────────────────────────────────────
 * The approach in MainActivity used startActivityForResult which always shows
 * Google's speech dialog. This class uses SpeechRecognizer directly so:
 *  - No popup — the UI button itself is the visual cue
 *  - The caller controls the listening state (startListening / stopListening)
 *  - Results are delivered via {@link VoiceResultListener} callback
 *
 * Lifecycle rules (IMPORTANT)
 * ─────────────────────────────
 * • Must be created on the MAIN thread (SpeechRecognizer requirement).
 * • Call {@link #destroy()} in Activity.onDestroy() to avoid leaks.
 * • Do NOT call startListening() while already listening — call
 *   stopListening() first, or check {@link #isListening()}.
 *
 * Usage in MainActivity
 * ──────────────────────
 * <pre>
 *   voiceInputManager = new VoiceInputManager(this, (transcript, hypotheses) -> {
 *       VoiceCommand cmd = VoiceCommandResolver.resolveFromList(hypotheses);
 *       VoiceCommandExecutor.execute(this, cmd, mqttHandler, llmHandler, transcript);
 *   });
 *
 *   btnVoice.setOnClickListener(v -> {
 *       if (voiceInputManager.isListening()) {
 *           voiceInputManager.stopListening();
 *       } else {
 *           voiceInputManager.startListening();
 *       }
 *   });
 * </pre>
 */
public class VoiceInputManager {

    private static final String TAG = "VoiceInputManager";

    // ── Callback interface ────────────────────────────────────────────────────

    /**
     * Delivered on the main thread when speech recognition completes.
     */
    public interface VoiceResultListener {
        /**
         * @param topResult   The single best transcript (never null, may be empty).
         * @param hypotheses  All ranked hypotheses from the recognizer (for resolveFromList).
         */
        void onResult(String topResult, List<String> hypotheses);

        /**
         * Called when recognition fails or the user stays silent.
         *
         * @param errorCode One of {@link SpeechRecognizer}.ERROR_* constants.
         * @param message   Human-readable description.
         */
        default void onError(int errorCode, String message) {
            Log.w(TAG, "Voice error " + errorCode + ": " + message);
        }

        /**
         * Called when the recognizer has started listening (microphone is open).
         * Use this to update the button icon to "recording" state.
         */
        default void onListeningStarted() {}

        /**
         * Called when the recognizer finishes (either result or error).
         * Use this to restore the button icon to "idle" state.
         */
        default void onListeningEnded() {}
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final SpeechRecognizer recognizer;
    private final Intent           recognizerIntent;
    private final VoiceResultListener listener;

    private boolean listening = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param context  Must be an Activity context (NOT application context).
     *                 SpeechRecognizer requires it.
     * @param listener Callback receiving results and state changes.
     * @throws IllegalStateException if the device does not support speech recognition.
     */
    public VoiceInputManager(Context context, VoiceResultListener listener) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw new IllegalStateException(
                    "SpeechRecognizer not available on this device. " +
                            "Make sure Google app is installed.");
        }

        this.listener = listener;

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(buildRecognitionListener());

        // Build the intent once — reused on every startListening() call
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        // Return up to 5 ranked hypotheses so VoiceCommandResolver can pick the best match
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        // Do not play the "beep" sound (less disruptive in an IoT context)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start listening. If already listening, this call is ignored.
     * Requires RECORD_AUDIO permission to be granted at runtime.
     */
    public void startListening() {
        if (listening) {
            Log.d(TAG, "startListening() ignored — already listening.");
            return;
        }
        Log.d(TAG, "startListening()");
        listening = true;
        recognizer.startListening(recognizerIntent);
    }

    /**
     * Stop listening early (e.g. user releases a push-to-talk button).
     * The recognizer will process whatever it has heard so far.
     */
    public void stopListening() {
        if (!listening) return;
        Log.d(TAG, "stopListening()");
        recognizer.stopListening();
        // listening = false is set in onEndOfSpeech / onError callbacks
    }

    /** @return true if the microphone is currently open and recording. */
    public boolean isListening() {
        return listening;
    }

    /**
     * Release all resources. Must be called in Activity.onDestroy().
     * After this, the instance cannot be reused.
     */
    public void destroy() {
        recognizer.cancel();
        recognizer.destroy();
        listening = false;
        Log.d(TAG, "VoiceInputManager destroyed.");
    }

    // ── Internal RecognitionListener ─────────────────────────────────────────

    private RecognitionListener buildRecognitionListener() {
        return new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "onReadyForSpeech");
                listener.onListeningStarted();
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Volume level — could animate the button here if desired
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech");
                listening = false;
                listener.onListeningEnded();
            }

            @Override
            public void onError(int error) {
                listening = false;
                listener.onListeningEnded();

                String msg = errorToString(error);
                Log.w(TAG, "onError: " + error + " → " + msg);
                listener.onError(error, msg);
            }

            @Override
            public void onResults(Bundle results) {
                listening = false;
                listener.onListeningEnded();
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);

                System.out.println("STT-Output" + matches);

                if (matches == null || matches.isEmpty()) {
                    Log.w(TAG, "onResults: empty result list");
                    listener.onError(SpeechRecognizer.ERROR_NO_MATCH, "No results");
                    return;
                }

                String top = matches.get(0);
                Log.i(TAG, "onResults: top=\"" + top + "\" total=" + matches.size());
                listener.onResult(top, matches);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Partial results not used in push-to-talk mode
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        };
    }

    // ── Error helper ──────────────────────────────────────────────────────────

    private static String errorToString(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:               return "Audio-Error";
            case SpeechRecognizer.ERROR_CLIENT:              return "Client-Error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:             return "Network Error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:     return "Network-Timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:            return "No results";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:     return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:              return "Server-Error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:      return "No language detected";
            default:                                          return "Unknown error (" + error + ")";
        }
    }
}