package com.fhdw.biot.speech.iot.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.fhdw.biot.speech.iot.database.dao.SensorDao;
import com.fhdw.biot.speech.iot.database.dao.ValueSensorDAO;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.EreignisData;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.database.entities.Sensor;
import com.fhdw.biot.speech.iot.database.entities.ValueSensor;

/**
 * In-memory Room database — lives only for the duration of the process.
 * Cleared automatically when the app is killed, giving the live-session-only
 * semantics the charts require without any migration overhead.
 */
@Database(
        entities = {
            AccelData.class,
            GyroData.class,
            MagnetData.class,
            EreignisData.class,
            ValueSensor.class,
            Sensor.class
        },
        version = 1,
        exportSchema = false)
public abstract class DB extends RoomDatabase {

    public abstract SensorDao sensorDao();
    public abstract ValueSensorDAO valueSensorDao();

    private static volatile DB INSTANCE;

    public static DB getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (DB.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.inMemoryDatabaseBuilder(
                                    context.getApplicationContext(),
                                    DB.class)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
