package com.fhdw.biot.speech.iot.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fhdw.biot.speech.iot.BuildConfig;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.config.BiotBaseActivity;
import com.fhdw.biot.speech.iot.config.LanguageManager;
import com.fhdw.biot.speech.iot.config.AppContainer;
import com.fhdw.biot.speech.iot.config.BiotApplication;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.database.entities.ValueSensor;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.MainGraphActivity;
import com.fhdw.biot.speech.iot.llm.LlmAction;
import com.fhdw.biot.speech.iot.mqtt.MqttHandler;
import com.fhdw.biot.speech.iot.repository.SensorRepository;
import com.fhdw.biot.speech.iot.sensor.AccelActivity;
import com.fhdw.biot.speech.iot.sensor.GyroActivity;
import com.fhdw.biot.speech.iot.sensor.MagnetActivity;
import com.fhdw.biot.speech.iot.settings.SettingsActivity;
import com.fhdw.biot.speech.iot.simulation.SensorDataSimulator;
import com.fhdw.biot.speech.iot.voice.ILlmQueryHandler;
import com.fhdw.biot.speech.iot.voice.TtsManager;
import com.fhdw.biot.speech.iot.voice.VoiceCommand;
import com.fhdw.biot.speech.iot.voice.VoiceCommandExecutor;
import com.fhdw.biot.speech.iot.voice.VoiceCommandResolver;
import com.fhdw.biot.speech.iot.voice.VoiceInputManager;

import java.util.List;

/**
 * MainActivity
 * ─────────────────────────────────────────────────────────────────────────────
 * Subscribes to ESP8266 sensor topics via MQTT and writes data into the
 * in-memory Room DB via {@link SensorRepository}.
 *
 * Observer wiring:
 *   • {@code container.liveAction()} — LLM response: dispatch TTS/navigate/MQTT/filter
 *   • {@code container.llmLoading()} — show/hide "Asking BioT…" indicator
 *   • {@code mqttHandler.connectionStatus()} — MQTT connection indicator
 *
 * The container is retrieved from {@link BiotApplication} (process-singleton),
 * so no new MQTT connection is created on rotation.
 */
