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

        // ══ CALIBRATION (before NAV_* so "calibrate the gyro" doesn't trigger NAV_GYRO)
        r.add(Rule.of(VoiceCommand.START_CALIBRATION,
                new String[]{"calibrate", "calibration", "start calibration"}));

        // ══ SET EPSILON (before SETTINGS)
        r.add(Rule.of(VoiceCommand.SET_EPSILON,
                new String[]{"epsilon", "threshold value"},
                new String[]{"set", "change", "update", "adjust"}));

        // ══ CREATE EVENT (before SHOW_EVENTS)
        r.add(Rule.of(VoiceCommand.CREATE_EVENT,
                new String[]{"create", "add", "new"},
                new String[]{"event", "rule", "trigger", "alert"}));

        // ══ SHOW NOTIFICATIONS vs SHOW EVENTS
        r.add(Rule.of(VoiceCommand.SHOW_NOTIFICATIONS,
                new String[]{"notification", "notifications", "alert", "alerts"},
                new String[]{"show", "list", "display"}));

        r.add(Rule.of(VoiceCommand.SHOW_EVENTS,
                new String[]{"events", "event", "rules"},
                new String[]{"show", "list", "display"}));

        // ══ TELL VALUE (before NAV_* / QUERY_*)
        r.add(Rule.of(VoiceCommand.TELL_VALUE,
                new String[]{"tell", "say", "speak", "what is the value"}));

        // ══ OPERATING MODE
        r.add(Rule.of(VoiceCommand.OPMODE_AUTARK,
                new String[]{"autark", "autonomous", "standalone", "power saving"}));

        r.add(Rule.of(VoiceCommand.OPMODE_SUPERVISION,
                new String[]{"supervision", "monitor", "monitoring"}));

        r.add(Rule.of(VoiceCommand.OPMODE_EVENT,
                new String[]{"event mode", "trigger mode"}));

        r.add(Rule.of(VoiceCommand.OPMODE_IDENTIFICATION,
                new String[]{"identification", "identify"}));

        r.add(Rule.of(VoiceCommand.OPMODE_GET,
                new String[]{"current", "active", "running", "what", "which"},
                new String[]{"mode", "operating"}));

        // ══ COMBINED (specific multi-sensor commands)
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
                new String[]{"all sensors", "all sensor", "homescreen", "home screen", "complete overview", "summary"}));

        // ══ NAVIGATION + FILTER COMBOS
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

        // ══ TIME FILTERS (standalone)
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

        // ══ TRANSMISSION MODE
        r.add(Rule.of(VoiceCommand.MODE_STREAM,
                new String[]{"stream", "real time", "live", "continuously"}));

        r.add(Rule.of(VoiceCommand.MODE_BURST,
                new String[]{"burst", "bur", "packet", "packets", "bulk", "package", "packages"}));

        r.add(Rule.of(VoiceCommand.MODE_AVERAGE,
                new String[]{"average", "median", "mean"}));

        // ══ QUERIES (forwarded to LLM) — MUST come before simple NAV_* rules so
        //    natural questions like "what is the gyro?" route to LLM, not navigation.
        r.add(Rule.of(VoiceCommand.QUERY_ACCEL_VALUE,
                new String[]{"what", "how", "is", "are", "get", "check", "current", "latest", "value"},
                new String[]{"acceleration", "accel", "movement", "motion"}));

        r.add(Rule.of(VoiceCommand.QUERY_GYRO_VALUE,
                new String[]{"what", "how", "is", "are", "get", "check", "current", "latest", "value"},
                new String[]{"gyro", "gyroscope", "rotation", "spin"}));

        r.add(Rule.of(VoiceCommand.QUERY_MAGNET_STATUS,
                new String[]{"what", "active", "status", "how", "is", "check", "recognized"},
                new String[]{"magnet", "hall", "magnetic field"}));

        r.add(Rule.of(VoiceCommand.QUERY_MIC_LEVEL,
                new String[]{"what", "how", "loud", "level", "value", "check"},
                new String[]{"loud", "microphone", "mic", "noise", "sound"}));

        r.add(Rule.of(VoiceCommand.QUERY_ANOMALY,
                new String[]{"anomaly", "anomalies", "strange", "unusual", "uncommon", "warning", "problem"}));

        r.add(Rule.of(VoiceCommand.QUERY_RECENT_EVENTS,
                new String[]{"happened", "recent", "last", "latest"},
                new String[]{"events", "event", "minutes", "minute", "hour", "hours"}));

        // ══ SIMPLE NAVIGATION
        r.add(Rule.of(VoiceCommand.NAV_ACCEL,
                new String[]{"acceleration", "accel", "movement", "motion"}));

        r.add(Rule.of(VoiceCommand.NAV_GYRO,
                new String[]{"gyro", "gyroscope", "rotation", "spin"}));

        r.add(Rule.of(VoiceCommand.NAV_MAGNET,
                new String[]{"magnet", "hall", "magnetic field", "magnetic sensor"}));

        r.add(Rule.of(VoiceCommand.NAV_GRAPH,
                new String[]{"graph", "chart", "diagram", "table", "course", "timeframe", "curve"}));

        r.add(Rule.of(VoiceCommand.NAV_EVENTS,
                new String[]{"event", "events", "log", "report"}));

        r.add(Rule.of(VoiceCommand.NAV_HOME,
                new String[]{"home", "start", "main", "overview", "dashboard", "back"}));

        r.add(Rule.of(VoiceCommand.NAV_SETTINGS,
                new String[]{"settings", "configuration", "options"}));

        // ══ SYSTEM
        r.add(Rule.of(VoiceCommand.SYSTEM_HELP,
                new String[]{"help", "commands", "how can i", "voice commands"}));

        return r;
    }

    /** Returns an unmodifiable view of the rule list. */
    public static List<Rule> getRules() {
        return java.util.Collections.unmodifiableList(RULES);
    }
}
