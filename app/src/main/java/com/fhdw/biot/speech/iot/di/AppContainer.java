package com.fhdw.biot.speech.iot.di;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.fhdw.biot.speech.iot.config.AppConfig;
import com.fhdw.biot.speech.iot.llm.LlmQueryHandler;
import com.fhdw.biot.speech.iot.mqtt.IMqttPublisher;
import com.fhdw.biot.speech.iot.mqtt.MqttHandler;
import com.fhdw.biot.speech.iot.voice.ILlmQueryHandler;
import com.fhdw.biot.speech.iot.voice.TtsManager;

import database.DB;
import database.dao.SensorDao;
import database.dao.ValueSensorDAO;

import java.util.UUID;

/**
 * AppContainer
 * ─────────────────────────────────────────────────────────────────────────────
 * Tiny hand-rolled DI container. Replaces the previous pattern in MainActivity
 * where every collaborator was newed up inline (and {@code llmHandler = null}).
 *
 * Why not Hilt / Dagger? This is a school project, the dependency surface is
 * small, and adding a kapt-based DI framework here would dwarf the production
 * code in build configuration. A 60-line container is the right tool.
 *
 * Lifecycle:
 *   • Application-scoped (DB, DAOs) — built once, reused forever.
 *   • Activity-scoped (MqttHandler, TtsManager, LlmQueryHandler) — built when
 *     {@link #initActivityScope(Activity)} is called from MainActivity.onCreate
 *     and torn down via {@link #releaseActivityScope()} in onDestroy.
 *
 * Usage in MainActivity:
 *   private final AppContainer container = new AppContainer();
 *   ...
 *   container.initApplicationScope(this);
 *   container.initActivityScope(this);
 *   sensorDao        = container.sensorDao();
 *   valueSensorDao   = container.valueSensorDao();
 *   mqttHandler      = container.mqttHandler();
 *   ttsManager       = container.ttsManager();
 *   llmQueryHandler  = container.llmQueryHandler();   // never null any more
 */
public class AppContainer {

    private static final String TAG = "AppContainer";

    // ── Application scope (recreated only on process death) ──────────────────
    private DB db;
    private SensorDao sensorDao;
    private ValueSensorDAO valueSensorDao;

    // ── Activity scope (rebuilt per Activity.onCreate) ───────────────────────
    private MqttHandler mqttHandler;
    private TtsManager  ttsManager;
    private LlmQueryHandler llmQueryHandler;

    // ─────────────────────────────────────────────────────────────────────────
    // Application-scope wiring
    // ─────────────────────────────────────────────────────────────────────────

    public void initApplicationScope(Context context) {
        if (db != null) return;       // idempotent
        db             = DB.getDatabase(context.getApplicationContext());
        sensorDao      = db.sensorDao();
        valueSensorDao = db.valueSensorDao();
        Log.i(TAG, "Application scope initialised.");
    }

    public SensorDao sensorDao()           { return sensorDao; }
    public ValueSensorDAO valueSensorDao() { return valueSensorDao; }

    // ─────────────────────────────────────────────────────────────────────────
    // Activity-scope wiring
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build the per-activity collaborators. Safe to call once per Activity.onCreate.
     *
     * @throws IllegalStateException if {@link #initApplicationScope(Context)} hasn't run yet.
     */
    public void initActivityScope(Activity activity) {
        if (db == null) {
            throw new IllegalStateException("Call initApplicationScope() before initActivityScope().");
        }
        if (mqttHandler != null) return; // idempotent

        // ── MQTT ──────────────────────────────────────────────────────────
        String brokerUrl = AppConfig.mqttBrokerUrl();
        String clientId  = "Nutzer_" + UUID.randomUUID().toString().substring(0, 8);
        try {
            mqttHandler = new MqttHandler(brokerUrl, clientId);
            Log.i(TAG, "MqttHandler created (broker=" + brokerUrl + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MqttHandler: " + e.getMessage(), e);
            throw new IllegalStateException("Could not create MqttHandler", e);
        }

        // ── TTS ───────────────────────────────────────────────────────────
        ttsManager = new TtsManager(activity);

        // ── LLM ───────────────────────────────────────────────────────────
        llmQueryHandler = new LlmQueryHandler(
                activity,
                ttsManager,
                mqttHandler,
                AppConfig.llmChatEndpoint());

        Log.i(TAG, "Activity scope initialised.");
    }

    /** Tear down per-activity collaborators in onDestroy. */
    public void releaseActivityScope() {
        if (llmQueryHandler != null) { llmQueryHandler.shutdown();     llmQueryHandler = null; }
        if (ttsManager      != null) { ttsManager.destroy();           ttsManager      = null; }
        if (mqttHandler     != null) { mqttHandler.disconnect();       mqttHandler     = null; }
        Log.i(TAG, "Activity scope released.");
    }

    // ── Getters used by MainActivity ────────────────────────────────────────
    public MqttHandler      mqttHandler()      { return mqttHandler; }
    public IMqttPublisher   mqttPublisher()    { return mqttHandler; }   // exposed as interface for DI
    public TtsManager       ttsManager()       { return ttsManager; }
    public ILlmQueryHandler llmQueryHandler()  { return llmQueryHandler; } // exposed as interface
}
