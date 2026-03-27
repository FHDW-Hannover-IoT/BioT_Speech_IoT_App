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
 * Dependencies are injected as interfaces:
 *  • {@link IMqttPublisher}   – for mode-switching commands (no MqttHandler import)
 *  • {@link ILlmQueryHandler} – for query commands that need the LLM (Phase 4)
 *
 * This keeps the voice package fully decoupled from mqtt and networking code.
 *
 * @return {@code true}  if the command was fully handled locally.
 *         {@code false} if the command was forwarded to {@link ILlmQueryHandler}.
 */
public final class VoiceCommandExecutor {

    private static final String TOPIC_MODE = "Control/Mode";

    /** Intent extra key used to pass a time-filter window (minutes) to chart Activities. */
    public static final String EXTRA_FILTER_MINUTES = "FILTER_MINUTES";

    private VoiceCommandExecutor() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Execute a resolved voice command.
     *
     * @param activity   The activity that received the voice input (used for navigation/toasts).
     * @param command    The resolved {@link VoiceCommand}.
     * @param mqtt       An {@link IMqttPublisher} (may be null – mode commands will skip silently).
     * @param llm        An {@link ILlmQueryHandler} for QUERY_* commands (may be null in Phase 1).
     * @param transcript The original raw transcript (needed when forwarding to LLM).
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
                Toast.makeText(activity, "Mikrofon-Ansicht: Hauptseite", Toast.LENGTH_SHORT).show();
                navigate(activity, MainActivity.class, null, 0);
                return true;

            case NAV_GRAPH:
                navigate(activity, MainGraphActivity.class, null, 0);
                return true;

            case NAV_EVENTS:
                navigateWithStringExtra(activity, EreignisActivity.class, "SENSOR_FILTER", "ALL");
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
            case FILTER_LAST_5MIN:
                broadcastFilter(activity, 5);
                return true;

            case FILTER_LAST_10MIN:
                broadcastFilter(activity, 10);
                return true;

            case FILTER_LAST_30MIN:
                broadcastFilter(activity, 30);
                return true;

            case FILTER_LAST_1H:
                broadcastFilter(activity, 60);
                return true;

            case FILTER_LAST_24H:
                broadcastFilter(activity, 1440);
                return true;

            case FILTER_CLEAR:
                broadcastFilter(activity, 0);
                return true;

            // ── MQTT MODE CONTROL ─────────────────────────────────────────
            case MODE_STREAM:
                publishMode(mqtt, "STREAM");
                Toast.makeText(activity, "Modus: Stream", Toast.LENGTH_SHORT).show();
                return true;

            case MODE_BURST:
                publishMode(mqtt, "BURST");
                Toast.makeText(activity, "Modus: Burst", Toast.LENGTH_SHORT).show();
                return true;

            case MODE_AVERAGE:
                publishMode(mqtt, "AVERAGE");
                Toast.makeText(activity, "Modus: Durchschnitt", Toast.LENGTH_SHORT).show();
                return true;

            // ── COMBINED VIEWS ────────────────────────────────────────────
            case COMBINED_MOTION:
                navigateWithStringExtra(activity, MainGraphActivity.class, "VIEW_MODE", "MOTION");
                return true;

            case COMBINED_VIBRATION_SOUND:
                navigateWithStringExtra(activity, MainGraphActivity.class, "VIEW_MODE", "VIBRATION_SOUND");
                return true;

            case COMBINED_ORIENTATION_MAGNETIC:
                navigateWithStringExtra(activity, MainGraphActivity.class, "VIEW_MODE", "ORIENTATION_MAGNETIC");
                return true;

            case COMBINED_ALL_SENSORS:
                navigateWithStringExtra(activity, MainGraphActivity.class, "VIEW_MODE", "ALL");
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
                } else {
                    Toast.makeText(activity,
                            "LLM nicht verbunden. Frage kann nicht beantwortet werden.",
                            Toast.LENGTH_SHORT).show();
                }
                return false;

            // ── UNKNOWN ───────────────────────────────────────────────────
            case UNKNOWN:
            default:
                Toast.makeText(activity,
                        "Befehl nicht erkannt. Sag \"Hilfe\" für eine Übersicht.",
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
                ? "Filter zurückgesetzt"
                : "Filter: letzte " + (minutes >= 60
                ? (minutes / 60) + " Stunde(n)"
                : minutes + " Minuten");
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    }

    private static void publishMode(IMqttPublisher mqtt, String mode) {
        if (mqtt != null && mqtt.isConnected()) {
            mqtt.publish(TOPIC_MODE, mode, true);
        }
    }

    private static void showHelpToast(Activity activity) {
        Toast.makeText(activity,
                "Befehle z.B.: \"Zeige Gyro\", \"Letzte 10 Minuten\", " +
                        "\"Burst-Modus\", \"Alle Sensoren\", \"Gibt es Anomalien?\"",
                Toast.LENGTH_LONG).show();
    }
}