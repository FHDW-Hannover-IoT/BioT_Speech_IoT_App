package com.fhdw.biot.speech.iot.voice;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.MainGraphActivity;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.mqtt.IMqttPublisher;
import com.fhdw.biot.speech.iot.sensor.AccelActivity;
import com.fhdw.biot.speech.iot.sensor.GyroActivity;
import com.fhdw.biot.speech.iot.sensor.MagnetActivity;
import com.fhdw.biot.speech.iot.settings.SettingsActivity;

/**
 * VoiceCommandExecutor
 * ─────────────────────────────────────────────────────────────────────────────
 * Bridges a resolved {@link VoiceCommand} to concrete app actions.
 *
 * Dependencies are injected as interfaces (no concrete imports of MqttHandler
 * or any HTTP client):
 *   • {@link IMqttPublisher}   – for mode-switching commands
 *   • {@link ILlmQueryHandler} – for query commands that need the LLM
 *
 * Topic conventions (must match the ESP8266 firmware):
 *   • Control/Mode           → STREAM | BURST | AVERAGE        (transmission cadence)
 *   • Control/OperatingMode  → AUTARK | SUPERVISION |
 *                              EVENT  | IDENTIFICATION         (operating mode)
 *
 * Return value semantics:
 *   {@code true}  – the command was fully handled locally (toast / nav / publish).
 *   {@code false} – the command was forwarded to {@link ILlmQueryHandler};
 *                   the LLM will produce the user-visible response (TTS).
 */
public final class VoiceCommandExecutor {

    /** Topic the ESP8266 listens on for transmission cadence (Stream/Burst/Average). */
    public static final String TOPIC_MODE = "Control/Mode";

    /** Topic the ESP8266 listens on for operating mode (Autark/Supervision/Event/Identification). */
    public static final String TOPIC_OPERATING_MODE = "Control/OperatingMode";

    /** Topic used to request the ESP8266 publish its current operating mode. */
    public static final String TOPIC_OPERATING_MODE_GET = "Control/OperatingMode/Get";

    /** Intent extra key used to pass a time-filter window (minutes) to chart Activities. */
    public static final String EXTRA_FILTER_MINUTES = "FILTER_MINUTES";

    /** Intent extra key used to pre-filter the EreignisActivity by sensor type. */
    public static final String EXTRA_SENSOR_FILTER = "SENSOR_FILTER";

    /** Values for the VIEW_MODE extra passed to MainGraphActivity. */
    public static final String VIEW_MOTION               = "MOTION";
    public static final String VIEW_VIBRATION_SOUND      = "VIBRATION_SOUND";
    public static final String VIEW_ORIENTATION_MAGNETIC = "ORIENTATION_MAGNETIC";
    public static final String VIEW_ALL_SENSORS          = "ALL";

