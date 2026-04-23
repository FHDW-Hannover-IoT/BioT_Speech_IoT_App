package com.fhdw.biot.speech.iot.config;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

/**
 * BiotBaseActivity — base class for every Activity in this app.
 *
 * Applies the user's saved locale before the layout inflates, so that
 * string resources are resolved in the correct language.
 */
public abstract class BiotBaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageManager.applyLocale(base));
    }
}
