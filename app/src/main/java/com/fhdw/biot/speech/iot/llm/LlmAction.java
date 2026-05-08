package com.fhdw.biot.speech.iot.llm;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * LlmAction
 * ─────────────────────────────────────────────────────────────────────────────
 * Typed view of the structured JSON response returned by the BioT LLM_App's
 * POST /chat endpoint.
 *
 * Schema (single source of truth: LLM_App/docs/LLM_USE_CASES.md)
 * ──────────────────────────────────────────────────────────────────
 * {
 *   "action":  "answer | navigate | mqtt_publish | apply_filter | clear_filter",
 *   "tts":     "Text to speak aloud via Android TTS",
 *
 *   // Only for action = "navigate":
 *   "screen":  "MainActivity | AccelActivity | GyroActivity | MagnetActivity
 *               | MainGraphActivity | EreignisActivity | SettingsActivity",
 *
 *   // Only for action = "mqtt_publish":
 *   "topic":   "Control/Mode | Control/OperatingMode",
 *   "payload": "STREAM | BURST | AVERAGE | AUTARK | SUPERVISION | EVENT | IDENTIFICATION",
 *
 *   // Only for action = "apply_filter":
 *   "minutes": 10
 * }
 *
 * Parsing rules:
 *  • If the LLM wraps the JSON in extra prose, we strip the first {...} block.
 *  • If the JSON is malformed or missing "action", we fall back to ANSWER with
 *    the raw text as the TTS string so the user still hears something.
 *  • Unknown action types map to ANSWER as well (forward-compatible).
 */
public final class LlmAction {

    public enum Type { ANSWER, NAVIGATE, MQTT_PUBLISH, APPLY_FILTER, CLEAR_FILTER }

    public final Type    type;
    public final String  tts;
    public final String  screen;   // NAVIGATE only
    public final String  topic;    // MQTT_PUBLISH only
    public final String  payload;  // MQTT_PUBLISH only
    public final int     minutes;  // APPLY_FILTER only

    private LlmAction(Type type, String tts,
                      String screen, String topic, String payload, int minutes) {
        this.type    = type;
        this.tts     = tts == null ? "" : tts;
        this.screen  = screen;
        this.topic   = topic;
        this.payload = payload;
        this.minutes = minutes;
    }

    /** Convenience factory used when the LLM didn't return parseable JSON. */
    public static LlmAction answer(String tts) {
        return new LlmAction(Type.ANSWER, tts, null, null, null, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse a /chat reply body into a typed action.
     *
     * Robust to:
     *   • plain prose with no JSON  → ANSWER with the prose as tts
     *   • JSON wrapped in markdown fences (```json ... ```)
     *   • JSON preceded or followed by chatty text
     *   • missing optional fields
     *
     * @param raw the literal "reply" string from POST /chat (never null).
     */
    public static LlmAction parse(String raw) {
        if (raw == null) return answer("");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return answer("");

        String jsonCandidate = extractJsonObject(trimmed);
        if (jsonCandidate == null) {
            // No JSON at all — treat the whole string as a plain answer.
            return answer(trimmed);
        }

        try {
            JSONObject obj = new JSONObject(jsonCandidate);
            String actionStr = obj.optString("action", "answer").toLowerCase();
            String tts       = obj.optString("tts", "");

            switch (actionStr) {
                case "navigate":
                    return new LlmAction(Type.NAVIGATE, tts,
                            obj.optString("screen", null), null, null, 0);

                case "mqtt_publish":
                    return new LlmAction(Type.MQTT_PUBLISH, tts, null,
                            obj.optString("topic", null),
                            obj.optString("payload", null), 0);

                case "apply_filter":
                    return new LlmAction(Type.APPLY_FILTER, tts, null, null, null,
                            obj.optInt("minutes", 0));

                case "clear_filter":
                    return new LlmAction(Type.CLEAR_FILTER, tts, null, null, null, 0);

                case "answer":
                default:
                    // Unknown action types are forward-compatible: speak the tts and stop.
                    return new LlmAction(Type.ANSWER, tts.isEmpty() ? trimmed : tts,
                            null, null, null, 0);
            }
        } catch (JSONException e) {
            // JSON-looking but malformed — speak the raw reply.
            return answer(trimmed);
        }
    }

    /**
     * Find and return the first balanced {...} block in the input, or null.
     * This handles the common case where the model says
     *   "Sure! Here you go: { ... }"
     * or
     *   "```json\n{ ... }\n```"
     */
    private static String extractJsonObject(String text) {
        int firstBrace = text.indexOf('{');
        if (firstBrace < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = firstBrace; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(firstBrace, i + 1);
                }
            }
        }
        return null; // unbalanced — let caller treat as plain answer
    }
}
