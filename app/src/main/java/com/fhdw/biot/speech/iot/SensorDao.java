package com.fhdw.biot.speech.iot;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

// DAta Access Object
@Dao
public interface SensorDao {
    @Insert
    void insert(AccelData data);

    @Insert
    void insert(GyroData data);

    @Insert
    void insert(MagnetData data);

    @Insert
    void insert(EreignisData date);

    @Query("SELECT * FROM accel_data Order By timestamp ASC")
    List<AccelData> getAllAccelData();

    @Query("SELECT * FROM gyro_data ORDER BY timestamp ASC")
    List<GyroData> getAllGyroData();

    @Query("SELECT * FROM magnet_data ORDER BY timestamp ASC")
    List<MagnetData> getAllMagnetData();

    @Query("SELECT * FROM ereignis_data ORDER BY timestamp ASC")
    List<EreignisData> getAllEreignisData();
}
