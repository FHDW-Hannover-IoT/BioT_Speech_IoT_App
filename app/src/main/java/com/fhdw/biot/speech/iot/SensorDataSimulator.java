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
 * Periodically generates *fake* sensor values and publishes them via MQTT.
 * Purpose:
 *  - Let you test the app UI + DB + MQTT pipeline without real hardware.
 * Topics being published:
 *  - "Sensor/Bewegung" : CSV "x,y,z" → interpreted as accelerometer-like values.
 *  - "Sensor/Gyro"     : CSV "x,y,z" → interpreted as gyro-like values.
 *  - "Sensor/Zeit"     : plain ISO-like timestamp string.
 * Data flow:
 *  - Timer (ScheduledExecutorService) fires.
 *  - Simulator generates random payloads.
 *  - Calls {@link MqttHandler#publish(String, String, boolean)} for each topic.
 *  - Broker receives PUBLISH and forwards to any subscribers.
 *  - Our own client is subscribed, so messages arrive in
 *      MqttHandler → MainActivity → TextViews + Room DB.
 */
public class SensorDataSimulator {

    private static final String TAG = "SensorDataSimulator";

    /**
     * Reference to the app's MQTT wrapper.
     * Used only for publish calls; it must already be connected.
     */
    private final MqttHandler mqttHandler;

    /**
     * Single-threaded scheduler that repeatedly runs our simulation task
     * in the background (off the UI thread).
     */
    private final ScheduledExecutorService scheduler;

    /**
     * Handle to the currently scheduled task (so we can cancel it in stop()).
     * Will be null when the simulator is not running.
     */
    private ScheduledFuture<?> task;

    /**
     * Random number generator for fake values.
     */
    private final Random random = new Random();

    /**
     * Desired delay between the END of one simulation run and the START
     * of the next, in milliseconds.
     * Note:
     *  - We use scheduleWithFixedDelay() so that Android can't "catch up"
     *    missed executions after the process was cached.
     */
    private final long intervalMs;

    /**
     * @param mqttHandler existing, connected {@link MqttHandler} used for publish()
     * @param intervalMs  delay in milliseconds between fake messages
     *                    (measured from end-of-task to start-of-next-task)
     */
    public SensorDataSimulator(MqttHandler mqttHandler, long intervalMs) {
        this.mqttHandler = mqttHandler;
        this.intervalMs = intervalMs;

        // Create a dedicated background thread that just runs scheduled tasks.
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Starts periodic publishing of fake data.
     * Behaviour:
     *  - If already running, it simply logs and returns (idempotent).
     *  - Otherwise, schedules a repeating task with:
     *        initialDelay = 1000 ms
     *        delayBetweenRuns = intervalMs
     * IMPORTANT: We now use scheduleWithFixedDelay instead of scheduleAtFixedRate
     * to avoid Android's "burst" behaviour when the process comes back from
     * cached state (no backlog of queued executions will be fired).
     */
    public void start() {
        // Do not start again if we already have a running task.
        if (task != null && !task.isCancelled()) {
            Log.i(TAG, "SensorDataSimulator already running");
            return;
        }

        /*
         * scheduleWithFixedDelay:
         *  - First run after 1000 ms.
         *  - After each run finishes, wait intervalMs milliseconds,
         *    then start the next run.
         *
         * This means:
         *  - If a run takes longer because the process was paused or the
         *    device was busy, Android will NOT try to "catch up" all the
         *    missed executions. We just resume with one run, then delay,
         *    then the next.
         */
        task = scheduler.scheduleWithFixedDelay(() -> {
            try {
                // If MQTT is not connected yet, skip this cycle.
                if (!mqttHandler.isConnected()) {
                    Log.w(TAG, "Simulator: MQTT not connected yet, skipping publish");
                    return;
                }

                // -------- Fake Bewegung data: 3 random floats in [-2, 2] --------
                String bewegungPayload = String.format(
                        Locale.US,
                        "%.3f,%.3f,%.3f",
                        randomFloat(-2f, 2f),
                        randomFloat(-2f, 2f),
                        randomFloat(-2f, 2f)
                );

                // -------- Fake Gyro data: 3 random floats in [-5, 5] --------
                String gyroPayload = String.format(
                        Locale.US,
                        "%.3f,%.3f,%.3f",
                        randomFloat(-5f, 5f),
                        randomFloat(-5f, 5f),
                        randomFloat(-5f, 5f)
                );

                // -------- Fake time: simple ISO-like timestamp --------
                String timePayload = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        Locale.US
                ).format(new Date());

                // Log what we're about to send (visible in Logcat).
                Log.i(TAG, "Simulator publish Bewegung: " + bewegungPayload);
                Log.i(TAG, "Simulator publish Gyro    : " + gyroPayload);
                Log.i(TAG, "Simulator publish Zeit    : " + timePayload);

                /*
                 * Publish to MQTT.
                 *
                 * - These calls go to MqttHandler.publish(...), which starts
                 *   its own background threads for the actual network I/O.
                 * - retained = false → broker does NOT store the last value.
                 *   We want a pure real-time stream here.
                 */
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
     * Behaviour:
     *  - Cancels the scheduled task so no more runs will happen.
     *  - Shuts down the executor and interrupts the worker thread.
     * Note:
     *  - After calling stop(), this instance of SensorDataSimulator
     *    cannot be restarted because the executor is shut down.
     *  - If you ever want to support restarting, remove shutdownNow()
     *    and instead keep the executor alive for the lifetime of the app.
     */
    public void stop() {
        if (task != null) {
            task.cancel(true);   // Interrupt current run (if any) and prevent future runs.
            task = null;
        }
        scheduler.shutdownNow(); // Stop the scheduler's thread.
    }

    /**
     * Utility to generate a random float in [min, max].
     */
    private float randomFloat(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }
}