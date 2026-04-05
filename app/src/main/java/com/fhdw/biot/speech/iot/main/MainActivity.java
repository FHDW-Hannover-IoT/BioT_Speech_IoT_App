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
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.MainGraphActivity;
import com.fhdw.biot.speech.iot.mqtt.MqttHandler;
import com.fhdw.biot.speech.iot.sensor.AccelActivity;
import com.fhdw.biot.speech.iot.sensor.GyroActivity;
import com.fhdw.biot.speech.iot.sensor.MagnetActivity;
import com.fhdw.biot.speech.iot.settings.SettingsActivity;
import com.fhdw.biot.speech.iot.simulation.SensorDataSimulator;
import com.fhdw.biot.speech.iot.voice.TtsManager;
import com.fhdw.biot.speech.iot.voice.ILlmQueryHandler;
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
import java.util.Locale;
import java.util.UUID;

/**
 * MainActivity
 * ─────────────────────────────────────────────────────────────────────────────
 * MQTT-based version:
 *  - Subscribes to ESP8266 sensor topics and writes data into Room DB.
 *  - Exposes mode-control buttons (Stream / Burst / Average).
 *  - Push-to-talk voice button → VoiceInputManager → VoiceCommandResolver
 *    → VoiceCommandExecutor (no popup dialog).
 */
public class MainActivity extends AppCompatActivity {

    // ── Broker URLs ──────────────────────────────────────────────────────────
    private static final String PHONE_BROKER    = "tcp://192.168.178.80:1883";
    private static final String EMULATOR_BROKER = "tcp://10.0.2.2:1883";
    private static final String TAG             = "MainActivity";

    // ── Permission request code ───────────────────────────────────────────────
    private static final int REQUEST_RECORD_AUDIO = 101;

    // ── MQTT ─────────────────────────────────────────────────────────────────
    private MqttHandler mqttHandler;
    private SensorDataSimulator dataSimulator;

    // ── Room DAOs ────────────────────────────────────────────────────────────
    private SensorDao      sensorDao;
    private ValueSensorDAO valueSensorDao;

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView accelXValue, accelYValue, accelZValue;
    private TextView gyroXValue,  gyroYValue,  gyroZValue;
    private TextView magXValue,   magYValue,   magZValue;
    private TextView micValueText;
    private TextView ModeLabel;
    private Button   btnStream, btnBurst, btnAverage;
    private ImageButton btnVoice;

    // ── Voice ─────────────────────────────────────────────────────────────────
    private VoiceInputManager voiceInputManager;

    private TtsManager ttsManager;

