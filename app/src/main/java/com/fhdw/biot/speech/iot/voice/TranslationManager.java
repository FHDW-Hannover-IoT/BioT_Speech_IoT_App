package com.fhdw.biot.speech.iot.voice;

import android.util.Log;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * TranslationManager
 * ─────────────────────────────────────────────────────────────────────────────
 * Translates STT hypothesis lists from any supported app language to English
 * so that {@link VoiceCommandResolver} only needs English keywords.
 *
 * Uses Google ML Kit on-device translation — works offline after the language
 * model is downloaded (~30 MB per language pair, cached automatically).
 *
 * Supported source languages: German ("de"), Chinese simplified ("zh").
 * English ("en") is a no-op — {@link #isNeeded} returns false.
 *
 * Translator instances are lazy-created and cached per source language.
 * Call {@link #close()} when the app process is shutting down.
 */
public class TranslationManager {

    private static final String TAG = "TranslationManager";

    private static final Map<String, String> LANG_TO_MLKIT = new HashMap<>();
    static {
        LANG_TO_MLKIT.put("de", TranslateLanguage.GERMAN);
        LANG_TO_MLKIT.put("zh", TranslateLanguage.CHINESE);
    }

    private final Map<String, Translator> cache = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if the given app language code requires translation. */
    public boolean isNeeded(String langCode) {
        return LANG_TO_MLKIT.containsKey(langCode);
    }

    /**
     * Translates every hypothesis in the list from {@code sourceLangCode} to
     * English, then calls {@code onComplete} with the translated list.
     *
     * Falls back to the original list if the ML Kit model download fails or
     * any translation errors occur, so the LLM fallback path still works.
     *
     * Must be called from the main thread — {@code onComplete} is also
     * delivered on the main thread.
     *
     * @param sourceLangCode App language code ("de" or "zh").
     * @param hypotheses     Ranked hypotheses from SpeechRecognizer (up to 5).
     * @param onComplete     Receives the translated hypotheses (or originals on failure).
     */
    public void translateAll(String sourceLangCode, List<String> hypotheses,
                             Consumer<List<String>> onComplete) {
        Translator translator = getOrCreate(sourceLangCode);
        if (translator == null) {
            onComplete.accept(hypotheses);
            return;
        }

        translator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> translateList(translator, hypotheses, onComplete))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Model download failed, using originals: " + e.getMessage());
                    onComplete.accept(hypotheses);
                });
    }

    /** Releases all cached Translator instances. */
    public void close() {
        for (Translator t : cache.values()) t.close();
        cache.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void translateList(Translator translator, List<String> hypotheses,
                               Consumer<List<String>> onComplete) {
        int total = hypotheses.size();
        List<String> results = new ArrayList<>(total);
        for (int i = 0; i < total; i++) results.add(null);

        AtomicInteger remaining = new AtomicInteger(total);

        for (int i = 0; i < total; i++) {
            final int idx = i;
            translator.translate(hypotheses.get(idx))
                    .addOnSuccessListener(translated -> {
                        results.set(idx, translated);
                        if (remaining.decrementAndGet() == 0) {
                            Log.d(TAG, "Translated " + total + " hypothesis(es) to English");
                            onComplete.accept(results);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Single translation failed, keeping original: " + e.getMessage());
                        results.set(idx, hypotheses.get(idx));
                        if (remaining.decrementAndGet() == 0) {
                            onComplete.accept(results);
                        }
                    });
        }
    }

    private Translator getOrCreate(String langCode) {
        String mlKitLang = LANG_TO_MLKIT.get(langCode);
        if (mlKitLang == null) return null;

        if (!cache.containsKey(langCode)) {
            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(mlKitLang)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build();
            cache.put(langCode, Translation.getClient(options));
            Log.i(TAG, "Translator created: " + mlKitLang + " → en");
        }
        return cache.get(langCode);
    }
}
