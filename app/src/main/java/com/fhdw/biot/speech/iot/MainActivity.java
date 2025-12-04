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
 * Entry point / main screen of the app.
 *
 * High-level data flow:
 * 1. Activity starts.
 * 2. UI (3 TextViews) is created.
 * 3. We create a single MQTT client (MqttHandler) and connect to the broker.
 * 4. Once connected, we subscribe to three topics:
 *      - "Sensor/Bewegung"
 *      - "Sensor/Gyro"
 *      - "Sensor/Zeit"
 * 5. A SensorDataSimulator publishes fake values *to those topics* every second.
 * 6. Incoming MQTT messages are forwarded by MqttHandler to a callback in this
 *    Activity. Here we:
 *      - Update the corresponding TextView.
 *      - Convert the raw strings into a ValueSensor object.
 *      - Store that object into the Room database via InitDb.appDatabase.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * MQTT broker URL used when the app is running on a physical phone.
     *
     * Format: tcp://<PC_LAN_IP>:1883
     *  - Host: the IPv4 address of the PC where Mosquitto (or another broker) is running.
     *  - Port: 1883 (default MQTT port, non-TLS).
     */
    private static final String PHONE_BROKER = "tcp://172.17.160.183:1883";

    /**
     * MQTT broker URL used when the app is running on an Android emulator.
     *
     * The special IP 10.0.2.2 points from the emulator to the host machine.
     */
    private static final String EMULATOR_BROKER = "tcp://10.0.2.2:1883";

    /**
     * Tag used for Android Logcat logging.
     */
    private static final String TAG = "MainActivity";

    /**
     * Wrapper around the Eclipse Paho MQTT client.
     * This object actually talks to the MQTT broker.
     * It exposes:
     *  - connect(...)
     *  - subscribe(topic)
     *  - publish(topic, payload[, retained])
     *  - setMessageListener(...)
     *  - disconnect()
     */
    private MqttHandler mqttHandler;

    /**
     * Helper that periodically publishes fake sensor data to MQTT topics.
     * It uses the same mqttHandler instance to publish messages.
     */
    private SensorDataSimulator dataSimulator;

    /**
     * TextView that displays movement sensor data (topic "Sensor/Bewegung").
     * Example content: "0.123,-1.234,0.456"
     */
    private TextView datenBewegung;

    /**
     * TextView that displays time data (topic "Sensor/Zeit").
     * Example content: "2025-12-04T20:20:37.946Z"
     */
    private TextView datenZeit;

    /**
     * TextView that displays gyro sensor data (topic "Sensor/Gyro").
     * Example content: "1.004,4.314,3.038"
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

        // Inflate the layout XML (activity_main.xml) and attach it to this Activity.
        setContentView(R.layout.activity_main);

        // Find our TextViews in the layout.
        // From now on, we update these when new MQTT messages arrive.
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

        // Generate a semi-random MQTT client ID:
        //   - Prefix: "Nutzer_"
        //   - 8-character suffix from a UUID
        // This needs to be unique per client, otherwise the broker will drop
        // the old connection when a new one with the same ID appears.
        final String clientId = "Nutzer_" + UUID.randomUUID().toString().substring(0, 8);

        // Decide which broker URL to use (emulator vs real device).
        final String brokerUrl = getBrokerUrl();
        Log.i(TAG, "Using brokerUrl=" + brokerUrl + " clientId=" + clientId);

        // Create the MqttHandler instance that internally creates an MqttAsyncClient.
        // If this fails, the app cannot talk MQTT at all, so we show a toast and stop.
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
         * Data flow:
         *  - Broker pushes a message to this client.
         *  - MqttHandler's internal MqttCallback.messageArrived(...) is invoked.
         *  - MqttHandler forwards the (topic, payload) to this listener.
         *  - Here, we decide which handler to call (movement, gyro, time).
         *  - Handler updates TextView + creates a ValueSensor object.
         *  - ValueSensor is saved to the Room database via storeSensor().
         */
        mqttHandler.setMessageListener((topic, message) -> {
            Log.i(TAG, "messageListener -> topic=" + topic + " msg=" + message);

            // We must update UI and interact with Room on the main thread.
            // runOnUiThread posts this work to the main Looper.
            runOnUiThread(() -> {
                try {
                    switch (topic) {

                        // Movement sensor: "x,y,z"
                        case "Sensor/Bewegung": {
                            handleMovementMessage(message);
                            break;
                        }

                        // Gyro sensor: "x,y,z"
                        case "Sensor/Gyro": {
                            handleGyroMessage(message);
                            break;
                        }

                        // Time / timestamp: plain string
                        case "Sensor/Zeit": {
                            handleTimeMessage(message);
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
         * ConnectionListener is our own small interface with two callbacks:
         * - onConnected(): called once the broker connection is fully up.
         * - onFailure(Throwable): called when the connect attempt fails.
         *
         * Inside MqttHandler.connect(), the actual network work is done on a
         * background thread and blocks until the MQTT handshake is finished.
         */
        mqttHandler.connect(new MqttHandler.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "MQTT connected callback");

                // Inform the user in the UI that MQTT is ready.
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "MQTT verbunden",
                                Toast.LENGTH_SHORT).show()
                );

                // Once we are connected, we can subscribe to topics.
                // From this moment on, messages published on these topics by
                // *any* client (including our SensorDataSimulator) will be
                // forwarded to our MqttMessageListener.
                mqttHandler.subscribe("Sensor/Bewegung");
                mqttHandler.subscribe("Sensor/Gyro");
                mqttHandler.subscribe("Sensor/Zeit");

                // Load existing DB values and log the count.
                // This does NOT affect MQTT, it only shows how many
                // ValueSensor rows are currently saved.
                loadDatabaseValues();

                // Start the simulator which publishes fake data every second.
                // Data path:
                //   SensorDataSimulator -> mqttHandler.publish(...) ->
                //   MQTT Broker -> mqttHandler (subscription) ->
                //   messageListener in this Activity ->
                //   handleMovement/Gyro/Time -> TextViews + DB.
                dataSimulator = new SensorDataSimulator(mqttHandler, 1000L); // 1 second interval
                dataSimulator.start();
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
     * We override this to:
     *  - stop the SensorDataSimulator (stop publishing fake data).
     *  - disconnect cleanly from the MQTT broker.
     */
    @Override
    protected void onDestroy() {
        if (dataSimulator != null) dataSimulator.stop();
        if (mqttHandler != null) mqttHandler.disconnect();
        super.onDestroy();
    }

    // ============================================================
    //  Datastream handlers
    //  Each of these is called for EVERY incoming message on their topic.
    //  They are responsible for:
    //  - updating the UI
    //  - mapping raw data into ValueSensor
    //  - calling storeSensor(...) to persist in Room
    // ============================================================

    private void handleMovementMessage(String message) {
        // 1) Update the TextView so the user sees the latest movement values.
        datenBewegung.setText(message);

        // 2) Parse CSV: "x,y,z"
        String[] p = message.split(",");
        if (p.length >= 3) {
            try {
                // Create a ValueSensor entity and fill the first 3 float fields.
                ValueSensor s = new ValueSensor();
                s.value1 = Float.parseFloat(p[0].trim());
                s.value2 = Float.parseFloat(p[1].trim());
                s.value3 = Float.parseFloat(p[2].trim());

                // 3) Persist this entity to the database on a background thread.
                storeSensor(s);
            } catch (NumberFormatException e) {
                Log.e(TAG, "handleMovementMessage parse error: " + e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "handleMovementMessage: not enough values: " + message);
        }
    }

    private void handleGyroMessage(String message) {
        // 1) Update gyro TextView with the newest "x,y,z" data.
        datenGyro.setText(message);

        // 2) Parse CSV: "x,y,z" into three floats.
        String[] g = message.split(",");
        if (g.length >= 3) {
            try {
                ValueSensor s = new ValueSensor();
                s.value4 = Float.parseFloat(g[0].trim());
                s.value5 = Float.parseFloat(g[1].trim());
                s.value6 = Float.parseFloat(g[2].trim());

                // 3) Store in DB.
                storeSensor(s);

                // Optional toast just to visually confirm that data arrived.
                Toast.makeText(MainActivity.this,
                        "Txt erhalten",
                        Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Log.e(TAG, "handleGyroMessage parse error: " + e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "handleGyroMessage: not enough values: " + message);
        }
    }

    private void handleTimeMessage(String message) {
        // 1) Show the timestamp string in the TextView.
        datenZeit.setText(message);

        // 2) Create a ValueSensor that only carries the time field (value7).
        ValueSensor s = new ValueSensor();
        s.value7 = message;

        // 3) Persist to DB.
        storeSensor(s);
    }


    /**
     * Persists a {@link ValueSensor} entity into the Room database on a
     * background thread.
     *
     * @param sensor the sensor values to insert into the database.
     *
     * Data path:
     *  - handleXxxMessage(...) creates a ValueSensor and calls storeSensor().
     *  - storeSensor(...) runs insert(...) on InitDb.appDatabase.valueDao()
     *    in a new Thread.
     *  - Room writes the row into the SQLite database file.
     */
    private void storeSensor(ValueSensor sensor) {
        new Thread(() -> {
            try {
                if (InitDb.appDatabase != null) {
                    // valueDao() is your Room DAO.
                    // insert(sensor) writes a single row into the table.
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
     *
     * This is mainly for debugging/monitoring at startup:
     *  - It does NOT change the UI.
     *  - It helps you see how many samples have been stored so far.
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
     * Detect whether the app is running on an emulator or a physical device
     * using some common Build.* heuristics and return the appropriate MQTT
     * broker URL.
     *
     * If it's an emulator, we use {@link #EMULATOR_BROKER} which points back
     * to the host PC (where Mosquitto is expected to run).
     * If it's a real device on the same LAN, we use {@link #PHONE_BROKER}
     * with the PC's LAN IP.
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
