package com.fhdw.biot.speech.iot.database;

import com.fhdw.biot.speech.iot.database.dao.SensorDao;
import com.fhdw.biot.speech.iot.database.dao.ValueSensorDAO;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.EreignisData;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.database.entities.ValueSensor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DbContext — ACID-compliant write gateway over the in-memory Room database.
 *
 * Every write goes through {@link DB#runInTransaction(Runnable)}, which wraps the
 * operation in a SQLite transaction and rolls back automatically on any exception.
 * This matches the EF Core DbContext pattern: callers never touch the DAO directly
 * for writes; they call DbContext which owns the executor and the transaction boundary.
 *
 * Reads (LiveData) are exposed via the DAO accessors so Room can manage the
 * observer lifecycle automatically.
 */
public class DbContext {

    private final DB db;
    private final SensorDao sensorDao;
    private final ValueSensorDAO valueSensorDao;
    final ExecutorService executor;

    public DbContext(DB db) {
        this.db = db;
        this.sensorDao = db.sensorDao();
        this.valueSensorDao = db.valueSensorDao();
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "db-write-pool");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Single-row writes ─────────────────────────────────────────────────────

    public void insertAccel(AccelData data) {
        executor.execute(() -> db.runInTransaction(() -> sensorDao.insert(data)));
    }

    public void insertGyro(GyroData data) {
        executor.execute(() -> db.runInTransaction(() -> sensorDao.insert(data)));
    }

    public void insertMagnet(MagnetData data) {
        executor.execute(() -> db.runInTransaction(() -> sensorDao.insert(data)));
    }

    public void insertEreignis(EreignisData data) {
        executor.execute(() -> db.runInTransaction(() -> sensorDao.insert(data)));
    }

    public void insertValueSensor(ValueSensor vs) {
        executor.execute(() -> db.runInTransaction(() -> valueSensorDao.insert(vs)));
    }

    // ── Batch writes (MCP historical sync) ───────────────────────────────────

    public void insertAccelBatch(List<AccelData> batch) {
        if (batch == null || batch.isEmpty()) return;
        executor.execute(() -> db.runInTransaction(() -> {
            for (AccelData d : batch) sensorDao.insert(d);
        }));
    }

    public void insertGyroBatch(List<GyroData> batch) {
        if (batch == null || batch.isEmpty()) return;
        executor.execute(() -> db.runInTransaction(() -> {
            for (GyroData d : batch) sensorDao.insert(d);
        }));
    }

    public void insertMagnetBatch(List<MagnetData> batch) {
        if (batch == null || batch.isEmpty()) return;
        executor.execute(() -> db.runInTransaction(() -> {
            for (MagnetData d : batch) sensorDao.insert(d);
        }));
    }

    // ── DAO accessors (for read LiveData) ─────────────────────────────────────

    public SensorDao sensorDao() { return sensorDao; }
    public ValueSensorDAO valueSensorDao() { return valueSensorDao; }
}
