package com.fhdw.biot.speech.iot.events;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.fhdw.biot.speech.iot.database.entities.EreignisType;
import com.fhdw.biot.speech.iot.repository.SensorRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Evaluates incoming sensor readings against the persisted {@link EreignisType} rule set and
 * triggers {@link SensorEreignis} events when a threshold is exceeded.
 *
 * <p>Responsibilities: load rules from the DB, apply per-rule cooldown, fire notifications, and
 * persist triggered events — all without holding an Activity reference.
 *
 * <p>Constructed and owned by {@link com.fhdw.biot.speech.iot.config.AppContainer}; injected into
 * MainActivity so the Activity only calls {@link #evaluate(String, float, float, float)}.
 */
public class RuleEvaluator {

    private static final String TAG = "RuleEvaluator";
    private static final long COOLDOWN_MS = 2000;
    private static final String PREF_NAME = "AppPreferences";

    private final SensorRepository repo;
    private final Context appContext;
    private final ExecutorService executor;

    private List<EreignisType> activeRules;
    private final Map<Integer, Long> lastTriggeredMap = new HashMap<>();

    /**
     * @param repo Repository for DB writes and rule reads.
     * @param appContext Application context (not Activity) used for notifications and prefs.
     * @param executor Shared executor for off-main-thread DB operations.
     */
    public RuleEvaluator(SensorRepository repo, Context appContext, ExecutorService executor) {
        this.repo = repo;
        this.appContext = appContext.getApplicationContext();
        this.executor = executor;
    }

    /**
     * Reloads {@link EreignisType} rules from the DB on the shared executor. Call once after the DB
     * is ready (e.g. from AppContainer.initApplicationScope).
     */
    public void reloadRules() {
        executor.execute(
                () -> {
                    activeRules = repo.getAllEreignisTypes();
                    Log.i(TAG, "Rules loaded: " + (activeRules == null ? 0 : activeRules.size()));
                });
    }

    /**
     * Evaluates the given sensor reading against all active rules. Safe to call from any thread
     * (the MQTT message callback).
     *
     * @param sensorType Logical sensor identifier matching {@link EreignisType#sensorType}.
     * @param x X-axis reading.
     * @param y Y-axis reading.
     * @param z Z-axis reading.
     */
    public void evaluate(String sensorType, float x, float y, float z) {
        if (activeRules == null || activeRules.isEmpty()) return;

        long now = System.currentTimeMillis();
        float sum = (float) Math.sqrt(x * x + y * y + z * z);

        for (EreignisType rule : activeRules) {
            if (rule.sensorType == null || !rule.sensorType.equalsIgnoreCase(sensorType)) continue;

            Long lastTrigger = lastTriggeredMap.get(rule.ereignisID);
            if (lastTrigger != null && (now - lastTrigger) < COOLDOWN_MS) continue;

            boolean triggered = false;
            if (rule.axisX && meetsThreshold(x, rule.ereignisThreshold, rule.thresholdDirection)) {
                fire(now, sensorType, x, rule.ereignisName, "X");
                triggered = true;
            } else if (rule.axisY
                    && meetsThreshold(y, rule.ereignisThreshold, rule.thresholdDirection)) {
                fire(now, sensorType, y, rule.ereignisName, "Y");
                triggered = true;
            } else if (rule.axisZ
                    && meetsThreshold(z, rule.ereignisThreshold, rule.thresholdDirection)) {
                fire(now, sensorType, z, rule.ereignisName, "Z");
                triggered = true;
            } else if (rule.axisSum
                    && meetsThreshold(sum, rule.ereignisThreshold, rule.thresholdDirection)) {
                fire(now, sensorType, sum, rule.ereignisName, "Sum");
                triggered = true;
            }

            if (triggered) lastTriggeredMap.put(rule.ereignisID, now);
        }
    }

    /**
     * Returns true if {@code |value|} satisfies the threshold comparison. Defaults to {@code >=}
     * when direction is null.
     */
    private boolean meetsThreshold(float value, float threshold, String direction) {
        float abs = Math.abs(value);
        if ("<=".equals(direction)) return abs <= threshold;
        return abs >= threshold; // default: ">="
    }

    /**
     * Creates a {@link SensorEreignis} (which fires the notification) and persists the event to the
     * DB via the shared executor, avoiding Activity context or unbounded threads.
     */
    private void fire(
            long timestamp, String sensorType, float value, String eventName, String axis) {
        Log.w(TAG, "Event triggered: " + eventName + " on sensor " + sensorType);

        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isPushActive = prefs.getBoolean("PUSH_ACTIVE", true);

        SensorEreignis ereignis =
                new SensorEreignis(
                        timestamp, sensorType, value, eventName, appContext, axis, isPushActive);

        executor.execute(() -> repo.insertEreignis(ereignis.getEreignisData()));
    }
}
