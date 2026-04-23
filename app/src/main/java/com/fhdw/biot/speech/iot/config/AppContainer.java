package com.fhdw.biot.speech.iot.config;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.fhdw.biot.speech.iot.database.DB;
import com.fhdw.biot.speech.iot.database.DbContext;
import com.fhdw.biot.speech.iot.llm.LlmAction;
import com.fhdw.biot.speech.iot.llm.LlmQueryHandler;
import com.fhdw.biot.speech.iot.mqtt.IMqttPublisher;
import com.fhdw.biot.speech.iot.mqtt.MqttHandler;
import com.fhdw.biot.speech.iot.repository.McpDataSyncService;
import com.fhdw.biot.speech.iot.repository.SensorRepository;
import com.fhdw.biot.speech.iot.voice.ILlmQueryHandler;
import com.fhdw.biot.speech.iot.voice.TtsManager;

import java.util.UUID;

/**
 * AppContainer — hand-rolled DI container, now living in the config package
 * and instantiated once by {@link BiotApplication}.
 *
 * Lifecycle tiers:
 *   Application-scoped (survives rotation):
 *     DB → DbContext → SensorRepository → McpDataSyncService
 *     LlmQueryHandler (pure HTTP — no Activity reference)
 *     MutableLiveData<LlmAction>  — observer hub for LLM responses
 *     MutableLiveData<Boolean>    — MQTT connection status
 *
 *   Activity-scoped (rebuilt per Activity.onCreate / torn down in onDestroy):
 *     MqttHandler, TtsManager
 */
public class AppContainer {

    private static final String TAG = "AppContainer";

    // ── Application scope ─────────────────────────────────────────────────────
    private DB db;
    private DbContext dbContext;
    private SensorRepository sensorRepository;
    private McpDataSyncService mcpDataSync;
    private LlmQueryHandler llmQueryHandler;

    private final MutableLiveData<LlmAction>  liveAction  = new MutableLiveData<>();
    private final MutableLiveData<Boolean>    llmLoading  = new MutableLiveData<>(false);

    // ── Activity scope ────────────────────────────────────────────────────────
    private MqttHandler mqttHandler;
    private TtsManager  ttsManager;

    // ─────────────────────────────────────────────────────────────────────────
    // Application-scope init (called once from BiotApplication.onCreate)
    // ─────────────────────────────────────────────────────────────────────────

    public void initApplicationScope(Context context) {
        if (db != null) return;

        db              = DB.getDatabase(context.getApplicationContext());
        dbContext       = new DbContext(db);
        sensorRepository = new SensorRepository(dbContext);
        mcpDataSync     = new McpDataSyncService(sensorRepository, AppConfig.mcpBaseUrl());
        llmQueryHandler = new LlmQueryHandler(liveAction, llmLoading, AppConfig.llmChatEndpoint());

        Log.i(TAG, "Application scope initialised.");
    }

    // ── Application-scope getters ─────────────────────────────────────────────

    public SensorRepository    sensorRepository() { return sensorRepository; }
    public McpDataSyncService  mcpDataSync()       { return mcpDataSync; }
    public ILlmQueryHandler    llmQueryHandler()   { return llmQueryHandler; }
    public MutableLiveData<LlmAction> liveAction() { return liveAction; }
    public LiveData<Boolean>   llmLoading()        { return llmLoading; }

    // ─────────────────────────────────────────────────────────────────────────
    // Activity-scope init (called from Activity.onCreate)
    // ─────────────────────────────────────────────────────────────────────────

    public void initActivityScope(Activity activity) {
        if (db == null) {
            throw new IllegalStateException("Call initApplicationScope() before initActivityScope().");
        }
        if (mqttHandler != null) return;

        String brokerUrl = AppConfig.mqttBrokerUrl();
        String clientId  = "Nutzer_" + UUID.randomUUID().toString().substring(0, 8);
        try {
            mqttHandler = new MqttHandler(brokerUrl, clientId);
            Log.i(TAG, "MqttHandler created (broker=" + brokerUrl + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MqttHandler: " + e.getMessage(), e);
            throw new IllegalStateException("Could not create MqttHandler", e);
        }

        ttsManager = new TtsManager(activity);
        Log.i(TAG, "Activity scope initialised.");
    }

    public void releaseActivityScope() {
        if (ttsManager  != null) { ttsManager.destroy();    ttsManager  = null; }
        if (mqttHandler != null) { mqttHandler.disconnect(); mqttHandler = null; }
        Log.i(TAG, "Activity scope released.");
    }

    // ── Activity-scope getters ────────────────────────────────────────────────

    public MqttHandler    mqttHandler()   { return mqttHandler; }
    public IMqttPublisher mqttPublisher() { return mqttHandler; }
    public TtsManager     ttsManager()   { return ttsManager; }
    public LiveData<Boolean> mqttConnected() {
        return mqttHandler != null ? mqttHandler.connectionStatus() : new MutableLiveData<>(false);
    }
}
