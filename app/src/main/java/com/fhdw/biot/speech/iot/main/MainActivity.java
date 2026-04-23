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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.di.AppContainer;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.MainGraphActivity;
import com.fhdw.biot.speech.iot.mqtt.MqttHandler;
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
import database.DB;
import database.dao.SensorDao;
import database.dao.ValueSensorDAO;
import database.entities.AccelData;
import database.entities.GyroData;
import database.entities.MagnetData;
import database.entities.ValueSensor;
import java.util.List;

/**
 * MainActivity
 * ─────────────────────────────────────────────────────────────────────────────
 *  - Subscribes to ESP8266 sensor topics via MQTT and writes data into Room DB.
 *  - Exposes mode-control buttons:
 *      • Stream / Burst / Average  → Control/Mode (transmission cadence)
 *      • Autark / Supervision /
 *        Event  / Identification   → Control/OperatingMode (operating mode)
 *  - Push-to-talk voice button → VoiceInputManager
 *      → VoiceCommandResolver → VoiceCommandExecutor
 *      → either local action OR forwarded to LlmQueryHandler (via ILlmQueryHandler).
 *
 * All collaborators come from {@link AppContainer}. No hardcoded broker URLs,
 * no hardcoded LLM endpoints, no {@code llmHandler = null} placeholder.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO = 101;

    // ── DI container ─────────────────────────────────────────────────────────
    private final AppContainer container = new AppContainer();

    // ── References pulled from the container in onCreate ─────────────────────
    private SensorDao         sensorDao;
    private ValueSensorDAO    valueSensorDao;
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

        // ── DI: build everything once, pull references ──────────────────────
        container.initApplicationScope(this);
        sensorDao      = container.sensorDao();
        valueSensorDao = container.valueSensorDao();

        try {
            container.initActivityScope(this);
        } catch (IllegalStateException e) {
            Toast.makeText(this, "MQTT Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        mqttHandler = container.mqttHandler();
        ttsManager  = container.ttsManager();
        llmHandler  = container.llmQueryHandler();

        // ── UI bindings ─────────────────────────────────────────────────────
        bindNavigationButtons();
        bindSensorTextViews();
        bindModeButtons();
        bindOperatingModeButtons();
        bindVoiceButton();

        // ── MQTT subscriptions + connection ─────────────────────────────────
        wireMqttCallbacks();
        connectMqtt();

        // ── Voice setup (after permission grant) ────────────────────────────
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
        // These views are added in activity_main.xml. If the layout hasn't been
        // updated yet (unlikely but defensive), findViewById returns null and we
        // just skip — the voice commands still publish on Control/OperatingMode.
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
                    // Stop simulator the first time real hardware data arrives.
                    if (!liveDataReceived && topic.startsWith("Sensor/")) {
                        liveDataReceived = true;
                        if (simulator != null) {
                            simulator.stop();
                            simulator = null;
                            Log.i(TAG, "Live data received — simulator stopped");
                        }
                    }

                    switch (topic) {
                        case "Sensor/Bewegung":  handleMovementMessage(message); break;
                        case "Sensor/Gyro":      handleGyroMessage(message);     break;
                        case "Sensor/Magnet":    handleMagnetMessage(message);   break;
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
                                case "AUTARK":         highlightActiveMode(operatingModeLabel, "Autark",         btnAutark,         btnSupervision, btnEvent,      btnIdentification); break;
                                case "SUPERVISION":    highlightActiveMode(operatingModeLabel, "Supervision",    btnSupervision,    btnAutark,      btnEvent,      btnIdentification); break;
                                case "EVENT":          highlightActiveMode(operatingModeLabel, "Event",          btnEvent,          btnAutark,      btnSupervision, btnIdentification); break;
                                case "IDENTIFICATION": highlightActiveMode(operatingModeLabel, "Identification", btnIdentification, btnAutark,      btnSupervision, btnEvent);          break;
                            }
                            break;
                        default:
                            Log.w(TAG, "Unhandled topic: " + topic);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "MQTT handler error: " + ex.getMessage(), ex);
                }
            });
        });
    }

    private void connectMqtt() {
        mqttHandler.connect(new MqttHandler.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "MQTT connected");
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "MQTT verbunden",
                                Toast.LENGTH_SHORT).show());
                mqttHandler.subscribe("Sensor/Bewegung");
                mqttHandler.subscribe("Sensor/Gyro");
                mqttHandler.subscribe("Sensor/Magnet");
                mqttHandler.subscribe("Control/Mode");
                mqttHandler.subscribe("Control/OperatingMode");
                loadDatabaseValues();
                scheduleSimulatorFallback();
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "MQTT connect failed: " + (t == null ? "?" : t.getMessage()), t);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "MQTT Fehler: " + (t == null ? "?" : t.getMessage()),
                            Toast.LENGTH_LONG).show();
                    startSimulator();
                });
            }
        });
    }

    /**
     * If no real sensor data arrives within 5 seconds of connecting, fall back to
     * the simulator so charts are not empty during demos without hardware.
     */
    private void scheduleSimulatorFallback() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!liveDataReceived) {
                Log.i(TAG, "No live MQTT data after 5 s — starting simulator");
                Toast.makeText(this, "Kein Hardware gefunden – Simulator gestartet",
                        Toast.LENGTH_LONG).show();
                startSimulator();
            }
        }, 5_000);
    }

    private void startSimulator() {
        if (simulator != null) return;
        simulator = new SensorDataSimulator(mqttHandler, 500);
        simulator.start();
        Log.i(TAG, "SensorDataSimulator started");
    }

    private void mqttPublishOrToast(String topic, String payload) {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            Toast.makeText(this, "MQTT nicht verbunden", Toast.LENGTH_SHORT).show();
            return;
        }
        mqttHandler.publish(topic, payload, true);
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
                        "Mikrofon-Zugriff verweigert. Sprachbefehle nicht verfügbar.",
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
            voiceInputManager = new VoiceInputManager(this, new VoiceInputManager.VoiceResultListener() {
                @Override
                public void onResult(String topResult, List<String> hypotheses) {
                    Log.i(TAG, "Voice result: \"" + topResult + "\"");
                    VoiceCommand cmd = VoiceCommandResolver.resolveFromList(hypotheses);
                    Log.i(TAG, "Resolved command: " + cmd);

                    boolean handledLocally = VoiceCommandExecutor.execute(
                            MainActivity.this, cmd, mqttHandler, llmHandler, topResult);

                    // Speak a short confirmation only when the executor handled the
                    // command locally. If it was forwarded to the LLM, the LLM's reply
                    // already contains the user-facing TTS so we don't double-speak.
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
                            btnVoice.setColorFilter(
                                    ContextCompat.getColor(MainActivity.this,
                                            android.R.color.holo_red_light));
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

    /**
     * Map a resolved VoiceCommand to a short confirmation phrase.
     * Returns empty string for commands forwarded to the LLM (the LLM's reply
     * already supplies the TTS).
     */
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
            case UNKNOWN:                       return "";  // LLM might be handling it
            default:                            return "";  // anything LLM-forwarded
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MQTT message handlers (UI + Room writes)
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

            DB.databaseWriteExecutor.execute(() -> {
                sensorDao.insert(accelData);
                valueSensorDao.insert(vs);
            });
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

            DB.databaseWriteExecutor.execute(() -> {
                sensorDao.insert(gyroData);
                valueSensorDao.insert(vs);
            });
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

            DB.databaseWriteExecutor.execute(() -> sensorDao.insert(magnetData));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Magnet parse error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void highlightActiveMode(TextView label, String modeName,
                                     Button active, Button... others) {
        if (active != null) active.setAlpha(1.0f);
        for (Button b : others) if (b != null) b.setAlpha(0.4f);
        if (label != null) label.setText("Modus: " + modeName);
    }

    private void loadDatabaseValues() {
        DB.databaseWriteExecutor.execute(() -> {
            try {
                List<ValueSensor> list = valueSensorDao.getAllvalue();
                Log.i(TAG, "ValueSensor DB SIZE = " + list.size());
            } catch (Exception e) {
                Log.e(TAG, "loadDatabaseValues error: " + e.getMessage(), e);
            }
        });
    }
}