    private VoiceCommandExecutor() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Execute a resolved voice command.
     *
     * @param activity   The activity that received the voice input (used for navigation/toasts).
     * @param command    The resolved {@link VoiceCommand}.
     * @param mqtt       An {@link IMqttPublisher}. May be null — mode commands skip silently then.
     * @param llm        An {@link ILlmQueryHandler}. May be null in early phases — QUERY_*
     *                   and TELL_VALUE will fall back to a polite toast in that case.
     * @param transcript The original raw transcript (needed when forwarding to the LLM).
     * @return {@code true} if handled locally; {@code false} if forwarded to LLM.
     */
    public static boolean execute(
            Activity activity,
            VoiceCommand command,
            IMqttPublisher mqtt,
            ILlmQueryHandler llm,
            String transcript) {

        switch (command) {

            // ── NAVIGATION ────────────────────────────────────────────────
            case NAV_HOME:
                navigate(activity, MainActivity.class, null, 0);
                return true;

            case NAV_ACCEL:
                navigate(activity, AccelActivity.class, null, 0);
                return true;

            case NAV_GYRO:
                navigate(activity, GyroActivity.class, null, 0);
                return true;

            case NAV_MAGNET:
                navigate(activity, MagnetActivity.class, null, 0);
                return true;

            case NAV_MIC:
                Toast.makeText(activity, "Microphone view: home screen", Toast.LENGTH_SHORT).show();
                navigate(activity, MainActivity.class, null, 0);
                return true;

            case NAV_GRAPH:
                navigate(activity, MainGraphActivity.class, null, 0);
                return true;

            case NAV_EVENTS:
                navigateWithStringExtra(activity, EreignisActivity.class, EXTRA_SENSOR_FILTER, "ALL");
                return true;

            case NAV_SETTINGS:
                navigate(activity, SettingsActivity.class, null, 0);
                return true;

            // ── NAVIGATION + FILTER COMBOS ────────────────────────────────
            case NAV_ACCEL_FILTER_10MIN:
                navigate(activity, AccelActivity.class, EXTRA_FILTER_MINUTES, 10);
                return true;

            case NAV_GYRO_FILTER_10MIN:
                navigate(activity, GyroActivity.class, EXTRA_FILTER_MINUTES, 10);
                return true;

            case NAV_MAGNET_FILTER_10MIN:
                navigate(activity, MagnetActivity.class, EXTRA_FILTER_MINUTES, 10);
                return true;

            // ── TIME FILTERS ─────────────────────────────────────────────
            case FILTER_LAST_5MIN:   broadcastFilter(activity, 5);    return true;
            case FILTER_LAST_10MIN:  broadcastFilter(activity, 10);   return true;
            case FILTER_LAST_30MIN:  broadcastFilter(activity, 30);   return true;
            case FILTER_LAST_1H:     broadcastFilter(activity, 60);   return true;
            case FILTER_LAST_24H:    broadcastFilter(activity, 1440); return true;
            case FILTER_CLEAR:       broadcastFilter(activity, 0);    return true;

            // ── TRANSMISSION MODE (Control/Mode) ──────────────────────────
            case MODE_STREAM:
                publish(mqtt, TOPIC_MODE, "STREAM", true);
                Toast.makeText(activity, "Mode: Stream", Toast.LENGTH_SHORT).show();
                return true;

            case MODE_BURST:
                publish(mqtt, TOPIC_MODE, "BURST", true);
                Toast.makeText(activity, "Mode: Burst", Toast.LENGTH_SHORT).show();
                return true;

            case MODE_AVERAGE:
                publish(mqtt, TOPIC_MODE, "AVERAGE", true);
                Toast.makeText(activity, "Mode: Average", Toast.LENGTH_SHORT).show();
                return true;

            // ── OPERATING MODE (Control/OperatingMode) ───────────────────
            case OPMODE_AUTARK:
                publish(mqtt, TOPIC_OPERATING_MODE, "AUTARK", true);
                Toast.makeText(activity, "Operating Mode: Autark (power saving)", Toast.LENGTH_SHORT).show();
                return true;

            case OPMODE_SUPERVISION:
                publish(mqtt, TOPIC_OPERATING_MODE, "SUPERVISION", true);
                Toast.makeText(activity, "Operating Mode: Supervision", Toast.LENGTH_SHORT).show();
                return true;

            case OPMODE_EVENT:
                publish(mqtt, TOPIC_OPERATING_MODE, "EVENT", true);
                Toast.makeText(activity, "Operating Mode: Event (threshold-based)", Toast.LENGTH_SHORT).show();
                return true;

            case OPMODE_IDENTIFICATION:
                publish(mqtt, TOPIC_OPERATING_MODE, "IDENTIFICATION", true);
                Toast.makeText(activity, "Operating Mode: Identification", Toast.LENGTH_SHORT).show();
                return true;

            case OPMODE_GET:
                // Two paths: ask the ESP8266 to re-publish the retained value (fast path),
                // and ALSO let the LLM contextualise the answer if it's available.
                publish(mqtt, TOPIC_OPERATING_MODE_GET, "?", false);
                if (llm != null && transcript != null && !transcript.isEmpty()) {
                    llm.handleQuery(transcript);
                    return false;
                }
                Toast.makeText(activity, "Asked sensor for current mode…", Toast.LENGTH_SHORT).show();
                return true;

            // ── CALIBRATION & EPSILON (rails laid; feature work comes later) ──
            case START_CALIBRATION:
                Toast.makeText(activity,
                        "Calibration command received. Calibration UI is not yet implemented.",
                        Toast.LENGTH_LONG).show();
                return true;

            case SET_EPSILON:
                // Forward to the LLM if available — it can extract sensor + axis from the transcript.
                if (llm != null && transcript != null && !transcript.isEmpty()) {
                    llm.handleQuery(transcript);
                    return false;
                }
                Toast.makeText(activity,
                        "Set-epsilon recognised. Use the Settings screen to adjust epsilon manually.",
                        Toast.LENGTH_LONG).show();
                return true;

            // ── EVENT MANAGEMENT ─────────────────────────────────────────
            case CREATE_EVENT:
                // The .adoc says "Create event (sensor) (threshold)" — extracting those
                // entities reliably needs the LLM. Fall back to opening the events screen.
                if (llm != null && transcript != null && !transcript.isEmpty()) {
                    llm.handleQuery(transcript);
                    return false;
                }
                navigateWithStringExtra(activity, EreignisActivity.class, EXTRA_SENSOR_FILTER, "ALL");
                Toast.makeText(activity,
                        "Open the events screen to define a new threshold rule.",
                        Toast.LENGTH_LONG).show();
                return true;

            case SHOW_EVENTS:
                navigateWithStringExtra(activity, EreignisActivity.class, EXTRA_SENSOR_FILTER, "ALL");
                return true;

            case SHOW_NOTIFICATIONS:
                // The .adoc separates "events" (rules) from "notifications" (triggered events).
                // The current EreignisActivity shows triggered events, so it covers both for now.
                navigateWithStringExtra(activity, EreignisActivity.class, EXTRA_SENSOR_FILTER, "ALL");
                return true;

            // ── TELL VALUE ───────────────────────────────────────────────
            case TELL_VALUE:
                // Always needs the LLM — it has to extract the sensor and (optional) axis
                // from a sentence like "tell me the gyro X value" and read the latest row.
                if (llm != null && transcript != null && !transcript.isEmpty()) {
                    llm.handleQuery(transcript);
                    return false;
                }
                Toast.makeText(activity,
                        "Cannot read value: LLM not connected.",
                        Toast.LENGTH_SHORT).show();
                return true;

            // ── COMBINED VIEWS ────────────────────────────────────────────
            case COMBINED_MOTION:
                navigateWithStringExtra(activity, MainGraphActivity.class, "VIEW_MODE", VIEW_MOTION);
                return true;

            case COMBINED_VIBRATION_SOUND:
                navigateWithStringExtra(activity, MainGraphActivity.class, "VIEW_MODE", VIEW_VIBRATION_SOUND);
                return true;

            case COMBINED_ORIENTATION_MAGNETIC:
                navigateWithStringExtra(activity, MainGraphActivity.class, "VIEW_MODE", VIEW_ORIENTATION_MAGNETIC);
                return true;

            case COMBINED_ALL_SENSORS:
                navigateWithStringExtra(activity, MainGraphActivity.class, "VIEW_MODE", VIEW_ALL_SENSORS);
                return true;

            // ── SYSTEM ────────────────────────────────────────────────────
            case SYSTEM_HELP:
                showHelpToast(activity);
                return true;

            // ── QUERIES → delegate to LLM ─────────────────────────────────
            case QUERY_ACCEL_VALUE:
            case QUERY_GYRO_VALUE:
            case QUERY_MAGNET_STATUS:
            case QUERY_MIC_LEVEL:
            case QUERY_ANOMALY:
            case QUERY_RECENT_EVENTS:
                if (llm != null && transcript != null && !transcript.isEmpty()) {
                    llm.handleQuery(transcript);
                    return false;
                }
                Toast.makeText(activity,
                        "LLM not connected. Question can't be answered.",
                        Toast.LENGTH_SHORT).show();
                return true;

            // ── UNKNOWN → fall back to LLM if connected ───────────────────
            case UNKNOWN:
            default:
                if (llm != null && transcript != null && !transcript.isEmpty()) {
                    // The LLM might still understand the natural-language phrasing
                    // even if our keyword dictionary missed it.
                    llm.handleQuery(transcript);
                    return false;
                }
                Toast.makeText(activity,
                        "Command not recognised. Say \"Help\" for an overview.",
                        Toast.LENGTH_SHORT).show();
                return true;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static void navigate(Activity from, Class<?> to, String extraKey, int extraValue) {
        Intent intent = new Intent(from, to);
        if (extraKey != null) intent.putExtra(extraKey, extraValue);
        from.startActivity(intent);
    }

    private static void navigateWithStringExtra(Activity from, Class<?> to,
                                                String key, String value) {
        Intent intent = new Intent(from, to);
        intent.putExtra(key, value);
        from.startActivity(intent);
    }

    private static void broadcastFilter(Activity activity, int minutes) {
        Intent broadcast = new Intent("com.fhdw.biot.speech.iot.FILTER_ACTION");
        broadcast.putExtra(EXTRA_FILTER_MINUTES, minutes);
        activity.sendBroadcast(broadcast);

        String msg = minutes == 0
                ? "Filter: reset"
                : "Filter: last "
                  + (minutes >= 60 ? (minutes / 60) + " hour(s)" : minutes + " minutes");
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    }

    private static void publish(IMqttPublisher mqtt, String topic, String payload, boolean retained) {
        if (mqtt != null && mqtt.isConnected()) {
            mqtt.publish(topic, payload, retained);
        }
    }

    private static void showHelpToast(Activity activity) {
        Toast.makeText(activity,
                "Try: \"Show Gyro\", \"last 10 minutes\", \"Burst mode\", " +
                "\"Supervision mode\", \"tell me the accel value\", \"any anomalies?\"",
                Toast.LENGTH_LONG).show();
    }
}
