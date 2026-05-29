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
 * <p>Every write goes through {@link DB#runInTransaction(Runnable)}, which wraps the operation in a
 * SQLite transaction and rolls back automatically on any exception. This matches the EF Core
 * DbContext pattern: callers never touch the DAO directly for writes; they call DbContext which
 * owns the executor and the transaction boundary.
 *
 * <p>Reads (LiveData) are exposed via the DAO accessors so Room can manage the observer lifecycle
 * automatically.
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
        // Single-thread executor: SQLite only allows one writer at a time.
        // A thread pool causes 3 threads to always block on the write lock — wasteful.
        this.executor =
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "db-write");
                    t.setDaemon(true);
                    return t;
                });
    }

    /** Returns the shared DB write executor for use by application-scope services. */
    public ExecutorService executor() {
        return executor;
    }

    // ── Single-row writes (live MQTT path) ───────────────────────────────────
    // Each write is submitted to the single-thread executor and wrapped in a
    // transaction so it is ACID and never blocks the MQTT subscriber thread.

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

    // ── Batch writes (McpDataSyncService historical fetch) ───────────────────
    // insertAll() generates a single bulk INSERT statement — ~90x faster than
    // calling insert() in a loop for a 500-row page. Error is logged but does
    // not propagate; a failed batch just leaves a gap in the chart history.

    public void insertAccelBatch(List<AccelData> batch) {
        if (batch == null || batch.isEmpty()) return;
        android.util.Log.d("DbContext", "GRAPH_DB: queuing accel batch size=" + batch.size());
        executor.execute(() -> {
            try {
                db.runInTransaction(() -> sensorDao.insertAll(batch));
                android.util.Log.d("DbContext", "GRAPH_DB: accel batch committed size=" + batch.size());
            } catch (Exception e) {
                android.util.Log.e("DbContext", "GRAPH_DB: accel batch FAILED: " + e.getMessage(), e);
            }
        });
    }

    public void insertGyroBatch(List<GyroData> batch) {
        if (batch == null || batch.isEmpty()) return;
        android.util.Log.d("DbContext", "GRAPH_DB: queuing gyro batch size=" + batch.size());
        executor.execute(() -> {
            try {
                db.runInTransaction(() -> sensorDao.insertAll(batch));
                android.util.Log.d("DbContext", "GRAPH_DB: gyro batch committed size=" + batch.size());
            } catch (Exception e) {
                android.util.Log.e("DbContext", "GRAPH_DB: gyro batch FAILED: " + e.getMessage(), e);
            }
        });
    }

    public void insertMagnetBatch(List<MagnetData> batch) {
        if (batch == null || batch.isEmpty()) return;
        android.util.Log.d("DbContext", "GRAPH_DB: queuing magnet batch size=" + batch.size());
        executor.execute(() -> {
            try {
                db.runInTransaction(() -> sensorDao.insertAll(batch));
                android.util.Log.d("DbContext", "GRAPH_DB: magnet batch committed size=" + batch.size());
            } catch (Exception e) {
                android.util.Log.e("DbContext", "GRAPH_DB: magnet batch FAILED: " + e.getMessage(), e);
            }
        });
    }

    // ── DAO accessors (for read LiveData) ─────────────────────────────────────

    public SensorDao sensorDao() {
        return sensorDao;
    }

    public ValueSensorDAO valueSensorDao() {
        return valueSensorDao;
    }
}
