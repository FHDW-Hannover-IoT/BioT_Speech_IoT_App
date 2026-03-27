package com.fhdw.biot.speech.iot.voice;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * TtsManager
 * ─────────────────────────────────────────────────────────────────────────────
 * Thin wrapper around Android's {@link TextToSpeech} engine.
 *
 * Responsibilities
 * ─────────────────
 *  - Initialise the TTS engine in German (de-DE).
 *  - Speak a string immediately, interrupting any current speech.
 *  - Speak a string queued after current speech finishes.
 *  - Provide a ready-check so callers don't speak before init completes.
 *  - Release resources cleanly in {@link #destroy()}.
 *
 * Usage in MainActivity
 * ──────────────────────
 * <pre>
 *   ttsManager = new TtsManager(this);
 *
 *   // Speak confirmation after a voice command is executed:
 *   ttsManager.speak("Öffne Gyroskop");
 *
 *   // In onDestroy():
 *   ttsManager.destroy();
 * </pre>
 *
 * Lifecycle rules
 * ────────────────
 *  • Create on the main thread.
 *  • Call {@link #destroy()} in Activity.onDestroy() — never reuse after that.
 *  • {@link #speak(String)} is safe to call before init completes; it queues
 *    the utterance and plays it once the engine is ready.
 */
public class TtsManager {

    private static final String TAG = "TtsManager";

    private TextToSpeech tts;
    private boolean      ready = false;

    /** Pending utterance spoken as soon as TTS finishes initialising. */
    private String pendingUtterance = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param context Activity or application context.
     */
    public TtsManager(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.GERMAN);
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fall back to English if German TTS data is not installed
                    tts.setLanguage(Locale.ENGLISH);
                    Log.w(TAG, "German TTS not available, falling back to English.");
                }
                // Slightly slower speech rate — easier to understand in noisy environments
                tts.setSpeechRate(0.9f);
                ready = true;
                Log.i(TAG, "TTS engine ready.");

                // Play anything that was requested before init finished
                if (pendingUtterance != null) {
                    speakNow(pendingUtterance);
                    pendingUtterance = null;
                }
            } else {
                Log.e(TAG, "TTS init failed, status=" + status);
            }
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Speak {@code text} immediately, cutting off any current speech.
     * Safe to call before the engine is fully initialised — the text is queued
     * and spoken once ready.
     *
     * @param text The German text to speak.
     */
    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!ready) {
            pendingUtterance = text;   // will be played once engine is ready
            return;
        }
        speakNow(text);
    }

    /**
     * Speak {@code text} after the current utterance finishes (queue mode).
     *
     * @param text The German text to speak.
     */
    public void speakQueued(String text) {
        if (text == null || text.trim().isEmpty() || !ready) return;
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, String.valueOf(text.hashCode()));
    }

    /**
     * Stop any ongoing speech immediately.
     */
    public void stop() {
        if (tts != null) tts.stop();
    }

    /**
     * @return {@code true} if the TTS engine has finished initialising and is ready to speak.
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Release the TTS engine. Must be called in Activity.onDestroy().
     * After this call the instance cannot be reused.
     */
    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ready = false;
            Log.d(TAG, "TtsManager destroyed.");
        }
    }

    // ── Internal helper ───────────────────────────────────────────────────────

    private void speakNow(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(text.hashCode()));
        Log.d(TAG, "Speaking: \"" + text + "\"");
    }
}