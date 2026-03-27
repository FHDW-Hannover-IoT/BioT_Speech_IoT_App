package com.fhdw.biot.speech.iot.voice;

import java.util.List;
import java.util.Locale;

/**
 * VoiceCommandResolver
 * ─────────────────────────────────────────────────────────────────────────────
 * Turns a raw speech-recogniser transcript into a {@link VoiceCommand}.
 *
 * Usage
 * ─────
 *   VoiceCommand cmd = VoiceCommandResolver.resolve("zeige mir den Gyro");
 *   // → VoiceCommand.NAV_GYRO
 *
 * The resolver is intentionally stateless and dependency-free so it can be
 * called from any thread and unit-tested without Android stubs.
 */
public final class VoiceCommandResolver {

    private VoiceCommandResolver() {}   // utility class – no instances

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve a speech transcript to the best matching {@link VoiceCommand}.
     *
     * @param transcript Raw text from the speech recogniser.  May be null.
     * @return The matched command, or {@link VoiceCommand#UNKNOWN} if nothing matched.
     */
    public static VoiceCommand resolve(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            return VoiceCommand.UNKNOWN;
        }

        // Normalise: lower-case, strip punctuation, collapse whitespace
        String normalised = normalise(transcript);

        for (VoiceCommandDictionary.Rule rule : VoiceCommandDictionary.getRules()) {
            if (matches(normalised, rule)) {
                return rule.command;
            }
        }

        return VoiceCommand.UNKNOWN;
    }

    /**
     * Resolve from a list of hypotheses (e.g. Android SpeechRecognizer returns
     * several alternatives).  Each hypothesis is tried in order; the first match
     * wins.
     *
     * @param hypotheses Ordered list of transcript hypotheses.
     * @return The first matched command, or {@link VoiceCommand#UNKNOWN}.
     */
    public static VoiceCommand resolveFromList(List<String> hypotheses) {
        if (hypotheses == null) return VoiceCommand.UNKNOWN;
        for (String h : hypotheses) {
            VoiceCommand cmd = resolve(h);
            if (cmd != VoiceCommand.UNKNOWN) return cmd;
        }
        return VoiceCommand.UNKNOWN;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Matching helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A rule matches iff the transcript contains at least one keyword from
     * EVERY keyword group (AND between groups, OR within a group).
     */
    private static boolean matches(String normalised, VoiceCommandDictionary.Rule rule) {
        for (List<String> group : rule.keywordGroups) {
            boolean groupMatched = false;
            for (String keyword : group) {
                if (normalised.contains(keyword)) {
                    groupMatched = true;
                    break;
                }
            }
            if (!groupMatched) return false;
        }
        return true;
    }

    /**
     * Lower-case + remove characters that are neither letters, digits nor spaces.
     * German umlauts are kept (ä ö ü ß) so keywords like "dreißig" still match.
     */
    private static String normalise(String raw) {
        return raw.toLowerCase(Locale.GERMAN)
                .replaceAll("[^a-zäöüß0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}