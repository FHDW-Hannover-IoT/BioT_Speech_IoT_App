package com.fhdw.biot.speech.iot.config;

import android.app.Application;
import android.util.Log;

/**
 * BiotApplication — process-singleton that owns the {@link AppContainer}.
 *
 * <p>Registered in AndroidManifest.xml via android:name=".config.BiotApplication". Activities
 * retrieve the container via: AppContainer container = ((BiotApplication)
 * getApplication()).getContainer();
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
