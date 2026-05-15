package com.fhdw.biot.speech.iot.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.fhdw.biot.speech.iot.database.dao.SensorDao;
import com.fhdw.biot.speech.iot.database.dao.ValueSensorDAO;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.EreignisData;
import com.fhdw.biot.speech.iot.database.entities.EreignisType;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.database.entities.Sensor;
import com.fhdw.biot.speech.iot.database.entities.ValueSensor;

/**
 * Persistent Room database. Two-tier data model:
 * - {@code EreignisType} — event rule definitions; survive across sessions (user-configured).
 * - Sensor tables ({@code accel_data}, {@code gyro_data}, {@code magnet_data},
 *   {@code ereignis_data}) — session-only; cleared on every app start so live-session
 *   semantics are preserved while rules persist.
 */
@Database(
        entities = {
            AccelData.class,
            GyroData.class,
            MagnetData.class,
            EreignisData.class,
            ValueSensor.class,
            Sensor.class,
            EreignisType.class
        },
        version = 5,
        exportSchema = false)
public abstract class DB extends RoomDatabase {

    public abstract SensorDao sensorDao();

    public abstract ValueSensorDAO valueSensorDao();

    private static volatile DB INSTANCE;
    private static final String DB_NAME = "sensor_database";

    public static DB getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (DB.class) {
                if (INSTANCE == null) {
                    INSTANCE =
                            Room.databaseBuilder(context.getApplicationContext(), DB.class, DB_NAME)
                                    .fallbackToDestructiveMigration()
                                    .addCallback(
                                            new RoomDatabase.Callback() {
                                                @Override
                                                public void onCreate(
                                                        @NonNull SupportSQLiteDatabase db) {
                                                    super.onCreate(db);
                                                    EreignisTypeSeeder.seed(db);
                                                }

                                                @Override
                                                public void onOpen(
                                                        @NonNull SupportSQLiteDatabase db) {
                                                    super.onOpen(db);
                                                    clearSessionData(db);
                                                }
                                            })
                                    .build();
                }
            }
        }
        return INSTANCE;
    }

    /** Deletes all session-scoped sensor rows so each app start begins with a clean slate. */
    private static void clearSessionData(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("DELETE FROM accel_data");
        db.execSQL("DELETE FROM gyro_data");
        db.execSQL("DELETE FROM magnet_data");
        db.execSQL("DELETE FROM ereignis_data");
    }
}
