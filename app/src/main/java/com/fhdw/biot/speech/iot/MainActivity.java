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

/**
 * MainActivity
 * ------------
 * This is the main screen of the app.
 *
 * Responsibilities:
 * - Set up the UI (three TextViews showing movement, gyro and time data).
 * - Establish an MQTT connection using {@link MqttHandler}.
 * - Subscribe to three sensor topics and publish test values once connected.
 * - Persist received sensor values to the local Room database via {@link InitDb}.
 * - Load and log the number of stored sensor records at startup.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * MQTT broker URL used when the app is running on a physical phone.
     * <p>
     * Format: tcp://&lt;PC_LAN_IP&gt;:1883
     *  - Host: the IPv4 address of the PC where Mosquitto (or another broker) is running.
     *  - Port: 1883 (default MQTT port, non-TLS).
     */
    private static final String PHONE_BROKER = "tcp://172.17.160.183:1883";

    /**
     * MQTT broker URL used when the app is running on an Android emulator.
     * <p>
     * The special IP 10.0.2.2 points from the emulator to the host machine.
     */
    private static final String EMULATOR_BROKER = "tcp://10.0.2.2:1883";

    /**
     * Tag used for Android Logcat logging.
     */
    private static final String TAG = "MainActivity";

    /**
     * Wrapper around the Eclipse Paho MQTT client.
     * Handles connect / subscribe / publish / disconnect.
     */
    private MqttHandler mqttHandler;

    /**
     * TextView that displays movement sensor data (Sensor/Bewegung).
     */
    private TextView datenBewegung;

    /**
     * TextView that displays time data (Sensor/Zeit).
     */
    private TextView datenZeit;

    /**
     * TextView that displays gyro sensor data (Sensor/Gyro).
     */
    private TextView datenGyro;

    /**
     * Lifecycle callback: called when the activity is first created.
     *
     * @param savedInstanceState previous state of the activity (if it was
     *                           destroyed and recreated), or {@code null}
     *                           when started fresh.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable "edge to edge" drawing (under system bars) using the AndroidX helper.
        EdgeToEdge.enable(this);

        // Inflate the layout XML and attach it to this Activity.
        setContentView(R.layout.activity_main);

        // Resolve and cache references to the three TextViews from the layout.
        datenBewegung = findViewById(R.id.Daten_Bewegung);
        datenGyro = findViewById(R.id.Daten_Gyro);
        datenZeit = findViewById(R.id.Daten_Zeit);

        // Adjust padding of the root view so that content is not hidden under
        // the status bar / navigation bar when drawing edge-to-edge.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ==== MQTT setup using getBrokerUrl() ====

        // Create a semi-random MQTT client ID:
        //  - Prefix: "Nutzer_"
        //  - 8-character suffix from a UUID
        final String clientId = "Nutzer_" + UUID.randomUUID().toString().substring(0, 8);

        // Choose broker URL depending on whether this is a real device or an emulator.
        final String brokerUrl = getBrokerUrl();
        Log.i(TAG, "Using brokerUrl=" + brokerUrl + " clientId=" + clientId);

        // Create the MqttHandler instance.
        // If this fails, we show a toast and abort MQTT setup.
        try {
            mqttHandler = new MqttHandler(brokerUrl, clientId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MqttHandler: " + e.getMessage(), e);
            Toast.makeText(this,
                    "MQTT client creation failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        /*
         * Register a message listener that will be called whenever an MQTT
         * message arrives on any subscribed topic.
         *
         * Parameters of the lambda:
         * @param topic   the topic name of the received MQTT message
         * @param message the string payload of the MQTT message
         */
        mqttHandler.setMessageListener((topic, message) -> {
            Log.i(TAG, "messageListener -> topic=" + topic + " msg=" + message);

            // We must update UI and interact with Room on the main thread.
            runOnUiThread(() -> {
                try {
                    switch (topic) {

                        // Movement sensor: "x,y,z"
                        case "Sensor/Bewegung": {
                            // Show raw payload in the corresponding TextView.
                            datenBewegung.setText(message);

                            // Split CSV "x,y,z" into components.
                            String[] p = message.split(",");
                            if (p.length >= 3) {
                                ValueSensor s = new ValueSensor();
                                s.value1 = Float.parseFloat(p[0].trim()); // X
                                s.value2 = Float.parseFloat(p[1].trim()); // Y
                                s.value3 = Float.parseFloat(p[2].trim()); // Z
                                // Persist to DB in a background thread.
                                storeSensor(s);
                            }
                            break;
                        }

                        // Gyro sensor: "x,y,z"
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

                        // Time / timestamp: plain string
                        case "Sensor/Zeit": {
                            datenZeit.setText(message);
                            ValueSensor s = new ValueSensor();
                            s.value7 = message;
                            storeSensor(s);
                            break;
                        }

                        // Any topic we don’t explicitly handle.
                        default:
                            Log.w(TAG, "Unhandled topic: " + topic);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Error handling message: " + ex.getMessage(), ex);
                }
            });
        });

        /*
         * Initiate the asynchronous MQTT connection.
         *
         * ConnectionListener has two callbacks:
         * - onConnected(): called once the broker connection is up.
         * - onFailure(Throwable): called when the connect attempt fails.
         */
        mqttHandler.connect(new MqttHandler.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "MQTT connected callback");

                // Notify the user that the connection was established.
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "MQTT verbunden",
                                Toast.LENGTH_SHORT).show()
                );

                // Once we are connected, we can subscribe and publish.
                mqttHandler.subscribe("Sensor/Bewegung");
                mqttHandler.subscribe("Sensor/Gyro");
                mqttHandler.subscribe("Sensor/Zeit");

                // Publish some retained test messages so new subscribers will
                // immediately receive the last value.
                mqttHandler.publish("Sensor/Bewegung", "-0.788295,4.259267,0.982682", true);
                mqttHandler.publish("Sensor/Gyro", "1.265565,0.301551,0.052052", true);
                mqttHandler.publish("Sensor/Zeit", "1970-01-01T00:01:10.215742Z", true);

                // Load existing DB values and log the count.
                loadDatabaseValues();
            }

            @Override
            public void onFailure(Throwable t) {
                // Log the failure (including stack trace) and inform the user.
                Log.e(TAG, "MQTT connect failed: " +
                        (t == null ? "unknown" : t.getMessage()), t);

                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "MQTT Fehler: " +
                                        (t == null ? "unknown" : t.getMessage()),
                                Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    /**
     * Lifecycle callback: called when the Activity is about to be destroyed.
     * We override this to gracefully disconnect from the MQTT broker.
     */
    @Override
    protected void onDestroy() {
        if (mqttHandler != null) mqttHandler.disconnect();
        super.onDestroy();
    }

    // ==== DB helper methods – taken from your git version ====

    /**
     * Persists a {@link ValueSensor} entity into the Room database on a
     * background thread.
     *
     * @param sensor the sensor values to insert into the database.
     *
     * Return value: {@code void}.
     * Side effects:
     * - Inserts a row into the DB if {@link InitDb#appDatabase} is not null.
     * - Logs warnings / errors on failure.
     */
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

    /**
     * Loads all {@link ValueSensor} records from the database on a background
     * thread and logs the number of rows.
     * Return value: {@code void}.
     * Side effects:
     * - Reads the DB via {@link InitDb#appDatabase}.
     * - Writes the count of entries to Logcat.
     */
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

    // ==== Emulator detection: choose correct broker URL ====

    /**
     * Determines whether the app is running on an emulator or a physical
     * device based on various {@link android.os.Build} fields, and returns the
     * appropriate MQTT broker URL.
     *
     * Detection heuristics:
     * - {@link android.os.Build#FINGERPRINT} starts with "generic",
     *   contains "vbox", or contains "test-keys".
     * - {@link android.os.Build#MODEL} contains "google_sdk",
     *   "emulator", or "android sdk built for x86".
     * - {@link android.os.Build#PRODUCT} contains "sdk_gphone".
     *
     * @return {@link #EMULATOR_BROKER} if heuristics indicate an emulator,
     *         otherwise {@link #PHONE_BROKER}.
     */
    private String getBrokerUrl() {

        String f = (android.os.Build.FINGERPRINT == null ? "" : android.os.Build.FINGERPRINT).toLowerCase();
        String m = (android.os.Build.MODEL == null       ? "" : android.os.Build.MODEL).toLowerCase();
        String p = (android.os.Build.PRODUCT == null     ? "" : android.os.Build.PRODUCT).toLowerCase();

        boolean isEmulator =
                f.startsWith("generic") ||
                        f.contains("vbox") ||
                        f.contains("test-keys") ||
                        m.contains("google_sdk") ||
                        m.contains("emulator") ||
                        m.contains("android sdk built for x86") ||
                        p.contains("sdk_gphone");

        return isEmulator ? EMULATOR_BROKER : PHONE_BROKER;
    }
}
