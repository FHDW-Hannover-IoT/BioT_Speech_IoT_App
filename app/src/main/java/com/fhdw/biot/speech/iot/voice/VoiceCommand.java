package com.fhdw.biot.speech.iot.voice;

/**
 * VoiceCommand
 * ─────────────────────────────────────────────────────────────────────────────
 * Every intent the voice system can recognise is listed here as an enum value.
 *
 * Intent groups
 * ─────────────────────────────────────────────────────────────────────────────
 *  NAVIGATION   – open a screen
 *  FILTER       – apply a time / sensor filter on charts
 *  MODE         – switch the ESP8266 sampling mode via MQTT
 *  QUERY        – ask a question about a sensor value (for LLM phase)
 *  COMBINED     – commands that involve more than one sensor at once
 *  SYSTEM       – app-level commands (settings, home, help, …)
 *  UNKNOWN      – nothing matched
 */
public enum VoiceCommand {

    // ── NAVIGATION ────────────────────────────────────────────────
    /** Open the accelerometer (Bewegung) detail screen. */
    NAV_ACCEL,

    /** Open the gyroscope detail screen. */
    NAV_GYRO,

    /** Open the Hall / Magnet detail screen. */
    NAV_MAGNET,

    /** Open the microphone / sound detail screen. */
    NAV_MIC,

    /** Open the graph / chart overview screen. */
    NAV_GRAPH,

    /** Open the event / notification log. */
    NAV_EVENTS,

    /** Go back to the main dashboard. */
    NAV_HOME,

    /** Open the settings screen. */
    NAV_SETTINGS,

    // ── TIME FILTER (applies to the currently visible chart) ──────
    /** Show data from the last 5 minutes. */
    FILTER_LAST_5MIN,

    /** Show data from the last 10 minutes. */
    FILTER_LAST_10MIN,

    /** Show data from the last 30 minutes. */
    FILTER_LAST_30MIN,

    /** Show data from the last 1 hour. */
    FILTER_LAST_1H,

    /** Show data from the last 24 hours. */
    FILTER_LAST_24H,

    /** Clear all active time filters and show all data. */
    FILTER_CLEAR,

    // ── SENSOR FILTER (navigate + filter in one step) ─────────────
    /** Open accel screen AND apply the last-10-min filter. */
    NAV_ACCEL_FILTER_10MIN,

    /** Open gyro screen AND apply the last-10-min filter. */
    NAV_GYRO_FILTER_10MIN,

    /** Open magnet screen AND apply the last-10-min filter. */
    NAV_MAGNET_FILTER_10MIN,

    // ── MQTT MODE CONTROL ─────────────────────────────────────────
    /** Switch ESP8266 to STREAM mode (publish every reading). */
    MODE_STREAM,

    /** Switch ESP8266 to BURST mode (collect, then send in one burst). */
    MODE_BURST,

    /** Switch ESP8266 to AVERAGE mode (send rolling averages). */
    MODE_AVERAGE,

    // ── COMBINED SENSOR QUERIES ───────────────────────────────────
    /**
     * Show the combined motion dashboard: accelerometer + gyro side-by-side.
     * Useful to judge whether an object is tilting vs. rotating.
     */
    COMBINED_MOTION,

    /**
     * Show the vibration analysis view: accelerometer magnitude + mic level
     * overlaid. Useful to correlate physical impacts with sound spikes.
     */
    COMBINED_VIBRATION_SOUND,

    /**
     * Show the orientation summary: gyro + Hall sensor.
     * Useful to see if a magnetic field triggered together with a rotation.
     */
    COMBINED_ORIENTATION_MAGNETIC,

    /**
     * Show a full all-sensors snapshot (latest single reading from every sensor).
     */
    COMBINED_ALL_SENSORS,

    // ── QUERY (answered by LLM in Phase 4) ───────────────────────
    /** "What is the current acceleration?" */
    QUERY_ACCEL_VALUE,

    /** "What is the current gyro reading?" */
    QUERY_GYRO_VALUE,

    /** "Is the magnet / hall sensor active?" */
    QUERY_MAGNET_STATUS,

    /** "How loud is it right now?" */
    QUERY_MIC_LEVEL,

    /** "Are there any unusual sensor events?" */
    QUERY_ANOMALY,

    /** "What happened in the last X minutes?" */
    QUERY_RECENT_EVENTS,

    // ── SYSTEM ────────────────────────────────────────────────────
    /** "Hilfe" / "Was kann ich sagen?" – show voice-command help overlay. */
    SYSTEM_HELP,

    // ── UNKNOWN ───────────────────────────────────────────────────
    /** No rule matched the transcript. */
    UNKNOWN
}