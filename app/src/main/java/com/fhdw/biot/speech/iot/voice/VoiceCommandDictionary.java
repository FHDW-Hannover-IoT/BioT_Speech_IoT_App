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
                new String[]{"bewegung", "beschleunigung", "accel"},
                new String[]{"gyro", "rotation", "drehung"}));

        // "vibration und lautstärke" / "mikrofon und bewegung"
        r.add(Rule.of(VoiceCommand.COMBINED_VIBRATION_SOUND,
                new String[]{"vibration", "schall", "lautstärke", "mikrofon", "mic", "sound"},
                new String[]{"bewegung", "beschleunigung", "accel", "sensor"}));

        // "orientierung und magnet" / "gyro und hall"
        r.add(Rule.of(VoiceCommand.COMBINED_ORIENTATION_MAGNETIC,
                new String[]{"orientierung", "ausrichtung", "gyro", "rotation"},
                new String[]{"magnet", "hall", "magnetfeld"}));

        // "alle sensoren" / "gesamtübersicht" / "übersicht"
        r.add(Rule.of(VoiceCommand.COMBINED_ALL_SENSORS,
                new String[]{"alle", "gesamt", "komplett", "übersicht", "zusammenfassung"}));

        // ══ NAVIGATION + FILTER combos ════════════════════════════════════════

        // "zeige Beschleunigung letzte 10 Minuten"
        r.add(Rule.of(VoiceCommand.NAV_ACCEL_FILTER_10MIN,
                new String[]{"beschleunigung", "accel", "bewegung"},
                new String[]{"10", "zehn"},
                new String[]{"minute", "min"}));

        // "zeige Gyro letzte 10 Minuten"
        r.add(Rule.of(VoiceCommand.NAV_GYRO_FILTER_10MIN,
                new String[]{"gyro", "gyroskop", "rotation", "drehung"},
                new String[]{"10", "zehn"},
                new String[]{"minute", "min"}));

        // "zeige Magnet letzte 10 Minuten"
        r.add(Rule.of(VoiceCommand.NAV_MAGNET_FILTER_10MIN,
                new String[]{"magnet", "hall", "magnetfeld"},
                new String[]{"10", "zehn"},
                new String[]{"minute", "min"}));

        // ══ TIME FILTERS (standalone) ═════════════════════════════════════════

        r.add(Rule.of(VoiceCommand.FILTER_LAST_5MIN,
                new String[]{"5", "fünf"},
                new String[]{"minute", "min"}));

        r.add(Rule.of(VoiceCommand.FILTER_LAST_10MIN,
                new String[]{"10", "zehn"},
                new String[]{"minute", "min"}));

        r.add(Rule.of(VoiceCommand.FILTER_LAST_30MIN,
                new String[]{"30", "dreißig"},
                new String[]{"minute", "min"}));

        r.add(Rule.of(VoiceCommand.FILTER_LAST_1H,
                new String[]{"1", "eine", "ein"},
                new String[]{"stunde", "std", "hour"}));

        r.add(Rule.of(VoiceCommand.FILTER_LAST_24H,
                new String[]{"24", "vierundzwanzig", "tag", "heute"},
                new String[]{"stunde", "std", "hour", "tag", "stunden"}));

        r.add(Rule.of(VoiceCommand.FILTER_CLEAR,
                new String[]{"filter", "zeitraum", "datum"},
                new String[]{"zurücksetzen", "löschen", "aufheben", "alle", "entfernen"}));

        // ══ MQTT MODE CONTROL ═════════════════════════════════════════════════

        r.add(Rule.of(VoiceCommand.MODE_STREAM,
                new String[]{"stream", "echtzeit", "live", "kontinuierlich"}));

        r.add(Rule.of(VoiceCommand.MODE_BURST,
                new String[]{"burst", "paket", "pakete", "bündel"}));

        r.add(Rule.of(VoiceCommand.MODE_AVERAGE,
                new String[]{"durchschnitt", "average", "mittelwert", "gemittelt"}));

        // ══ QUERIES (for LLM phase; registered here so resolver can hand off) ═

        // "wie hoch ist die Beschleunigung" / "aktueller Accel-Wert"
        r.add(Rule.of(VoiceCommand.QUERY_ACCEL_VALUE,
                new String[]{"wie", "aktuell", "wert", "zeig"},
                new String[]{"beschleunigung", "accel", "bewegung"}));

        // "wie dreht sich das" / "aktueller Gyro-Wert"
        r.add(Rule.of(VoiceCommand.QUERY_GYRO_VALUE,
                new String[]{"wie", "aktuell", "wert"},
                new String[]{"gyro", "gyroskop", "rotation", "drehung"}));

        // "ist der Magnet aktiv" / "Hall-Status"
        r.add(Rule.of(VoiceCommand.QUERY_MAGNET_STATUS,
                new String[]{"aktiv", "status", "wie", "erkannt"},
                new String[]{"magnet", "hall", "magnetfeld"}));

        // "wie laut ist es" / "Mikrofon-Pegel"
        r.add(Rule.of(VoiceCommand.QUERY_MIC_LEVEL,
                new String[]{"wie", "laut", "pegel", "wert"},
                new String[]{"laut", "mikrofon", "mic", "geräusch", "sound"}));

        // "gibt es Anomalien" / "ungewöhnliche Werte"
        r.add(Rule.of(VoiceCommand.QUERY_ANOMALY,
                new String[]{"anomalie", "ungewöhnlich", "alarm", "warnung", "fehler", "problem"}));

        // "was ist passiert" / "letzte Ereignisse"
        r.add(Rule.of(VoiceCommand.QUERY_RECENT_EVENTS,
                new String[]{"passiert", "ereignisse", "verlauf", "letzte", "zuletzt"},
                new String[]{"passiert", "ereignisse", "verlauf", "minuten", "stunde"}));

        // ══ SIMPLE NAVIGATION ═════════════════════════════════════════════════

        // Accel
        r.add(Rule.of(VoiceCommand.NAV_ACCEL,
                new String[]{"beschleunigung", "accel", "bewegung", "beschleunigungssensor"}));

        // Gyro
        r.add(Rule.of(VoiceCommand.NAV_GYRO,
                new String[]{"gyro", "gyroskop", "rotation", "drehung", "drehsensor"}));

        // Magnet / Hall
        r.add(Rule.of(VoiceCommand.NAV_MAGNET,
                new String[]{"magnet", "hall", "magnetfeld", "magnetsensor"}));

        // Mic
        r.add(Rule.of(VoiceCommand.NAV_MIC,
                new String[]{"mikrofon", "mic", "ton", "sound", "geräusch", "lautstärke"}));

        // Graph / Charts
        r.add(Rule.of(VoiceCommand.NAV_GRAPH,
                new String[]{"graph", "grafik", "chart", "diagramm", "verlauf", "kurve"}));

        // Events / Notifications
        r.add(Rule.of(VoiceCommand.NAV_EVENTS,
                new String[]{"ereignis", "ereignisse", "benachrichtigung", "alarm", "meldung", "log"}));

        // Home / Dashboard
        r.add(Rule.of(VoiceCommand.NAV_HOME,
                new String[]{"home", "start", "hauptseite", "übersicht", "dashboard", "zurück"}));

        // Settings
        r.add(Rule.of(VoiceCommand.NAV_SETTINGS,
                new String[]{"einstellungen", "settings", "konfiguration", "optionen"}));

        // ══ SYSTEM ════════════════════════════════════════════════════════════

        r.add(Rule.of(VoiceCommand.SYSTEM_HELP,
                new String[]{"hilfe", "help", "befehle", "was kann", "kommandos", "sprachbefehle"}));

        return r;
    }

    /** Returns an unmodifiable view of the rule list. */
    public static List<Rule> getRules() {
        return java.util.Collections.unmodifiableList(RULES);
    }
}