package database;

import android.app.Application;

public class InitDb extends Application {
    public static Database appDatabase;

    @Override
    public void onCreate() {
        super.onCreate();
        appDatabase = Database.getDatabase(this);
    }
}
