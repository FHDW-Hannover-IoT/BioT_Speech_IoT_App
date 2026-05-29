package com.fhdw.biot.speech.iot.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SupportSQLiteQuery;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.EreignisData;
import com.fhdw.biot.speech.iot.database.entities.EreignisType;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.database.entities.Sensor;
import java.util.List;

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

    @Insert
    void insert(Sensor sensor);

    @Query("SELECT * FROM accel_data ORDER BY timestamp ASC")
    LiveData<List<AccelData>> getAllAccelData();

    @Query("SELECT * FROM gyro_data ORDER BY timestamp ASC")
    LiveData<List<GyroData>> getAllGyroData();

    @Query("SELECT * FROM magnet_data ORDER BY timestamp ASC")
    LiveData<List<MagnetData>> getAllMagnetData();

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

    // Bucketed downsampling — groups rows into time buckets and returns AVG per bucket.
    // Aliased to match entity field names so Room maps results without a separate projection class.
    // bucketMs passed twice: once for division, once for multiplication to get bucket start time.
    @RawQuery(observedEntities = AccelData.class)
    LiveData<List<AccelData>> getAccelBucketed(SupportSQLiteQuery query);

    @RawQuery(observedEntities = GyroData.class)
    LiveData<List<GyroData>> getGyroBucketed(SupportSQLiteQuery query);

    @RawQuery(observedEntities = MagnetData.class)
    LiveData<List<MagnetData>> getMagnetBucketed(SupportSQLiteQuery query);

    @Query("SELECT * FROM ereignis_data ORDER BY timestamp ASC")
    List<EreignisData> getAllEreignisData();

    @Query("SELECT * FROM knownSensors ORDER BY sensorID ASC")
    List<Sensor> getAllKnownSensors();

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insertEreignisType(EreignisType ereignisType);

    @Query("SELECT * FROM ereignisType")
    List<EreignisType> getAllEreignisTypes();

    @Query("DELETE FROM ereignisType")
    void deleteAllEreignisTypes();
}
