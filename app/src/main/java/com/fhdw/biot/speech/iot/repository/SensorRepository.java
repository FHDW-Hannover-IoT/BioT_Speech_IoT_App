package com.fhdw.biot.speech.iot.repository;

import androidx.lifecycle.LiveData;
import com.fhdw.biot.speech.iot.database.DbContext;
import com.fhdw.biot.speech.iot.database.dao.SensorDao;
import com.fhdw.biot.speech.iot.database.dao.ValueSensorDAO;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.EreignisData;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.database.entities.Sensor;
import com.fhdw.biot.speech.iot.database.entities.ValueSensor;
import java.util.List;

/**
 * SensorRepository — single access point for sensor data.
 *
 * Writes always go through {@link DbContext} (ACID, background thread).
 * Reads return Room-managed {@link LiveData} so the UI auto-updates
 * without polling.
 *
 * No Activity or Fragment reference is held here — the LiveData lifecycle
 * is managed by the observer (Activity/Fragment) at call site.
 */
public class SensorRepository {

    private final DbContext ctx;
    private final SensorDao dao;
    private final ValueSensorDAO valueSensorDao;

    public SensorRepository(DbContext ctx) {
        this.ctx = ctx;
        this.dao = ctx.sensorDao();
        this.valueSensorDao = ctx.valueSensorDao();
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    public void insertAccel(AccelData data)      { ctx.insertAccel(data); }
    public void insertGyro(GyroData data)         { ctx.insertGyro(data); }
    public void insertMagnet(MagnetData data)     { ctx.insertMagnet(data); }
    public void insertEreignis(EreignisData data) { ctx.insertEreignis(data); }
    public void insertValueSensor(ValueSensor vs) { ctx.insertValueSensor(vs); }

    public void insertAccelBatch(List<AccelData> batch)   { ctx.insertAccelBatch(batch); }
    public void insertGyroBatch(List<GyroData> batch)     { ctx.insertGyroBatch(batch); }
    public void insertMagnetBatch(List<MagnetData> batch) { ctx.insertMagnetBatch(batch); }

    // ── Live reads ────────────────────────────────────────────────────────────

    public LiveData<List<AccelData>>  getAllAccelData()  { return dao.getAllAccelData(); }
    public LiveData<List<GyroData>>   getAllGyroData()   { return dao.getAllGyroData(); }
    public LiveData<List<MagnetData>> getAllMagnetData() { return dao.getAllMagnetData(); }

    public LiveData<Long> getOldestAccelTimestamp()  { return dao.getOldestAccelTimestamp(); }
    public LiveData<Long> getOldestGyroTimestamp()   { return dao.getOldestGyroTimestamp(); }
    public LiveData<Long> getOldestMagnetTimestamp() { return dao.getOldestMagnetTimestamp(); }

    public LiveData<List<AccelData>>  getAccelBetween(long from, long to)  { return dao.getAccelDataBetween(from, to); }
    public LiveData<List<GyroData>>   getGyroBetween(long from, long to)   { return dao.getGyroDataBetween(from, to); }
    public LiveData<List<MagnetData>> getMagnetBetween(long from, long to) { return dao.getMagnetDataBetween(from, to); }

    public List<EreignisData> getAllEreignisData()  { return dao.getAllEreignisData(); }
    public List<Sensor>       getAllKnownSensors()  { return dao.getAllKnownSensors(); }
    public List<ValueSensor>  getAllValueSensors()  { return valueSensorDao.getAllvalue(); }
}