    /**
     * LLM handler — null until Phase 4.
     * The executor shows a toast gracefully when this is null.
     */
    private final ILlmQueryHandler llmHandler = null;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // ── Edge-to-edge padding ─────────────────────────────────────────────
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top,
                            systemBars.right, systemBars.bottom);
                    return insets;
                });

        // ── Navigation buttons ───────────────────────────────────────────────
        findViewById(R.id.btnGyro).setOnClickListener(v ->
                startActivity(new Intent(this, GyroActivity.class)));
        findViewById(R.id.btnAccel).setOnClickListener(v ->
                startActivity(new Intent(this, AccelActivity.class)));
        findViewById(R.id.btnMagnet).setOnClickListener(v ->
                startActivity(new Intent(this, MagnetActivity.class)));

        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EreignisActivity.class);
            intent.putExtra("SENSOR_FILTER", "ALL");
            startActivity(intent);
        });

        findViewById(R.id.graphenansicht).setOnClickListener(v ->
                startActivity(new Intent(this, MainGraphActivity.class)));
        findViewById(R.id.settings_button).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // ── Mode buttons ─────────────────────────────────────────────────────
        btnStream  = findViewById(R.id.btnStream);
        btnBurst   = findViewById(R.id.btnBurst);
        btnAverage = findViewById(R.id.btnAverage);
        ModeLabel  = findViewById(R.id.ModeLabel);

        btnStream.setOnClickListener(v -> {
            if (mqttHandler != null) mqttHandler.publish("Control/Mode", "STREAM", true);
            highlightActiveMode(btnStream, "Stream", btnBurst, btnAverage);
        });
        btnBurst.setOnClickListener(v -> {
            if (mqttHandler != null) mqttHandler.publish("Control/Mode", "BURST", true);
            highlightActiveMode(btnBurst, "Burst", btnStream, btnAverage);
        });
        btnAverage.setOnClickListener(v -> {
            if (mqttHandler != null) mqttHandler.publish("Control/Mode", "AVERAGE", true);
            highlightActiveMode(btnAverage, "Average", btnStream, btnBurst);
        });

        // ── TextViews ────────────────────────────────────────────────────────
        accelXValue  = findViewById(R.id.accelXValue);
        accelYValue  = findViewById(R.id.accelYValue);
        accelZValue  = findViewById(R.id.accelZValue);
        gyroXValue   = findViewById(R.id.gyroXValue);
        gyroYValue   = findViewById(R.id.gyroYValue);
        gyroZValue   = findViewById(R.id.gyroZValue);
        magXValue    = findViewById(R.id.magXValue);
        magYValue    = findViewById(R.id.magYValue);
        magZValue    = findViewById(R.id.magZValue);
        micValueText = findViewById(R.id.micValue);

        // ── Voice button ─────────────────────────────────────────────────────
        btnVoice = findViewById(R.id.btnVoice);
        btnVoice.setOnClickListener(v -> onVoiceButtonClicked());

        // ── Room ─────────────────────────────────────────────────────────────
        DB db = DB.getDatabase(this);
        sensorDao      = db.sensorDao();
        valueSensorDao = db.valueSensorDao();

        // ── MQTT ─────────────────────────────────────────────────────────────
        final String clientId  = "Nutzer_" + UUID.randomUUID().toString().substring(0, 8);
        final String brokerUrl = getBrokerUrl();
        Log.i(TAG, "brokerUrl=" + brokerUrl + " clientId=" + clientId);

        try {
            mqttHandler = new MqttHandler(brokerUrl, clientId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MqttHandler: " + e.getMessage(), e);
            Toast.makeText(this, "MQTT Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        mqttHandler.setMessageListener((topic, message) -> {
            Log.i(TAG, "MQTT → " + topic + " = " + message);
            runOnUiThread(() -> {
                try {
                    switch (topic) {
                        case "Sensor/Bewegung":   handleMovementMessage(message); break;
                        case "Sensor/Gyro":       handleGyroMessage(message);     break;
                        case "Sensor/Magnet":     handleMagnetMessage(message);   break;
                        case "Sensor/Mic":        handleMicMessage(message);      break;
                        case "Control/Mode":
                            switch (message) {
                                case "STREAM":  highlightActiveMode(btnStream,  "Stream",  btnBurst, btnAverage); break;
                                case "BURST":   highlightActiveMode(btnBurst,   "Burst",   btnStream, btnAverage); break;
                                case "AVERAGE": highlightActiveMode(btnAverage, "Average", btnStream, btnBurst);  break;
                            }
                            break;
                        default: Log.w(TAG, "Unhandled topic: " + topic);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "MQTT handler error: " + ex.getMessage(), ex);
                }
            });
        });

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
                mqttHandler.subscribe("Sensor/Mic");
                mqttHandler.subscribe("Control/Mode");
                loadDatabaseValues();
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "MQTT connect failed: " + (t == null ? "?" : t.getMessage()), t);
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "MQTT Fehler: " + (t == null ? "?" : t.getMessage()),
                                Toast.LENGTH_LONG).show());
            }
        });

        // ── Request mic permission + init VoiceInputManager ──────────────────
        requestMicPermissionAndInitVoice();
        initTts();
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
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
    // Voice — setup
    // ─────────────────────────────────────────────────────────────────────────

    private void initTts() {
        ttsManager = new TtsManager(this);
    }

    /**
     * Map a resolved VoiceCommand to a short German confirmation phrase.
     * For UNKNOWN or unhandled (forwarded to LLM), return an empty string
     * so nothing is spoken (the executor already shows a toast).
     */
    private String toConfirmation(VoiceCommand cmd, boolean handled) {
        if (!handled) return ""; // LLM will respond — don't double-speak
        switch (cmd) {
            case NAV_ACCEL:              return "Öffne Beschleunigung";
            case NAV_GYRO:               return "Öffne Gyroskop";
            case NAV_MAGNET:             return "Öffne Magnetfeld";
            case NAV_MIC:                return "Mikrofon-Ansicht";
            case NAV_GRAPH:              return "Öffne Grafiken";
            case NAV_EVENTS:             return "Öffne Ereignisse";
            case NAV_HOME:               return "Zurück zur Hauptseite";
            case NAV_SETTINGS:           return "Öffne Einstellungen";
            case NAV_ACCEL_FILTER_10MIN: return "Beschleunigung, letzte 10 Minuten";
            case NAV_GYRO_FILTER_10MIN:  return "Gyroskop, letzte 10 Minuten";
            case NAV_MAGNET_FILTER_10MIN:return "Magnetfeld, letzte 10 Minuten";
            case FILTER_LAST_5MIN:       return "Filter: 5 Minuten";
            case FILTER_LAST_10MIN:      return "Filter: 10 Minuten";
            case FILTER_LAST_30MIN:      return "Filter: 30 Minuten";
            case FILTER_LAST_1H:         return "Filter: 1 Stunde";
            case FILTER_LAST_24H:        return "Filter: 24 Stunden";
            case FILTER_CLEAR:           return "Filter zurückgesetzt";
            case MODE_STREAM:            return "Stream-Modus aktiviert";
            case MODE_BURST:             return "Burst-Modus aktiviert";
            case MODE_AVERAGE:           return "Durchschnitt-Modus aktiviert";
            case COMBINED_MOTION:        return "Bewegungsübersicht";
            case COMBINED_VIBRATION_SOUND: return "Vibration und Lautstärke";
            case COMBINED_ORIENTATION_MAGNETIC: return "Orientierung und Magnetfeld";
            case COMBINED_ALL_SENSORS:   return "Alle Sensoren";
            case SYSTEM_HELP:            return "Hier sind verfügbare Befehle";
            case UNKNOWN:
            default:                     return "Befehl nicht erkannt";
        }
    }

    private void initVoiceInputManager() {
        try {
            voiceInputManager = new VoiceInputManager(this, new VoiceInputManager.VoiceResultListener() {

                @Override
                public void onResult(String topResult, List<String> hypotheses) {
                    Log.i(TAG, "Voice result: \"" + topResult + "\"");
                    VoiceCommand cmd = VoiceCommandResolver.resolveFromList(hypotheses);
                    Log.i(TAG, "Resolved command: " + cmd);

                    boolean handled = VoiceCommandExecutor.execute(
                            MainActivity.this, cmd, mqttHandler, llmHandler, topResult);

                    // Speak back a short confirmation so the user knows the command worked
                    if (ttsManager != null) {
                        ttsManager.speak(toConfirmation(cmd, handled));
                    }
                }

                @Override
                public void onError(int errorCode, String message) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Sprache: " + message, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onListeningStarted() {
                    // Visual feedback — tint button red while mic is open
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
                    // Restore button to normal colour
                    runOnUiThread(() -> {
                        if (btnVoice != null) {
                            btnVoice.clearColorFilter();
                        }
                    });
                }
            });

            Log.i(TAG, "VoiceInputManager initialised.");

        } catch (IllegalStateException e) {
            Log.e(TAG, "VoiceInputManager init failed: " + e.getMessage());
            Toast.makeText(this,
                    "Spracherkennung nicht verfügbar auf diesem Gerät.",
                    Toast.LENGTH_LONG).show();
            if (btnVoice != null) btnVoice.setEnabled(false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Voice — button click (push-to-talk toggle)
    // ─────────────────────────────────────────────────────────────────────────

    private void onVoiceButtonClicked() {
        if (voiceInputManager == null) {
            Toast.makeText(this, "Spracherkennung nicht bereit.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (voiceInputManager.isListening()) {
            voiceInputManager.stopListening();
        } else {
            voiceInputManager.startListening();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        if (dataSimulator    != null) dataSimulator.stop();
        if (mqttHandler      != null) mqttHandler.disconnect();
        if (voiceInputManager != null) voiceInputManager.destroy();
        if (ttsManager != null) ttsManager.destroy();
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MQTT message handlers
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
            accelData.timestamp = now; accelData.accelX = x;
            accelData.accelY = y;     accelData.accelZ = z;

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
            gyroData.timestamp = now; gyroData.gyroX = x;
            gyroData.gyroY = y;      gyroData.gyroZ = z;

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
            magnetData.timestamp = now; magnetData.magnetX = x;
            magnetData.magnetY = y;    magnetData.magnetZ = z;

            DB.databaseWriteExecutor.execute(() -> sensorDao.insert(magnetData));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Magnet parse error: " + e.getMessage(), e);
        }
    }

    private void handleMicMessage(String message) {
        try {
            String[] parts = message.split(",");
            int displayValue = Integer.parseInt(parts[parts.length - 1].trim());
            if (micValueText != null) micValueText.setText("Wert: " + displayValue);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Mic parse error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void highlightActiveMode(Button active, String modeName, Button... others) {
        active.setAlpha(1.0f);
        for (Button b : others) b.setAlpha(0.4f);
        if (ModeLabel != null) ModeLabel.setText("Modus: " + modeName);
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

    // ─────────────────────────────────────────────────────────────────────────
    // Emulator detection
    // ─────────────────────────────────────────────────────────────────────────

    private String getBrokerUrl() {
        String f = (android.os.Build.FINGERPRINT == null ? "" : android.os.Build.FINGERPRINT)
                .toLowerCase(Locale.US);
        String m = (android.os.Build.MODEL == null ? "" : android.os.Build.MODEL)
                .toLowerCase(Locale.US);
        String p = (android.os.Build.PRODUCT == null ? "" : android.os.Build.PRODUCT)
                .toLowerCase(Locale.US);

        boolean isEmulator = f.startsWith("generic") || f.contains("vbox")
                || f.contains("test-keys")          || m.contains("google_sdk")
                || m.contains("emulator")           || m.contains("android sdk built for x86")
                || p.contains("sdk_gphone");

        return isEmulator ? EMULATOR_BROKER : PHONE_BROKER;
    }
}