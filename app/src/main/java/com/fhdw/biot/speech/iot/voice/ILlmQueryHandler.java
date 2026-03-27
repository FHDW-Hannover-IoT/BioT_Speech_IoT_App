package com.fhdw.biot.speech.iot.voice;

/**
 * ILlmQueryHandler
 * ─────────────────────────────────────────────────────────────────────────────
 * Callback interface that receives voice commands which could NOT be handled
 * locally and must be forwarded to the LLM_App (Python FastAPI / Phase 4).
 *
 * Why this interface exists
 * ─────────────────────────
 * {@link VoiceCommandExecutor#execute} returns {@code false} for QUERY_*
 * commands because those require the LLM to answer (e.g. "Gibt es Anomalien?").
 *
 * Rather than the executor calling the HTTP layer directly (tight coupling),
 * it delegates via this interface.  The concrete implementation lives in
 * MainActivity (or a dedicated LlmBridge class) and knows the FastAPI URL,
 * authentication token, etc.
 *
 * This also means the entire voice package has zero HTTP / Retrofit dependencies.
 *
 * Implementations (Phase 4)
 * ─────────────────────────
 *  • {@code LlmBridge}  – sends the transcript to POST /chat on LLM_App,
 *                         receives a text response, triggers TTS read-back.
 *  • A stub in tests    – captures calls without real network I/O.
 *
 * Usage in MainActivity
 * ─────────────────────
 * <pre>
 *   ILlmQueryHandler llm = transcript -> {
 *       llmBridge.sendQuery(transcript, response ->
 *           tts.speak(response));
 *   };
 *
 *   boolean handled = VoiceCommandExecutor.execute(this, cmd, mqttPublisher, llm);
 * </pre>
 */
public interface ILlmQueryHandler {

    /**
     * Forward a raw voice transcript to the LLM for interpretation and response.
     *
     * <p>The implementation is responsible for:
     * <ol>
     *   <li>Sending the transcript to the LLM_App {@code /chat} endpoint.</li>
     *   <li>Receiving the natural-language response.</li>
     *   <li>Optionally reading the response aloud via TTS (Phase 3 / Issue #14).</li>
     * </ol>
     *
     * @param transcript The raw text recognised by Android SpeechRecognizer.
     *                   Never null or empty when this is called.
     */
    void handleQuery(String transcript);
}