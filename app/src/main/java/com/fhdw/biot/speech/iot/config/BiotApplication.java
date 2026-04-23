package com.fhdw.biot.speech.iot.config;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * BiotApplication — process-singleton that owns the {@link AppContainer}.
 *
 * Registered in AndroidManifest.xml via android:name=".config.BiotApplication".
 * Activities retrieve the container via:
 *   AppContainer container = ((BiotApplication) getApplication()).getContainer();
 */
public class BiotApplication extends Application {

    private static final String TAG = "BiotApplication";

    private AppContainer container;

    @Override
    protected void attachBaseContext(Context base) {
        // Apply saved locale before any resource resolution happens.
        super.attachBaseContext(LanguageManager.applyLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer();
        container.initApplicationScope(this);
        Log.i(TAG, "BiotApplication started — application scope ready");
    }

    public AppContainer getContainer() {
        return container;
    }
}
