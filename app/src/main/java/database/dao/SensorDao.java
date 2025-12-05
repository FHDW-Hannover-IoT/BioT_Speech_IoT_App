package database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;
import database.entities.AccelData;
import database.entities.MagnetData;
import database.entities.GyroData;
import database.entities.EreignisData;

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
    void insert(EreignisData data);

    @Query("SELECT * FROM accel_data Order By timestamp ASC")
    LiveData<List<AccelData>> getAllAccelData();

    @Query("SELECT * FROM gyro_data ORDER BY timestamp ASC")
    LiveData<List<GyroData>> getAllGyroData();

    @Query("SELECT * FROM magnet_data ORDER BY timestamp ASC")
    LiveData<List<MagnetData>> getAllMagnetData();

    // Abfragen für die Datumsfilterung
    @Query("SELECT MIN(timestamp) FROM accel_data")
    LiveData<Long> getOldestAccelTimestamp();

    @Query("SELECT MIN(timestamp) FROM gyro_data")
    LiveData<Long> getOldestGyroTimestamp();

    @Query("SELECT MIN(timestamp) FROM magnet_data")
    LiveData<Long> getOldestMagnetTimestamp();

    @Query(
            "SELECT * FROM accel_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    LiveData<List<AccelData>> getAccelDataBetween(long startTime, long endTime);

    @Query(
            "SELECT * FROM gyro_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    LiveData<List<GyroData>> getGyroDataBetween(long startTime, long endTime);

    @Query(
            "SELECT * FROM magnet_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    LiveData<List<MagnetData>> getMagnetDataBetween(long startTime, long endTime);

    @Query("SELECT * FROM ereignis_data ORDER BY timestamp ASC")
    List<EreignisData> getAllEreignisData();
}
