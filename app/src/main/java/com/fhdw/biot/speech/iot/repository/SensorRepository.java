package com.fhdw.biot.speech.iot.repository;

import androidx.lifecycle.LiveData;
import com.fhdw.biot.speech.iot.database.DbContext;
import com.fhdw.biot.speech.iot.database.dao.SensorDao;
import com.fhdw.biot.speech.iot.database.dao.ValueSensorDAO;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.EreignisData;
import com.fhdw.biot.speech.iot.database.entities.EreignisType;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.database.entities.Sensor;
import com.fhdw.biot.speech.iot.database.entities.ValueSensor;
import java.util.List;

/**
 * SensorRepository — single access point for all sensor data in the Android Room database.
 *
 * <p><b>Write path:</b> all writes go through {@link DbContext} which owns the single-thread
 * executor and transaction boundary. Callers never write directly to the DAO.
 *
 * <p><b>Read path:</b> returns Room-managed {@link LiveData} so graph activities auto-update
 * when new rows are inserted — no polling required. The data returned is already
 * pre-aggregated by the server (raw / 1min / 1hour depending on the requested window),
 * so every read is a fast indexed range scan with no GROUP BY on the device.
 *
 * <p>No Activity or Fragment reference is held here. LiveData lifecycle (observe/remove)
 * is managed by the observer at the call site.
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

    public void insertAccel(AccelData data) {
        ctx.insertAccel(data);
    }

    public void insertGyro(GyroData data) {
        ctx.insertGyro(data);
    }

    public void insertMagnet(MagnetData data) {
        ctx.insertMagnet(data);
    }

    public void insertEreignis(EreignisData data) {
        ctx.insertEreignis(data);
    }

    public void insertValueSensor(ValueSensor vs) {
        ctx.insertValueSensor(vs);
    }

    public void insertAccelBatch(List<AccelData> batch) {
        ctx.insertAccelBatch(batch);
    }

    public void insertGyroBatch(List<GyroData> batch) {
        ctx.insertGyroBatch(batch);
    }

    public void insertMagnetBatch(List<MagnetData> batch) {
        ctx.insertMagnetBatch(batch);
    }

    public void insertEreignisType(EreignisType type) {
        dao.insertEreignisType(type);
    }

    public void deleteAllEreignisTypes() {
        dao.deleteAllEreignisTypes();
    }

    // ── Live reads ────────────────────────────────────────────────────────────

    /** Used by MainGraphActivity to anchor the initial "from" date to the oldest available row. */
    public LiveData<Long> getOldestAccelTimestamp() {
        return dao.getOldestAccelTimestamp();
    }

    public LiveData<Long> getOldestGyroTimestamp() {
        return dao.getOldestGyroTimestamp();
    }

    public LiveData<Long> getOldestMagnetTimestamp() {
        return dao.getOldestMagnetTimestamp();
    }

    /**
     * Returns a reactive range query that fires whenever {@code accel_data} is modified.
     * The server pre-aggregates data to the correct resolution before sending it, so this
     * query always returns ≤600 rows regardless of the time window selected by the user.
     */
    public LiveData<List<AccelData>> getAccelBetween(long from, long to) {
        return dao.getAccelDataBetween(from, to);
    }

    public LiveData<List<GyroData>> getGyroBetween(long from, long to) {
        return dao.getGyroDataBetween(from, to);
    }

    public LiveData<List<MagnetData>> getMagnetBetween(long from, long to) {
        return dao.getMagnetDataBetween(from, to);
    }

    public List<EreignisData> getAllEreignisData() {
        return dao.getAllEreignisData();
    }

    public List<Sensor> getAllKnownSensors() {
        return dao.getAllKnownSensors();
    }

    public List<ValueSensor> getAllValueSensors() {
        return valueSensorDao.getAllvalue();
    }

    public List<EreignisType> getAllEreignisTypes() {
        return dao.getAllEreignisTypes();
    }
}
