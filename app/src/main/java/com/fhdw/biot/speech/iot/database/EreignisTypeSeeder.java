package com.fhdw.biot.speech.iot.database;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Seeds the {@code ereignisType} table with a set of default event rules on first DB creation.
 * Called once from {@link DB}'s {@code onCreate} callback. Each rule maps a sensor type, axis
 * selection, and threshold to a named event.
 */
class EreignisTypeSeeder {

    private EreignisTypeSeeder() {}

    /** Inserts the default rule set inside a single transaction. */
    static void seed(@NonNull SupportSQLiteDatabase db) {
        db.beginTransaction();
        try {
            // ── Accelerometer rules ──────────────────────────────────────────
            // 20 m/s²  ≈ 2g — sharp impact detectable without normal gravity noise
            insert(db, "Shock", "ACCEL", true, true, true, false, 20.0f, ">=");
            // Sum ≤ 1.5 m/s² — vector magnitude near zero means free-fall / zero-g
            insert(db, "Zero-G Peak", "ACCEL", false, false, false, true, 1.5f, "<=");
            // 40 m/s² ≈ 4g — severe mechanical shock
            insert(db, "Severe Impact", "ACCEL", true, true, true, false, 40.0f, ">=");
            // 12 m/s² — sustained high-frequency vibration
            insert(db, "Vibration Spike", "ACCEL", true, true, true, false, 12.0f, ">=");
            // 6.5 m/s² on X/Y — significant tilt from horizontal (≈ 42°)
            insert(db, "Tilt Limit", "ACCEL", true, true, false, false, 6.5f, ">=");

            // ── Gyroscope rules ──────────────────────────────────────────────
            // 4.5 rad/s — fast rotation (roughly 260°/s), indicating a sharp turn/throw
            insert(db, "Rotation Peak", "GYRO", true, true, true, false, 4.5f, ">=");
            // 0.5 rad/s — minimal motion threshold; anything above means the device is moving
            insert(db, "Motion Start", "GYRO", true, true, true, false, 0.5f, ">=");

            // ── Magnetometer rules ───────────────────────────────────────────
            // 150 µT — close proximity to a magnet (door/window reed contact)
            insert(db, "Magnetic Contact", "MAGNET", true, true, true, false, 150.0f, ">=");
            // 15 µT — unusually weak field; sensor may be shielded or malfunctioning
            insert(db, "Field Minimum", "MAGNET", true, true, true, false, 15.0f, "<=");
            // Sum ≥ 250 µT — strong anomaly, e.g. large motor or transformer nearby
            insert(db, "Field Anomaly", "MAGNET", false, false, false, true, 250.0f, ">=");

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void insert(
            SupportSQLiteDatabase db,
            String name,
            String sensorType,
            boolean axisX,
            boolean axisY,
            boolean axisZ,
            boolean axisSum,
            float threshold,
            String direction) {
        ContentValues v = new ContentValues();
        v.put("ereignisName", name);
        v.put("sensorType", sensorType);
        v.put("axisX", axisX ? 1 : 0);
        v.put("axisY", axisY ? 1 : 0);
        v.put("axisZ", axisZ ? 1 : 0);
        v.put("axisSum", axisSum ? 1 : 0);
        v.put("ereignisThreshold", threshold);
        v.put("thresholdDirection", direction);
        db.insert("ereignisType", SQLiteDatabase.CONFLICT_ABORT, v);
    }
}