public class MainActivity extends BiotBaseActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO = 101;

    // ── DI container (from BiotApplication — survives rotation) ──────────────
    private AppContainer container;

    // ── References pulled from container ─────────────────────────────────────
    private SensorRepository  sensorRepository;
    private MqttHandler       mqttHandler;
    private TtsManager        ttsManager;
    private ILlmQueryHandler  llmHandler;
    private VoiceInputManager voiceInputManager;

    // ── Simulation ───────────────────────────────────────────────────────────
    private SensorDataSimulator simulator;
    private boolean liveDataReceived = false;

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView accelXValue, accelYValue, accelZValue;
    private TextView gyroXValue,  gyroYValue,  gyroZValue;
    private TextView magXValue,   magYValue,   magZValue;
    private TextView modeLabel;
    private TextView operatingModeLabel;
    private Button   btnStream, btnBurst, btnAverage;
    private Button   btnAutark, btnSupervision, btnEvent, btnIdentification;
    private ImageButton btnVoice;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        applyWindowInsets();

        // ── Get application-scoped container (no new instance on rotation) ──
        container = ((BiotApplication) getApplication()).getContainer();
        sensorRepository = container.sensorRepository();

        // ── Activity-scoped: MQTT + TTS (safe to call after rotation guard) ─
        try {
            container.initActivityScope(this);
        } catch (IllegalStateException e) {
            Toast.makeText(this, "MQTT Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        mqttHandler = container.mqttHandler();
        ttsManager  = container.ttsManager();
        llmHandler  = container.llmQueryHandler();

        // ── UI ───────────────────────────────────────────────────────────────
        bindNavigationButtons();
        bindSensorTextViews();
        bindModeButtons();
        bindOperatingModeButtons();
        bindVoiceButton();

        // ── Observers ────────────────────────────────────────────────────────
        observeLlmAction();
        observeLlmLoading();
        observeMqttStatus();

        // ── MQTT ─────────────────────────────────────────────────────────────
        wireMqttCallbacks();
        connectMqtt();

        // ── Voice ─────────────────────────────────────────────────────────────
        requestMicPermissionAndInitVoice();
    }

    @Override
    protected void onDestroy() {
        if (voiceInputManager != null) voiceInputManager.destroy();
        if (simulator != null)        simulator.stop();
        container.releaseActivityScope();
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LiveData observers
    // ─────────────────────────────────────────────────────────────────────────

    private void observeLlmAction() {
        container.liveAction().observe(this, action -> {
            if (action == null) return;
            speak(action.tts);
            switch (action.type) {
                case NAVIGATE:     handleNavigate(action.screen);               break;
                case MQTT_PUBLISH: handleMqttPublish(action.topic, action.payload); break;
                case APPLY_FILTER: broadcastFilter(action.minutes);             break;
                case CLEAR_FILTER: broadcastFilter(0);                          break;
                case ANSWER:       break;
            }
        });
    }

    private void observeLlmLoading() {
        container.llmLoading().observe(this, loading -> {
            if (Boolean.TRUE.equals(loading)) {
                Toast.makeText(this, "Asking BioT…", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void observeMqttStatus() {
        mqttHandler.connectionStatus().observe(this, connected -> {
            Log.i(TAG, "MQTT status: " + (Boolean.TRUE.equals(connected) ? "connected" : "disconnected"));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLM action dispatch (previously in LlmQueryHandler — now here)
    // ─────────────────────────────────────────────────────────────────────────

    private void handleNavigate(String screenName) {
        if (screenName == null) return;
        Class<?> target;
        switch (screenName) {
            case "MainActivity":      target = MainActivity.class;      break;
            case "AccelActivity":     target = AccelActivity.class;     break;
            case "GyroActivity":      target = GyroActivity.class;      break;
            case "MagnetActivity":    target = MagnetActivity.class;    break;
            case "MainGraphActivity": target = MainGraphActivity.class; break;
            case "EreignisActivity":  target = EreignisActivity.class;  break;
            case "SettingsActivity":  target = SettingsActivity.class;  break;
            default:
                Log.w(TAG, "Unknown screen: " + screenName);
                return;
        }
        startActivity(new Intent(this, target));
    }

    private void handleMqttPublish(String topic, String payload) {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            Toast.makeText(this, "MQTT not connected — command not sent", Toast.LENGTH_SHORT).show();
            return;
        }
        if (topic == null || payload == null) {
            Log.w(TAG, "mqtt_publish missing topic or payload");
            return;
        }
        mqttHandler.publish(topic, payload, true);
    }

    private void broadcastFilter(int minutes) {
        Intent broadcast = new Intent("com.fhdw.biot.speech.iot.FILTER_ACTION");
        broadcast.putExtra(VoiceCommandExecutor.EXTRA_FILTER_MINUTES, minutes);
        sendBroadcast(broadcast);
    }

    private void speak(String text) {
        if (ttsManager == null || text == null || text.trim().isEmpty()) return;
        ttsManager.speak(text);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI binding
    // ─────────────────────────────────────────────────────────────────────────

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private void bindNavigationButtons() {
        findViewById(R.id.btnGyro).setOnClickListener(v ->
                startActivity(new Intent(this, GyroActivity.class)));
        findViewById(R.id.btnAccel).setOnClickListener(v ->
                startActivity(new Intent(this, AccelActivity.class)));
        findViewById(R.id.btnMagnet).setOnClickListener(v ->
                startActivity(new Intent(this, MagnetActivity.class)));

        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EreignisActivity.class);
            intent.putExtra(VoiceCommandExecutor.EXTRA_SENSOR_FILTER, "ALL");
            startActivity(intent);
        });

        findViewById(R.id.graphenansicht).setOnClickListener(v ->
                startActivity(new Intent(this, MainGraphActivity.class)));
        findViewById(R.id.settings_button).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void bindSensorTextViews() {
        accelXValue = findViewById(R.id.accelXValue);
        accelYValue = findViewById(R.id.accelYValue);
        accelZValue = findViewById(R.id.accelZValue);
        gyroXValue  = findViewById(R.id.gyroXValue);
        gyroYValue  = findViewById(R.id.gyroYValue);
        gyroZValue  = findViewById(R.id.gyroZValue);
        magXValue   = findViewById(R.id.magXValue);
        magYValue   = findViewById(R.id.magYValue);
        magZValue   = findViewById(R.id.magZValue);
    }

    private void bindModeButtons() {
        btnStream  = findViewById(R.id.btnStream);
        btnBurst   = findViewById(R.id.btnBurst);
        btnAverage = findViewById(R.id.btnAverage);
        modeLabel  = findViewById(R.id.ModeLabel);

        btnStream.setOnClickListener(v -> {
            mqttPublishOrToast(VoiceCommandExecutor.TOPIC_MODE, "STREAM");
            highlightActiveMode(modeLabel, "Stream", btnStream, btnBurst, btnAverage);
        });
        btnBurst.setOnClickListener(v -> {
            mqttPublishOrToast(VoiceCommandExecutor.TOPIC_MODE, "BURST");
            highlightActiveMode(modeLabel, "Burst", btnBurst, btnStream, btnAverage);
        });
        btnAverage.setOnClickListener(v -> {
            mqttPublishOrToast(VoiceCommandExecutor.TOPIC_MODE, "AVERAGE");
            highlightActiveMode(modeLabel, "Average", btnAverage, btnStream, btnBurst);
        });
    }

    private void bindOperatingModeButtons() {
        operatingModeLabel = findViewById(R.id.OperatingModeLabel);
        btnAutark          = findViewById(R.id.btnAutark);
        btnSupervision     = findViewById(R.id.btnSupervision);
        btnEvent           = findViewById(R.id.btnEvent);
        btnIdentification  = findViewById(R.id.btnIdentification);

        if (btnAutark == null) return;

        btnAutark.setOnClickListener(v -> {
            mqttPublishOrToast(VoiceCommandExecutor.TOPIC_OPERATING_MODE, "AUTARK");
            highlightActiveMode(operatingModeLabel, "Autark",
                    btnAutark, btnSupervision, btnEvent, btnIdentification);
        });
        btnSupervision.setOnClickListener(v -> {
            mqttPublishOrToast(VoiceCommandExecutor.TOPIC_OPERATING_MODE, "SUPERVISION");
            highlightActiveMode(operatingModeLabel, "Supervision",
                    btnSupervision, btnAutark, btnEvent, btnIdentification);
        });
        btnEvent.setOnClickListener(v -> {
            mqttPublishOrToast(VoiceCommandExecutor.TOPIC_OPERATING_MODE, "EVENT");
            highlightActiveMode(operatingModeLabel, "Event",
                    btnEvent, btnAutark, btnSupervision, btnIdentification);
        });
        btnIdentification.setOnClickListener(v -> {
            mqttPublishOrToast(VoiceCommandExecutor.TOPIC_OPERATING_MODE, "IDENTIFICATION");
            highlightActiveMode(operatingModeLabel, "Identification",
                    btnIdentification, btnAutark, btnSupervision, btnEvent);
        });
    }

    private void bindVoiceButton() {
        btnVoice = findViewById(R.id.btnVoice);
        btnVoice.setOnClickListener(v -> onVoiceButtonClicked());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MQTT
    // ─────────────────────────────────────────────────────────────────────────

    private void wireMqttCallbacks() {
        mqttHandler.setMessageListener((topic, message) -> {
            Log.i(TAG, "MQTT → " + topic + " = " + message);
            runOnUiThread(() -> {
                try {
                    // Only real hardware publishes to Sensor/*; simulator uses Sensor/Sim/*.
                    if (!liveDataReceived
                            && topic.startsWith("Sensor/")
                            && !topic.startsWith("Sensor/Sim/")) {
                        liveDataReceived = true;
                        if (simulator != null) {
                            simulator.stop();
                            simulator = null;
                            Log.i(TAG, "Live hardware data received — simulator stopped");
                        }
                    }
                    dispatchMqttMessage(topic, message);
                } catch (Exception ex) {
                    Log.e(TAG, "MQTT handler error: " + ex.getMessage(), ex);
                }
            });
        });
    }

    private void dispatchMqttMessage(String topic, String message) {
        switch (topic) {
            case "Sensor/Bewegung":
            case "Sensor/Sim/Bewegung": handleMovementMessage(message); break;
            case "Sensor/Gyro":
            case "Sensor/Sim/Gyro":     handleGyroMessage(message);     break;
            case "Sensor/Magnet":
            case "Sensor/Sim/Magnet":   handleMagnetMessage(message);   break;
            case "Control/Mode":
                switch (message) {
                    case "STREAM":  highlightActiveMode(modeLabel, "Stream",  btnStream,  btnBurst,   btnAverage); break;
                    case "BURST":   highlightActiveMode(modeLabel, "Burst",   btnBurst,   btnStream,  btnAverage); break;
                    case "AVERAGE": highlightActiveMode(modeLabel, "Average", btnAverage, btnStream,  btnBurst);   break;
                }
                break;
            case "Control/OperatingMode":
                if (operatingModeLabel == null) break;
                switch (message) {
                    case "AUTARK":         highlightActiveMode(operatingModeLabel, "Autark",         btnAutark,         btnSupervision, btnEvent,       btnIdentification); break;
                    case "SUPERVISION":    highlightActiveMode(operatingModeLabel, "Supervision",    btnSupervision,    btnAutark,      btnEvent,       btnIdentification); break;
                    case "EVENT":          highlightActiveMode(operatingModeLabel, "Event",          btnEvent,          btnAutark,      btnSupervision, btnIdentification); break;
                    case "IDENTIFICATION": highlightActiveMode(operatingModeLabel, "Identification", btnIdentification, btnAutark,      btnSupervision, btnEvent);          break;
                }
                break;
            default:
                Log.w(TAG, "Unhandled topic: " + topic);
        }
    }

    private void connectMqtt() {
        mqttHandler.connect(new MqttHandler.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "MQTT connected");
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, getString(R.string.toast_mqtt_connected), Toast.LENGTH_SHORT).show());
                mqttHandler.subscribe("Sensor/Bewegung");
                mqttHandler.subscribe("Sensor/Gyro");
                mqttHandler.subscribe("Sensor/Magnet");
                mqttHandler.subscribe("Sensor/Sim/Bewegung");
                mqttHandler.subscribe("Sensor/Sim/Gyro");
                mqttHandler.subscribe("Sensor/Sim/Magnet");
                mqttHandler.subscribe("Control/Mode");
                mqttHandler.subscribe("Control/OperatingMode");
                scheduleSimulatorFallback();
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "MQTT connect failed: " + (t == null ? "?" : t.getMessage()), t);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.toast_mqtt_error, t == null ? "?" : t.getMessage()),
                            Toast.LENGTH_LONG).show();
                    startSimulator();
                });
            }
        });
    }

    private void scheduleSimulatorFallback() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!liveDataReceived) {
                Log.i(TAG, "No live MQTT data after fallback delay — starting simulator");
                Toast.makeText(this, getString(R.string.toast_no_hardware),
                        Toast.LENGTH_LONG).show();
                startSimulator();
            }
        }, BuildConfig.SIMULATOR_FALLBACK_DELAY_MS);
    }

    private void startSimulator() {
        if (simulator != null) return;
        simulator = new SensorDataSimulator(mqttHandler, BuildConfig.SIMULATOR_INTERVAL_MS);
        simulator.start();
        Log.i(TAG, "SensorDataSimulator started");
    }

    private void mqttPublishOrToast(String topic, String payload) {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            Toast.makeText(this, getString(R.string.toast_mqtt_not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        mqttHandler.publish(topic, payload, true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MQTT sensor message handlers — UI update + Repository write
    // ─────────────────────────────────────────────────────────────────────────

    private void handleMovementMessage(String message) {
        String[] p = message.split(",");
        if (p.length < 3) { Log.w(TAG, "Movement bad payload: " + message); return; }
        try {
            float x = Float.parseFloat(p[0].trim());
            float y = Float.parseFloat(p[1].trim());
            float z = Float.parseFloat(p[2].trim());

            accelXValue.setText(getString(R.string.beschleunigung_x, x));
            accelYValue.setText(getString(R.string.beschleunigung_y, y));
            accelZValue.setText(getString(R.string.beschleunigung_z, z));

            long now = System.currentTimeMillis();

            AccelData accelData = new AccelData();
            accelData.timestamp = now;
            accelData.accelX = x; accelData.accelY = y; accelData.accelZ = z;

            ValueSensor vs = new ValueSensor();
            vs.value1 = x; vs.value2 = y; vs.value3 = z;

            sensorRepository.insertAccel(accelData);
            sensorRepository.insertValueSensor(vs);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Movement parse error: " + e.getMessage(), e);
        }
    }

    private void handleGyroMessage(String message) {
        String[] g = message.split(",");
        if (g.length < 3) { Log.w(TAG, "Gyro bad payload: " + message); return; }
        try {
            float x = Float.parseFloat(g[0].trim());
            float y = Float.parseFloat(g[1].trim());
            float z = Float.parseFloat(g[2].trim());

            gyroXValue.setText(getString(R.string.gyro_x, x));
            gyroYValue.setText(getString(R.string.gyro_y, y));
            gyroZValue.setText(getString(R.string.gyro_z, z));

            long now = System.currentTimeMillis();

            GyroData gyroData = new GyroData();
            gyroData.timestamp = now;
            gyroData.gyroX = x; gyroData.gyroY = y; gyroData.gyroZ = z;

            ValueSensor vs = new ValueSensor();
            vs.value4 = x; vs.value5 = y; vs.value6 = z;

            sensorRepository.insertGyro(gyroData);
            sensorRepository.insertValueSensor(vs);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Gyro parse error: " + e.getMessage(), e);
        }
    }

    private void handleMagnetMessage(String message) {
        String[] m = message.split(",");
        if (m.length < 3) { Log.w(TAG, "Magnet bad payload: " + message); return; }
        try {
            float x = Float.parseFloat(m[0].trim());
            float y = Float.parseFloat(m[1].trim());
            float z = Float.parseFloat(m[2].trim());

            magXValue.setText(getString(R.string.magnet_x, x));
            magYValue.setText(getString(R.string.magnet_y, y));
            magZValue.setText(getString(R.string.magnet_z, z));

            long now = System.currentTimeMillis();

            MagnetData magnetData = new MagnetData();
            magnetData.timestamp = now;
            magnetData.magnetX = x; magnetData.magnetY = y; magnetData.magnetZ = z;

            sensorRepository.insertMagnet(magnetData);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Magnet parse error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Voice — permission
    // ─────────────────────────────────────────────────────────────────────────

    private void requestMicPermissionAndInitVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            initVoiceInputManager();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initVoiceInputManager();
            } else {
                Toast.makeText(this,
                        getString(R.string.toast_mic_denied),
                        Toast.LENGTH_LONG).show();
                if (btnVoice != null) btnVoice.setEnabled(false);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Voice — wiring
    // ─────────────────────────────────────────────────────────────────────────

    private void initVoiceInputManager() {
        try {
            voiceInputManager = new VoiceInputManager(this, LanguageManager.getLanguageTag(this), new VoiceInputManager.VoiceResultListener() {
                @Override
                public void onResult(String topResult, List<String> hypotheses) {
                    Log.i(TAG, "Voice result: \"" + topResult + "\"");
                    VoiceCommand cmd = VoiceCommandResolver.resolveFromList(hypotheses);
                    Log.i(TAG, "Resolved command: " + cmd);

                    boolean handledLocally = VoiceCommandExecutor.execute(
                            MainActivity.this, cmd, mqttHandler, llmHandler, topResult);

                    if (handledLocally && ttsManager != null) {
                        String confirmation = toConfirmation(cmd);
                        if (!confirmation.isEmpty()) ttsManager.speak(confirmation);
                    }
                }

                @Override
                public void onError(int errorCode, String message) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Language: " + message, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onListeningStarted() {
                    runOnUiThread(() -> {
                        if (btnVoice != null) {
                            btnVoice.setColorFilter(ContextCompat.getColor(
                                    MainActivity.this, android.R.color.holo_red_light));
                        }
                    });
                }

                @Override
                public void onListeningEnded() {
                    runOnUiThread(() -> {
                        if (btnVoice != null) btnVoice.clearColorFilter();
                    });
                }
            });
            Log.i(TAG, "VoiceInputManager initialised.");
        } catch (IllegalStateException e) {
            Log.e(TAG, "VoiceInputManager init failed: " + e.getMessage());
            Toast.makeText(this,
                    "Voice recognition not available on this device.",
                    Toast.LENGTH_LONG).show();
            if (btnVoice != null) btnVoice.setEnabled(false);
        }
    }

    private void onVoiceButtonClicked() {
        if (voiceInputManager == null) {
            Toast.makeText(this, "Voice recognition not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (voiceInputManager.isListening()) voiceInputManager.stopListening();
        else                                  voiceInputManager.startListening();
    }

    private String toConfirmation(VoiceCommand cmd) {
        switch (cmd) {
            case NAV_ACCEL:                     return "Opening Acceleration";
            case NAV_GYRO:                      return "Opening Gyroscope";
            case NAV_MAGNET:                    return "Opening Magnetic Field";
            case NAV_MIC:                       return "Microphone overview";
            case NAV_GRAPH:                     return "Opening Graph";
            case NAV_EVENTS:                    return "Opening Events";
            case NAV_HOME:                      return "Going home";
            case NAV_SETTINGS:                  return "Opening Settings";
            case NAV_ACCEL_FILTER_10MIN:        return "Acceleration, last ten minutes";
            case NAV_GYRO_FILTER_10MIN:         return "Gyroscope, last ten minutes";
            case NAV_MAGNET_FILTER_10MIN:       return "Magnetic field, last ten minutes";
            case FILTER_LAST_5MIN:              return "Last five minutes";
            case FILTER_LAST_10MIN:             return "Last ten minutes";
            case FILTER_LAST_30MIN:             return "Last thirty minutes";
            case FILTER_LAST_1H:                return "Last hour";
            case FILTER_LAST_24H:               return "Last twenty four hours";
            case FILTER_CLEAR:                  return "Filter cleared";
            case MODE_STREAM:                   return "Stream mode";
            case MODE_BURST:                    return "Burst mode";
            case MODE_AVERAGE:                  return "Average mode";
            case OPMODE_AUTARK:                 return "Autark mode";
            case OPMODE_SUPERVISION:            return "Supervision mode";
            case OPMODE_EVENT:                  return "Event mode";
            case OPMODE_IDENTIFICATION:         return "Identification mode";
            case START_CALIBRATION:             return "Calibration is not yet implemented";
            case SHOW_EVENTS:                   return "Showing events";
            case SHOW_NOTIFICATIONS:            return "Showing notifications";
            case COMBINED_MOTION:               return "Movement overview";
            case COMBINED_VIBRATION_SOUND:      return "Vibration and sound";
            case COMBINED_ORIENTATION_MAGNETIC: return "Orientation and magnetic field";
            case COMBINED_ALL_SENSORS:          return "All sensors";
            case SYSTEM_HELP:                   return "Here are the available commands";
            case UNKNOWN:
            default:                            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void highlightActiveMode(TextView label, String modeName,
                                     Button active, Button... others) {
        if (active != null) active.setAlpha(1.0f);
        for (Button b : others) if (b != null) b.setAlpha(0.4f);
        if (label == null) return;
        // Use the correct prefix depending on which label is being updated.
        int fmtRes = (label == operatingModeLabel)
                ? R.string.label_betriebsmodus_format
                : R.string.label_modus_format;
        label.setText(getString(fmtRes, modeName));
    }
}
