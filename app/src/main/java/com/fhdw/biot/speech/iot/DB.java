package com.fhdw.biot.speech.iot;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {AccelData.class, GyroData.class, MagnetData.class, EreignisData.class},
        version = 2,
        exportSchema = false)
public abstract class DB extends RoomDatabase {

    public abstract SensorDao sensorDao();

    private static volatile DB INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static DB getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (DB.class) {
                if (INSTANCE == null) {
                    INSTANCE =
                            Room.databaseBuilder(
                                            context.getApplicationContext(),
                                            DB.class,
                                            "sensor_database")
                                    .fallbackToDestructiveMigration()
                                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
