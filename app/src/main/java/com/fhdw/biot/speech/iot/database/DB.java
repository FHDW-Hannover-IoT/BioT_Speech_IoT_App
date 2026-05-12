package com.fhdw.biot.speech.iot.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
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

/** Room database stored on disk with a one-time seed of default event rules. */
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
                                                    seedDefaultEreignisTypes(db);
                                                }
                                            })
                                    .build();
                }
            }
        }
        return INSTANCE;
    }

    private static void seedDefaultEreignisTypes(@NonNull SupportSQLiteDatabase db) {
        db.beginTransaction();
        try {
            insertEreignisType(db, "Shock", "ACCEL", true, true, true, false, 20.0f, ">=");
            insertEreignisType(db, "Zero-G Peak", "ACCEL", false, false, false, true, 1.5f, "<=");
            insertEreignisType(db, "Severe Impact", "ACCEL", true, true, true, false, 40.0f, ">=");
            insertEreignisType(
                    db, "Vibration Spike", "ACCEL", true, true, true, false, 12.0f, ">=");
            insertEreignisType(db, "Tilt Limit", "ACCEL", true, true, false, false, 6.5f, ">=");

            insertEreignisType(db, "Rotation Peak", "GYRO", true, true, true, false, 4.5f, ">=");
            insertEreignisType(db, "Motion Start", "GYRO", true, true, true, false, 0.5f, ">=");

            insertEreignisType(
                    db, "Magnetic Contact", "MAGNET", true, true, true, false, 150.0f, ">=");
            insertEreignisType(db, "Field Minimum", "MAGNET", true, true, true, false, 15.0f, "<=");
            insertEreignisType(
                    db, "Field Anomaly", "MAGNET", false, false, false, true, 250.0f, ">=");

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void insertEreignisType(
            SupportSQLiteDatabase db,
            String name,
            String sensorType,
            boolean axisX,
            boolean axisY,
            boolean axisZ,
            boolean axisSum,
            float threshold,
            String direction) {
        ContentValues values = new ContentValues();
        values.put("ereignisName", name);
        values.put("sensorType", sensorType);
        values.put("axisX", axisX ? 1 : 0);
        values.put("axisY", axisY ? 1 : 0);
        values.put("axisZ", axisZ ? 1 : 0);
        values.put("axisSum", axisSum ? 1 : 0);
        values.put("ereignisThreshold", threshold);
        values.put("thresholdDirection", direction);
        db.insert("ereignisType", SQLiteDatabase.CONFLICT_ABORT, values);
    }
}
