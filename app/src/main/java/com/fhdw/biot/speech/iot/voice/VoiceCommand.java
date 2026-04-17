package com.fhdw.biot.speech.iot.voice;

/**
 * VoiceCommand
 * ─────────────────────────────────────────────────────────────────────────────
 * Every intent the voice system can recognise is listed here as an enum value.
 *
 * Intent groups
 * ─────────────────────────────────────────────────────────────────────────────
 *  NAVIGATION       – open a screen
 *  FILTER           – apply a time filter to the current chart
 *  TRANSMISSION_MODE – Stream / Burst / Average (how the ESP8266 batches MQTT data)
 *  OPERATING_MODE   – Autark / Supervision / Event / Identification
 *                     (per BioT_Speech_IoT_Doc/doc/content/command-dictionary.adoc)
 *  CALIBRATION      – calibration & epsilon tuning commands
 *  EVENT_MGMT       – create / list events with optional sensor + threshold
 *  TELL_VALUE       – ask the assistant to read out a sensor value
 *  COMBINED         – multi-sensor dashboards
 *  QUERY            – natural-language questions forwarded to the LLM
 *  SYSTEM           – help, dashboard, etc.
 *  UNKNOWN          – nothing matched
 *
 * Source of truth: BioT_Speech_IoT_Doc/doc/content/command-dictionary.adoc
 *                 + LLM_App/docs/LLM_USE_CASES.md
 */
public enum VoiceCommand {

    // ── NAVIGATION ────────────────────────────────────────────────
    NAV_ACCEL,
    NAV_GYRO,
    NAV_MAGNET,
    NAV_MIC,
    NAV_GRAPH,
    NAV_EVENTS,
    NAV_HOME,
    NAV_SETTINGS,

    // ── TIME FILTERS (applies to the currently visible chart) ─────
    FILTER_LAST_5MIN,
    FILTER_LAST_10MIN,
    FILTER_LAST_30MIN,
    FILTER_LAST_1H,
    FILTER_LAST_24H,
    FILTER_CLEAR,

    // ── SENSOR FILTER (navigate + filter in one step) ─────────────
    NAV_ACCEL_FILTER_10MIN,
    NAV_GYRO_FILTER_10MIN,
    NAV_MAGNET_FILTER_10MIN,

    // ── TRANSMISSION MODE (existing — Control/Mode topic) ─────────
    /** Publish every reading immediately. */
    MODE_STREAM,
    /** Buffer 5 s of readings and publish in one batch. */
    MODE_BURST,
    /** Compute a 5 s rolling average and publish that. */
    MODE_AVERAGE,

    // ── OPERATING MODE (new — Control/OperatingMode topic) ────────
    // Per command-dictionary.adoc:
    //   Autark         → sensors stop sending data (power saving)
    //   Supervision    → sensors send all data; app shows homescreen
    //   Event          → sensors only send when thresholds are met; app notifies
    //   Identification → sensors send all data; app forwards to database
    OPMODE_AUTARK,
    OPMODE_SUPERVISION,
    OPMODE_EVENT,
    OPMODE_IDENTIFICATION,
    /** "Get mode" — ask the app to announce/show the current operating mode. */
    OPMODE_GET,

    // ── CALIBRATION & EPSILON ─────────────────────────────────────
    /** "Start calibration" — begin the guided calibration routine. */
    START_CALIBRATION,
    /** "Set epsilon (sensor) (axis?)" — adjust simplification threshold. */
    SET_EPSILON,

    // ── EVENT MANAGEMENT (from .adoc) ─────────────────────────────
    /** "Create event (sensor) (threshold)" — define a new threshold rule. */
    CREATE_EVENT,
    /** "Show events" or "Show events (sensor)" — list event RULES. */
    SHOW_EVENTS,
    /** "Show notifications (timeframe?)" — list TRIGGERED events. */
    SHOW_NOTIFICATIONS,

    // ── TELL VALUE ────────────────────────────────────────────────
    /** "Tell value (sensor) (axis?)" — speak a sensor value via TTS. */
    TELL_VALUE,

    // ── COMBINED VIEWS ────────────────────────────────────────────
    COMBINED_MOTION,
    COMBINED_VIBRATION_SOUND,
    COMBINED_ORIENTATION_MAGNETIC,
    COMBINED_ALL_SENSORS,

    // ── QUERY (forwarded to the LLM) ──────────────────────────────
    QUERY_ACCEL_VALUE,
    QUERY_GYRO_VALUE,
    QUERY_MAGNET_STATUS,
    QUERY_MIC_LEVEL,
    QUERY_ANOMALY,
    QUERY_RECENT_EVENTS,

    // ── SYSTEM ────────────────────────────────────────────────────
    SYSTEM_HELP,

    // ── UNKNOWN ───────────────────────────────────────────────────
    UNKNOWN
}
