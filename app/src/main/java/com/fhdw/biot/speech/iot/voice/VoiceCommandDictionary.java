package com.fhdw.biot.speech.iot.voice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * VoiceCommandDictionary
 * ─────────────────────────────────────────────────────────────────────────────
 * A pure-data class.  No Android dependencies – easy to unit-test.
 *
 * Each {@link Rule} pairs a {@link VoiceCommand} with a list of keyword sets.
 * A rule matches when the transcript contains AT LEAST ONE keyword from every
 * required group (AND logic between groups, OR logic within a group).
 *
 * Examples
 * ────────
 *  Rule  keywords = [["zeige","öffne"], ["gyro"]]
 *   → matches "zeige Gyro" ✓   "öffne den Gyro" ✓   "Gyro" ✗  "zeige" ✗
 *
 *  Rule  keywords = [["beschleunigung","bewegung","accel"]]
 *   → matches any one of those words alone
 *
 * Ordering matters: the resolver walks rules top-to-bottom and returns the
 * FIRST match.  More specific rules (more keyword groups) must come before
 * their shorter / more generic siblings.
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

        // ══ COMBINED (most specific – checked first) ══════════════════════════

        // "zeige bewegung und gyro" / "kombiniert accel gyro"
        r.add(Rule.of(VoiceCommand.COMBINED_MOTION,
                new String[]{"movement","motion", "acceleration", "Accel"},
                new String[]{"gyro", "rotation", "spin"}));

        // "vibration und lautstärke" / "mikrofon und bewegung"
        r.add(Rule.of(VoiceCommand.COMBINED_VIBRATION_SOUND,
                new String[]{"vibration", "sound", "volume", "microphone", "mic"},
                new String[]{"movement","motion", "acceleration", "Accel", "sensor"}));

        // "orientierung und magnet" / "gyro und hall"
        r.add(Rule.of(VoiceCommand.COMBINED_ORIENTATION_MAGNETIC,
                new String[]{"orientation","alignment", "facing", "gyro", "rotation"},
                new String[]{"magnet", "hall", "magnetic field"}));

        // "alle sensoren" / "gesamtübersicht" / "übersicht"
        r.add(Rule.of(VoiceCommand.COMBINED_ALL_SENSORS,
                new String[]{"all", "total", "complete", "overview", "summary"}));

        // ══ NAVIGATION + FILTER combos ════════════════════════════════════════

        // "zeige Beschleunigung letzte 10 Minuten"
        r.add(Rule.of(VoiceCommand.NAV_ACCEL_FILTER_10MIN,
                new String[]{"acceleration", "Accel", "movement", "motion"},
                new String[]{"10", "ten"},
                new String[]{"minute", "min"}));

        // "zeige Gyro letzte 10 Minuten"
        r.add(Rule.of(VoiceCommand.NAV_GYRO_FILTER_10MIN,
                new String[]{"gyro", "gyroscope", "rotation", "spin"},
                new String[]{"10", "ten"},
                new String[]{"minute", "min"}));

        // "zeige Magnet letzte 10 Minuten"
        r.add(Rule.of(VoiceCommand.NAV_MAGNET_FILTER_10MIN,
                new String[]{"magnet", "hall", "magnetic field"},
                new String[]{"10", "ten"},
                new String[]{"minute", "min"}));

        // ══ TIME FILTERS (standalone) ═════════════════════════════════════════

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
                new String[]{"24", "twenty four", "twenty four","day", "today"},
                new String[]{"hour", "hours", "day"}));

        r.add(Rule.of(VoiceCommand.FILTER_CLEAR,
                new String[]{"filter", "timeframe", "date", "period", "span", "duration"},
                new String[]{"reset", "delete", "undo" ,"cancel", "all", "remove", "revoke", "reverse", "void"}));

        // ══ MQTT MODE CONTROL ═════════════════════════════════════════════════

        r.add(Rule.of(VoiceCommand.MODE_STREAM,
                new String[]{"stream", "real time", "live", "continuously"}));

        r.add(Rule.of(VoiceCommand.MODE_BURST,
                new String[]{"burst","bur", "packet", "packets", "bulk", "package", "packages"}));

        r.add(Rule.of(VoiceCommand.MODE_AVERAGE,
                new String[]{"average", "median", "mean"}));

        // ══ QUERIES (for LLM phase; registered here so resolver can hand off) ═

        // "wie hoch ist die Beschleunigung" / "aktueller Accel-Wert"
        r.add(Rule.of(VoiceCommand.QUERY_ACCEL_VALUE,
                new String[]{"how", "current","latest","relevant", "value", "show"},
                new String[]{"acceleration", "Accel", "movement", "motion"}));

        // "wie dreht sich das" / "aktueller Gyro-Wert"
        r.add(Rule.of(VoiceCommand.QUERY_GYRO_VALUE,
                new String[]{"how", "current","latest","relevant", "value", "show"},
                new String[]{"gyro", "gyroscope", "rotation", "spin"}));

        // "ist der Magnet aktiv" / "Hall-Status"
        r.add(Rule.of(VoiceCommand.QUERY_MAGNET_STATUS,
                new String[]{"active", "status", "how", "recognized"},
                new String[]{"magnet", "hall", "magnetic field"}));

        // "wie laut ist es" / "Mikrofon-Pegel"
        r.add(Rule.of(VoiceCommand.QUERY_MIC_LEVEL,
                new String[]{"how", "loud", "level", "value"},
                new String[]{"loud", "microphone", "mic", "noise", "sound"}));

        // "gibt es Anomalien" / "ungewöhnliche Werte"
        r.add(Rule.of(VoiceCommand.QUERY_ANOMALY,
                new String[]{"anomaly", "strange", "unusual", "uncommon", "alert", "signal", "warning", "error", "problem"}));

        // "was ist passiert" / "letzte Ereignisse"
        r.add(Rule.of(VoiceCommand.QUERY_RECENT_EVENTS,
                new String[]{"happened", "events", "event", "timeframe", "duration", "course", "last"},
                new String[]{"happened", "events", "event", "timeframe", "duration", "course", "minutes","minute", "hour", "hours"}));

        // ══ SIMPLE NAVIGATION ═════════════════════════════════════════════════

        // Accel
        r.add(Rule.of(VoiceCommand.NAV_ACCEL,
                new String[]{"acceleration", "Accel", "movement", "motion"}));

        // Gyro
        r.add(Rule.of(VoiceCommand.NAV_GYRO,
                new String[]{"gyro", "gyroscope", "rotation", "spin"}));

        // Magnet / Hall
        r.add(Rule.of(VoiceCommand.NAV_MAGNET,
                new String[]{"magnet", "hall", "magnetic field", "magnetic sensor"}));

        // Mic
        r.add(Rule.of(VoiceCommand.NAV_MIC,
                new String[]{"microphone", "mic", "sound", "volume"}));

        // Graph / Charts
        r.add(Rule.of(VoiceCommand.NAV_GRAPH,
                new String[]{"graph", "chart", "diagram","table", "course",  "timeframe", "curve"}));

        // Events / Notifications
        r.add(Rule.of(VoiceCommand.NAV_EVENTS,
                new String[]{"event", "events", "notification","notifications", "alert","signal", "information", "log","report"}));

        // Home / Dashboard
        r.add(Rule.of(VoiceCommand.NAV_HOME,
                new String[]{"home", "start", "main", "overview", "dashboard", "back"}));

        // Settings
        r.add(Rule.of(VoiceCommand.NAV_SETTINGS,
                new String[]{"settings", "configuration", "options"}));

        // ══ SYSTEM ════════════════════════════════════════════════════════════

        r.add(Rule.of(VoiceCommand.SYSTEM_HELP,
                new String[]{"hilfe", "help", "commands", "how can i", "commandos", "voice commands"}));

        return r;
    }

    /** Returns an unmodifiable view of the rule list. */
    public static List<Rule> getRules() {
        return java.util.Collections.unmodifiableList(RULES);
    }
}