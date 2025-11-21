package database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;


import database.entities.AccelData;
import database.entities.GyroData;
import database.entities.MagnetData;

@Dao
public interface ValueSensorDAO {

        @Insert
        void insertAccelData(AccelData accelData);

        @Insert
        void insertGyroData(GyroData gyroData);

        @Insert
        void insertMagnetData(MagnetData magnetData);

        @Query("SELECT * FROM accel_data WHERE ID = :ID")
        AccelData getAccelDataByID(String ID);

        @Query("SELECT * FROM gyro_data WHERE ID = :ID")
        GyroData getGyroDataByID(String ID);

        @Query("SELECT * FROM magnet_data WHERE ID = :ID")
        MagnetData getMagnetDataByID(String ID);


}
