package database;

import android.content.Context;

import androidx.room.Room;
import androidx.room.RoomDatabase;

import database.entities.AccelData;
import database.entities.GyroData;
import database.entities.MagnetData;

@androidx.room.Database(entities = {AccelData.class, MagnetData.class, GyroData.class}, version = 1, exportSchema = false)
public abstract class Database extends RoomDatabase {
    public abstract ValueSensorDAO valueDao();

    private static volatile Database INSTANCE;

    public static Database getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (Database.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    Database.class, "Datenbank")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
