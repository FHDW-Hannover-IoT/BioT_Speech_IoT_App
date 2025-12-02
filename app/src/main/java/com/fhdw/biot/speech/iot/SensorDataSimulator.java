package com.fhdw.biot.speech.iot;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SensorDataSimulator
 * -------------------
 * Periodically publishes fake sensor data to MQTT topics so that the app
 * can be tested with a continuous datastream.
 * Topics:
 *  - Sensor/Bewegung : "x,y,z" accelerometer-like values
 *  - Sensor/Gyro     : "x,y,z" gyro-like values
 *  - Sensor/Zeit     : ISO-like timestamp string
 */
public class SensorDataSimulator {

    private static final String TAG = "SensorDataSimulator";

    private final MqttHandler mqttHandler;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private final Random random = new Random();

    // Publish every N milliseconds
    private final long intervalMs;

    /**
     * @param mqttHandler existing, connected MqttHandler
     * @param intervalMs  interval in milliseconds between fake messages
     */
    public SensorDataSimulator(MqttHandler mqttHandler, long intervalMs) {
        this.mqttHandler = mqttHandler;
        this.intervalMs = intervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Starts periodic publishing of fake data.
     * Safe to call multiple times (won't start another task if already running).
     */
    public void start() {
        if (task != null && !task.isCancelled()) {
            Log.i(TAG, "SensorDataSimulator already running");
            return;
        }

        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!mqttHandler.isConnected()) {
                    Log.w(TAG, "Simulator: MQTT not connected yet, skipping publish");
                    return;
                }

                // Fake Bewegung data: 3 floats
                String bewegungPayload = String.format(Locale.US,
                        "%.3f,%.3f,%.3f",
                        randomFloat(-2f, 2f),
                        randomFloat(-2f, 2f),
                        randomFloat(-2f, 2f));

                // Fake Gyro data: 3 floats
                String gyroPayload = String.format(Locale.US,
                        "%.3f,%.3f,%.3f",
                        randomFloat(-5f, 5f),
                        randomFloat(-5f, 5f),
                        randomFloat(-5f, 5f));

                // Fake time: a simple ISO-like timestamp
                String timePayload = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        Locale.US
                ).format(new Date());

                Log.i(TAG, "Simulator publish Bewegung: " + bewegungPayload);
                Log.i(TAG, "Simulator publish Gyro    : " + gyroPayload);
                Log.i(TAG, "Simulator publish Zeit    : " + timePayload);

                // No need for retained here; we want a real-time stream.
                mqttHandler.publish("Sensor/Bewegung", bewegungPayload, false);
                mqttHandler.publish("Sensor/Gyro", gyroPayload, false);
                mqttHandler.publish("Sensor/Zeit", timePayload, false);

            } catch (Exception e) {
                Log.e(TAG, "Simulator error: " + e.getMessage(), e);
            }
        }, 1000, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops publishing fake data and shuts down the scheduler.
     */
    public void stop() {
        if (task != null) {
            task.cancel(true);
            task = null;
        }
        scheduler.shutdownNow();
    }

    private float randomFloat(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }
}
