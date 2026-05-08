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
     * several alternatives).
     *
     * All hypotheses are scanned; the match with the lowest rule-index wins.
     * Rules are ordered most-specific first (QUERY_* before NAV_*), so this
     * ensures "what is the magnetic value" (→ QUERY_MAGNET_STATUS, rule 30)
     * beats a shorter hypothesis like "magnetic value" (→ NAV_MAGNET, rule 34)
     * even when the shorter form arrives as hypothesis[0].
     *
     * @param hypotheses Ordered list of transcript hypotheses.
     * @return The best-matched command, or {@link VoiceCommand#UNKNOWN}.
     */
    public static VoiceCommand resolveFromList(List<String> hypotheses) {
        if (hypotheses == null) return VoiceCommand.UNKNOWN;

        List<VoiceCommandDictionary.Rule> rules = VoiceCommandDictionary.getRules();
        int bestRuleIndex = Integer.MAX_VALUE;
        VoiceCommand best = VoiceCommand.UNKNOWN;

        for (String h : hypotheses) {
            if (h == null || h.trim().isEmpty()) continue;
            String normalised = normalise(h);
            for (int i = 0; i < rules.size(); i++) {
                if (i >= bestRuleIndex) break; // can't beat current best
                if (matches(normalised, rules.get(i))) {
                    bestRuleIndex = i;
                    best = rules.get(i).command;
                    break; // this hypothesis matched at rule i — try next hypothesis
                }
            }
        }

        return best;
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
     */
    private static String normalise(String raw) {
        return raw.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}