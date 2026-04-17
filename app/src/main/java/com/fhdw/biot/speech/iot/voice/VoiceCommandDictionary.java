package com.fhdw.biot.speech.iot.voice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * VoiceCommandDictionary
 * ─────────────────────────────────────────────────────────────────────────────
 * Pure-data class. No Android dependencies — easy to unit-test.
 *
 * Each {@link Rule} pairs a {@link VoiceCommand} with a list of keyword groups.
 * A rule matches when the transcript contains at least one keyword from EVERY
 * required group (AND logic between groups, OR logic within a group).
 *
 * Examples
 * ────────
 *  keywords = [["zeige","öffne"], ["gyro"]]
 *   → matches "zeige Gyro" ✓   "öffne den Gyro" ✓   "Gyro" ✗  "zeige" ✗
 *
 *  keywords = [["beschleunigung","bewegung","accel"]]
 *   → matches any one of those words alone
 *
 * Rule ordering matters: the resolver walks rules top-to-bottom and returns the
 * FIRST match. More specific rules (more keyword groups) MUST come before their
 * shorter / more generic siblings.
 *
 * This dictionary is the source of truth for what the keyword-based fast path
 * recognises. Anything it can't match becomes UNKNOWN, at which point the
 * VoiceCommandExecutor decides whether to fall back to the LLM.
 */
public class VoiceCommandDictionary {

    // ─────────────────────────────────────────────────────────────────────────
    // Rule
    // ─────────────────────────────────────────────────────────────────────────

    /** Immutable pairing of a {@link VoiceCommand} and its keyword groups. */
    public static final class Rule {
        public final VoiceCommand command;
        /** Each inner list is one required keyword group (OR within, AND across). */
        public final List<List<String>> keywordGroups;

        public Rule(VoiceCommand command, List<List<String>> keywordGroups) {
            this.command = command;
            this.keywordGroups = keywordGroups;
        }

