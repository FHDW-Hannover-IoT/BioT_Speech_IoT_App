package com.fhdw.biot.speech.iot;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.UUID;

import database.InitDb;
import database.ValueSensor;

public class MainActivity extends AppCompatActivity {

    private static final String PHONE_BROKER = "tcp://192.168.178.31:1883";   // replace with your PC LAN IP
    private static final String EMULATOR_BROKER = "tcp://10.0.2.2:1883";
    private static final String TAG = "MainActivity";

    private MqttHandler mqttHandler;
    private TextView datenBewegung;
    private TextView datenZeit;
    private TextView datenGyro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_main);
        datenBewegung = findViewById(R.id.Daten_Bewegung);
        datenGyro = findViewById(R.id.Daten_Gyro);
        datenZeit = findViewById(R.id.Daten_Zeit);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        final String clientId = "Nutzer_" + UUID.randomUUID().toString().substring(0,8);
        final String brokerUrl = getBrokerUrl();
        Log.i(TAG, "Using brokerUrl=" + brokerUrl + " clientId=" + clientId);

        try {
            mqttHandler = new MqttHandler(brokerUrl, clientId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MqttHandler: " + e.getMessage(), e);
            Toast.makeText(this, "MQTT client creation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        mqttHandler.setMessageListener((topic, message) -> {
            Log.i(TAG, "messageListener -> topic=" + topic + " msg=" + message);
            runOnUiThread(() -> {
                try {
                    switch (topic) {
                        case "Sensor/Bewegung": {
                            datenBewegung.setText(message);
                            String[] p = message.split(",");
                            if (p.length >= 3) {
                                ValueSensor s = new ValueSensor();
                                s.value1 = Float.parseFloat(p[0].trim());
                                s.value2 = Float.parseFloat(p[1].trim());
                                s.value3 = Float.parseFloat(p[2].trim());
                                storeSensor(s);
                            }
                            break;
                        }
                        case "Sensor/Gyro": {
                            datenGyro.setText(message);
                            String[] g = message.split(",");
                            if (g.length >= 3) {
                                ValueSensor s = new ValueSensor();
                                s.value4 = Float.parseFloat(g[0].trim());
                                s.value5 = Float.parseFloat(g[1].trim());
                                s.value6 = Float.parseFloat(g[2].trim());
                                storeSensor(s);
                            }
                            break;
                        }
                        case "Sensor/Zeit": {
                            datenZeit.setText(message);
                            ValueSensor s = new ValueSensor();
                            s.value7 = message;
                            storeSensor(s);
                            break;
                        }
                        default:
                            Log.w(TAG, "Unhandled topic: " + topic);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Error handling message: " + ex.getMessage(), ex);
                }
            });
        });

        mqttHandler.connect(new MqttHandler.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "MQTT connected callback");
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "MQTT verbunden", Toast.LENGTH_SHORT).show());

                // Subscribe and publish after connect
                mqttHandler.subscribe("Sensor/Bewegung");
                mqttHandler.subscribe("Sensor/Gyro");
                mqttHandler.subscribe("Sensor/Zeit");

                // publish some retained test messages so subscriber will receive them on subscribe
                mqttHandler.publish("Sensor/Bewegung", "-0.788295,4.259267,0.982682", true);
                mqttHandler.publish("Sensor/Gyro", "1.265565,0.301551,0.052052", true);
                mqttHandler.publish("Sensor/Zeit", "1970-01-01T00:01:10.215742Z", true);

                loadDatabaseValues();
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "MQTT connect failed: " + (t == null ? "unknown" : t.getMessage()), t);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "MQTT Fehler: " + (t == null ? "unknown" : t.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (mqttHandler != null) mqttHandler.disconnect();
        super.onDestroy();
    }

    private void storeSensor(ValueSensor sensor) {
        new Thread(() -> {
            try {
                if (InitDb.appDatabase != null) {
                    InitDb.appDatabase.valueDao().insert(sensor);
                } else {
                    Log.w(TAG, "storeSensor: appDatabase is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "storeSensor exception: " + e.getMessage(), e);
            }
        }).start();
    }

    private void loadDatabaseValues() {
        new Thread(() -> {
            try {
                if (InitDb.appDatabase != null) {
                    List<ValueSensor> list = InitDb.appDatabase.valueDao().getAllvalue();
                    Log.i(TAG, "DB SIZE = " + list.size());
                } else {
                    Log.w(TAG, "loadDatabaseValues: appDatabase is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "loadDatabaseValues exception: " + e.getMessage(), e);
            }
        }).start();
    }

    private String getBrokerUrl() {
        boolean isEmulator =
                android.os.Build.FINGERPRINT.contains("generic")
                        || android.os.Build.MODEL.contains("Emulator")
                        || android.os.Build.MODEL.contains("Android SDK");
        return isEmulator ? EMULATOR_BROKER : PHONE_BROKER;
    }
}
