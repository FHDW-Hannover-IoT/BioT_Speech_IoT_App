package database;

import android.app.Application;

public class InitDb extends Application {
    public static Database appDatabase;

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("INITDB DEBUG: InitDb.onCreate CALLED");
        appDatabase = Database.getDatabase(this);
    }
}