        /** Convenience factory: one keyword group per vararg array. */
        @SafeVarargs
        public static Rule of(VoiceCommand cmd, String[]... groups) {
            List<List<String>> list = new ArrayList<>();
            for (String[] g : groups) list.add(Arrays.asList(g));
            return new Rule(cmd, list);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule table  (most specific first)
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<Rule> RULES = buildRules();

    private static List<Rule> buildRules() {
        List<Rule> r = new ArrayList<>();

        // ══ CALIBRATION (must come before NAV_* so "calibrate the gyro"
        //    doesn't trigger NAV_GYRO) ══════════════════════════════════════
        r.add(Rule.of(VoiceCommand.START_CALIBRATION,
                new String[]{"calibrate", "calibration", "kalibrierung", "kalibrieren", "start calibration"}));

        // ══ SET EPSILON (must come before SETTINGS) ═══════════════════════
        r.add(Rule.of(VoiceCommand.SET_EPSILON,
                new String[]{"epsilon", "threshold value", "schwellwert", "vereinfachung"},
                new String[]{"set", "change", "update", "adjust", "setze", "ändere"}));

        // ══ CREATE EVENT (must come before SHOW_EVENTS so "create event for accel"
        //    doesn't match SHOW_EVENTS first) ═════════════════════════════
        r.add(Rule.of(VoiceCommand.CREATE_EVENT,
                new String[]{"create", "add", "new", "erstelle", "neues", "neue", "lege"},
                new String[]{"event", "ereignis", "rule", "regel", "trigger", "alert"}));

        // ══ SHOW NOTIFICATIONS vs SHOW EVENTS ═════════════════════════════
        // "notifications" is the .adoc term for TRIGGERED events;
        // "events" is the .adoc term for the RULES that produce them.
        r.add(Rule.of(VoiceCommand.SHOW_NOTIFICATIONS,
                new String[]{"notification", "notifications", "benachrichtigung", "benachrichtigungen", "alert", "alerts"},
                new String[]{"show", "list", "display", "zeige", "öffne", "anzeigen"}));

        r.add(Rule.of(VoiceCommand.SHOW_EVENTS,
                new String[]{"events", "event", "rules", "regel", "ereignisregel"},
                new String[]{"show", "list", "display", "zeige", "öffne", "anzeigen"}));

        // ══ TELL VALUE (must come before NAV_* / QUERY_*) ═════════════════
        r.add(Rule.of(VoiceCommand.TELL_VALUE,
                new String[]{"tell", "say", "speak", "sage", "sprich", "nenne", "what is the value"}));

        // ══ OPERATING MODE (Autark / Supervision / Event / Identification) ══
        // Each operating mode gets its own rule. They sit BEFORE the existing
        // transmission MODE_STREAM/BURST/AVERAGE rules so that a phrase like
        // "switch to event mode" is read as OPMODE_EVENT not as something else.
        r.add(Rule.of(VoiceCommand.OPMODE_AUTARK,
                new String[]{"autark", "autonomous", "standalone", "power saving", "energiesparen"}));

        r.add(Rule.of(VoiceCommand.OPMODE_SUPERVISION,
                new String[]{"supervision", "überwachung", "überwacht", "monitor", "monitoring"}));

        r.add(Rule.of(VoiceCommand.OPMODE_EVENT,
                new String[]{"event mode", "ereignismodus", "ereignis modus", "trigger mode", "schwellenmodus"}));

        r.add(Rule.of(VoiceCommand.OPMODE_IDENTIFICATION,
                new String[]{"identification", "identifikation", "identify", "identifizieren"}));

        // "What mode am I in" / "get mode" / "current mode"
        r.add(Rule.of(VoiceCommand.OPMODE_GET,
                new String[]{"current", "active", "running", "aktuell", "welcher", "what", "which"},
                new String[]{"mode", "modus", "operating", "betriebs"}));

        // ══ COMBINED (specific multi-sensor commands) ═════════════════════
        r.add(Rule.of(VoiceCommand.COMBINED_MOTION,
                new String[]{"movement", "motion", "acceleration", "accel"},
                new String[]{"gyro", "rotation", "spin"}));

        r.add(Rule.of(VoiceCommand.COMBINED_VIBRATION_SOUND,
                new String[]{"vibration", "sound", "volume", "microphone", "mic"},
                new String[]{"movement", "motion", "acceleration", "accel", "sensor"}));

        r.add(Rule.of(VoiceCommand.COMBINED_ORIENTATION_MAGNETIC,
                new String[]{"orientation", "alignment", "facing", "gyro", "rotation"},
                new String[]{"magnet", "hall", "magnetic field"}));

        r.add(Rule.of(VoiceCommand.COMBINED_ALL_SENSORS,
                new String[]{"all sensors", "all sensor", "alle sensoren", "homescreen", "home screen", "complete overview", "summary"}));

        // ══ NAVIGATION + FILTER COMBOS ════════════════════════════════════
        r.add(Rule.of(VoiceCommand.NAV_ACCEL_FILTER_10MIN,
                new String[]{"acceleration", "accel", "movement", "motion"},
                new String[]{"10", "ten"},
                new String[]{"minute", "min"}));

        r.add(Rule.of(VoiceCommand.NAV_GYRO_FILTER_10MIN,
                new String[]{"gyro", "gyroscope", "rotation", "spin"},
                new String[]{"10", "ten"},
                new String[]{"minute", "min"}));

        r.add(Rule.of(VoiceCommand.NAV_MAGNET_FILTER_10MIN,
                new String[]{"magnet", "hall", "magnetic field"},
                new String[]{"10", "ten"},
                new String[]{"minute", "min"}));

        // ══ TIME FILTERS (standalone) ═════════════════════════════════════
        r.add(Rule.of(VoiceCommand.FILTER_LAST_5MIN,
                new String[]{"5", "five"},
                new String[]{"minute", "min"}));

        r.add(Rule.of(VoiceCommand.FILTER_LAST_10MIN,
                new String[]{"10", "ten"},
                new String[]{"minute", "min"}));

        r.add(Rule.of(VoiceCommand.FILTER_LAST_30MIN,
                new String[]{"30", "thirty"},
                new String[]{"minute", "min"}));

        r.add(Rule.of(VoiceCommand.FILTER_LAST_1H,
                new String[]{"1", "one"},
                new String[]{"hour"}));

        r.add(Rule.of(VoiceCommand.FILTER_LAST_24H,
                new String[]{"24", "twenty four", "day", "today"},
                new String[]{"hour", "hours", "day"}));

        r.add(Rule.of(VoiceCommand.FILTER_CLEAR,
                new String[]{"filter", "timeframe", "date", "period", "span", "duration"},
                new String[]{"reset", "delete", "undo", "cancel", "all", "remove", "revoke", "reverse", "void"}));

        // ══ TRANSMISSION MODE (existing — kept on Control/Mode topic) ════
        r.add(Rule.of(VoiceCommand.MODE_STREAM,
                new String[]{"stream", "real time", "live", "continuously"}));

        r.add(Rule.of(VoiceCommand.MODE_BURST,
                new String[]{"burst", "bur", "packet", "packets", "bulk", "package", "packages"}));

        r.add(Rule.of(VoiceCommand.MODE_AVERAGE,
                new String[]{"average", "median", "mean"}));

        // ══ QUERIES (forwarded to LLM) ════════════════════════════════════
        r.add(Rule.of(VoiceCommand.QUERY_ACCEL_VALUE,
                new String[]{"how", "current", "latest", "value", "show"},
                new String[]{"acceleration", "accel", "movement", "motion"}));

        r.add(Rule.of(VoiceCommand.QUERY_GYRO_VALUE,
                new String[]{"how", "current", "latest", "value", "show"},
                new String[]{"gyro", "gyroscope", "rotation", "spin"}));

        r.add(Rule.of(VoiceCommand.QUERY_MAGNET_STATUS,
                new String[]{"active", "status", "how", "recognized"},
                new String[]{"magnet", "hall", "magnetic field"}));

        r.add(Rule.of(VoiceCommand.QUERY_MIC_LEVEL,
                new String[]{"how", "loud", "level", "value"},
                new String[]{"loud", "microphone", "mic", "noise", "sound"}));

        r.add(Rule.of(VoiceCommand.QUERY_ANOMALY,
                new String[]{"anomaly", "anomalies", "strange", "unusual", "uncommon", "warning", "problem"}));

        r.add(Rule.of(VoiceCommand.QUERY_RECENT_EVENTS,
                new String[]{"happened", "recent", "letzten", "kürzlich"},
                new String[]{"events", "event", "minutes", "minute", "hour", "hours"}));

        // ══ SIMPLE NAVIGATION ═════════════════════════════════════════════
        r.add(Rule.of(VoiceCommand.NAV_ACCEL,
                new String[]{"acceleration", "accel", "beschleunigung", "movement", "motion", "bewegung"}));

        r.add(Rule.of(VoiceCommand.NAV_GYRO,
                new String[]{"gyro", "gyroscope", "gyroskop", "rotation", "spin"}));

        r.add(Rule.of(VoiceCommand.NAV_MAGNET,
                new String[]{"magnet", "hall", "magnetic field", "magnetic sensor", "magnetfeld"}));

        r.add(Rule.of(VoiceCommand.NAV_MIC,
                new String[]{"microphone", "mic", "sound", "volume", "mikrofon"}));

        r.add(Rule.of(VoiceCommand.NAV_GRAPH,
                new String[]{"graph", "chart", "diagram", "table", "course", "timeframe", "curve", "graphen"}));

        r.add(Rule.of(VoiceCommand.NAV_EVENTS,
                new String[]{"event", "events", "ereignisse", "log", "report"}));

        r.add(Rule.of(VoiceCommand.NAV_HOME,
                new String[]{"home", "start", "main", "overview", "dashboard", "back", "hauptseite"}));

        r.add(Rule.of(VoiceCommand.NAV_SETTINGS,
                new String[]{"settings", "configuration", "options", "einstellungen"}));

        // ══ SYSTEM ════════════════════════════════════════════════════════
        r.add(Rule.of(VoiceCommand.SYSTEM_HELP,
                new String[]{"hilfe", "help", "commands", "how can i", "voice commands"}));

        return r;
    }

    /** Returns an unmodifiable view of the rule list. */
    public static List<Rule> getRules() {
        return java.util.Collections.unmodifiableList(RULES);
    }
}
