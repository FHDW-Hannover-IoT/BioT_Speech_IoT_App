package com.fhdw.biot.speech.iot.config;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * LanguageManager — persists the user's language choice and applies it to
 * every Activity context via {@link #applyLocale(Context)}.
 *
 * Supported codes: "de" (German, default), "en" (English), "zh" (Mandarin).
 *
 * Usage:
 *   Override attachBaseContext in every Activity (via BiotBaseActivity):
 *     super.attachBaseContext(LanguageManager.applyLocale(base));
 *
 *   Save a new choice from SettingsActivity:
 *     LanguageManager.save(this, "en");
 *     recreate();
 */
public final class LanguageManager {

    private static final String PREFS = "AppSettings";
    private static final String KEY   = "app_language";

    private LanguageManager() {}

    public static String getCode(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getString(KEY, "de");
    }

    public static void save(Context ctx, String code) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().putString(KEY, code).apply();
    }

    public static Locale getLocale(Context ctx) {
        switch (getCode(ctx)) {
            case "zh": return Locale.SIMPLIFIED_CHINESE;
            case "en": return Locale.ENGLISH;
            default:   return Locale.GERMAN;
        }
    }

    /** BCP-47 tag passed to SpeechRecognizer EXTRA_LANGUAGE. */
    public static String getLanguageTag(Context ctx) {
        switch (getCode(ctx)) {
            case "zh": return "zh-CN";
            case "en": return "en-US";
            default:   return "de-DE";
        }
    }

    /**
     * Wraps {@code base} in a context that uses the saved locale.
     * Call from {@code attachBaseContext} in every Activity.
     */
    public static Context applyLocale(Context base) {
        Locale locale = getLocale(base);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
