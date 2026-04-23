package com.fhdw.biot.speech.iot.config;

import android.app.Application;
import android.util.Log;

/**
 * BiotApplication — process-singleton that owns the {@link AppContainer}.
 *
 * Registered in AndroidManifest.xml via android:name=".config.BiotApplication".
 * Activities retrieve the container via:
 *   AppContainer container = ((BiotApplication) getApplication()).getContainer();
 *
 * This prevents the rotation bug where MainActivity previously created a new
 * AppContainer (and a new MQTT connection) on every configuration change.
 */
public class BiotApplication extends Application {

    private static final String TAG = "BiotApplication";

    private AppContainer container;

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
