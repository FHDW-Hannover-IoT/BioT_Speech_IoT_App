package database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import database.dao.SensorDao;
import database.dao.ValueSensorDAO;
import database.entities.AccelData;
import database.entities.GyroData;
import database.entities.MagnetData;
import database.entities.EreignisData;
import database.entities.ValueSensor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                AccelData.class,
                GyroData.class,
                MagnetData.class,
                EreignisData.class,
                ValueSensor.class
        },
        version = 2,           // keep the version that matches your existing DB_Impl
        exportSchema = false
)
public abstract class DB extends RoomDatabase {

    // --- DAOs that Room must generate ---
    public abstract SensorDao sensorDao();

    // THIS is the method MainActivity is calling
    public abstract ValueSensorDAO valueSensorDao();

    // --- Singleton + Executor like before ---
    private static volatile DB INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;

    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static DB getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (DB.class) {
                if (INSTANCE == null) {
                    INSTANCE =
                            Room.databaseBuilder(
                                            context.getApplicationContext(),
                                            DB.class,
                                            "sensor_database"
                                    )
                                    .fallbackToDestructiveMigration()
                                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
