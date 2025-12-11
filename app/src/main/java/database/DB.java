package database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import database.dao.SensorDao;
import database.dao.ValueSensorDAO;
import database.entities.AccelData;
import database.entities.EreignisData;
import database.entities.GyroData;
import database.entities.MagnetData;
import database.entities.Sensor;
import database.entities.ValueSensor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
            AccelData.class,
            GyroData.class,
            MagnetData.class,
            EreignisData.class,
            ValueSensor.class,
            Sensor.class
        },
        version = 3, // keep the version that matches your existing DB_Impl
        exportSchema = false)
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
                                            "sensor_database")
                                    .fallbackToDestructiveMigration()
                                    .addCallback(
                                            new RoomDatabase.Callback() {
                                                @Override
                                                public void onCreate(
                                                        @NonNull SupportSQLiteDatabase db) {
                                                    super.onCreate(db);

                                                    ContentValues values = new ContentValues();
                                                    Sensor sensor =
                                                            new Sensor().setSensorName("magX");
                                                    values.put("sensorName", sensor.sensorName);
                                                    values.put("sensorID", sensor.sensorID);
                                                    Log.d("DB", "beginning Transaction");
                                                    db.beginTransaction();
                                                    db.insert(
                                                            "knownSensors",
                                                            SQLiteDatabase.CONFLICT_ABORT,
                                                            values);
                                                    db.setTransactionSuccessful();
                                                    Log.d("DB", "successfully wrote data");
                                                    db.endTransaction();
                                                    Log.d("DB", "ended Transaction");
                                                }
                                            })
                                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
