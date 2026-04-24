package com.fhdw.biot.speech.iot.repository;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * McpDataSyncService — Observer-pattern bridge between the MCP/FastAPI server and
 * the in-memory Room database.
 *
 * Pattern:
 *   1. Caller invokes {@link #fetchAccel(long, long)} etc. and returns immediately.
 *   2. An internal background thread fires the HTTP request to the LLM app.
 *   3. On success: rows are inserted into Room via {@link SensorRepository}, then
 *      the result is posted to a {@link MutableLiveData}.
 *   4. Any Activity observing the LiveData is notified automatically on the main
 *      thread — no polling, no blocking.
 *
 * Endpoints (LLM app):
 *   GET {baseUrl}/data/accel?from={fromMs}&to={toMs}
 *   GET {baseUrl}/data/gyro?from={fromMs}&to={toMs}
 *   GET {baseUrl}/data/magnet?from={fromMs}&to={toMs}
 *
 * Response shape expected:
 *   { "rows": [ { "timestamp": 1234, "x": 0.1, "y": 0.2, "z": 0.3 }, ... ] }
 */
public class McpDataSyncService {

    private static final String TAG = "McpDataSyncService";

    private static final String PATH_ACCEL  = "/data/accel";
    private static final String PATH_GYRO   = "/data/gyro";
    private static final String PATH_MAGNET = "/data/magnet";

    private static final int TIMEOUT_MS = 10_000;

    private final SensorRepository repository;
    private final String baseUrl;
    private final ExecutorService executor;

    // ── Observer LiveData ─────────────────────────────────────────────────────

    private final MutableLiveData<List<AccelData>>  accelHistory  = new MutableLiveData<>();
    private final MutableLiveData<List<GyroData>>   gyroHistory   = new MutableLiveData<>();
    private final MutableLiveData<List<MagnetData>> magnetHistory = new MutableLiveData<>();
    private final MutableLiveData<String>           syncError     = new MutableLiveData<>();

    public LiveData<List<AccelData>>  accelHistory()  { return accelHistory; }
    public LiveData<List<GyroData>>   gyroHistory()   { return gyroHistory; }
    public LiveData<List<MagnetData>> magnetHistory() { return magnetHistory; }
    public LiveData<String>           syncError()     { return syncError; }

    // ─────────────────────────────────────────────────────────────────────────

    public McpDataSyncService(SensorRepository repository, String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mcp-sync-thread");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Public fetch API ──────────────────────────────────────────────────────

    public void fetchAccel(long fromMs, long toMs) {
        fetchSensor(PATH_ACCEL, "Accel", fromMs, toMs,
                row -> {
                    AccelData d = new AccelData();
                    d.timestamp = row.getLong("timestamp");
                    d.accelX    = (float) row.getDouble("x");
                    d.accelY    = (float) row.getDouble("y");
                    d.accelZ    = (float) row.getDouble("z");
                    return d;
                },
                repository::insertAccelBatch,
                accelHistory);
    }

    public void fetchGyro(long fromMs, long toMs) {
        fetchSensor(PATH_GYRO, "Gyro", fromMs, toMs,
                row -> {
                    GyroData d = new GyroData();
                    d.timestamp = row.getLong("timestamp");
                    d.gyroX     = (float) row.getDouble("x");
                    d.gyroY     = (float) row.getDouble("y");
                    d.gyroZ     = (float) row.getDouble("z");
                    return d;
                },
                repository::insertGyroBatch,
                gyroHistory);
    }

    public void fetchMagnet(long fromMs, long toMs) {
        fetchSensor(PATH_MAGNET, "Magnet", fromMs, toMs,
                row -> {
                    MagnetData d = new MagnetData();
                    d.timestamp = row.getLong("timestamp");
                    d.magnetX   = (float) row.getDouble("x");
                    d.magnetY   = (float) row.getDouble("y");
                    d.magnetZ   = (float) row.getDouble("z");
                    return d;
                },
                repository::insertMagnetBatch,
                magnetHistory);
    }

    // ── Generic fetch helper ──────────────────────────────────────────────────

    /**
     * Executes an HTTP GET to the given sensor path, maps each JSON row to a
     * domain object via {@code mapper}, batch-inserts via {@code batchInsert},
     * and posts the result list to {@code liveData}.
     *
     * @param path        URL path suffix (e.g. "/data/accel")
     * @param label       Human-readable name used in log messages
     * @param fromMs      Range start (epoch ms)
     * @param toMs        Range end (epoch ms)
     * @param mapper      Converts one JSON row object to the domain entity
     * @param batchInsert Inserts the completed list into Room
     * @param liveData    Receives the result on success, empty list on failure
     */
    private <T> void fetchSensor(
            String path,
            String label,
            long fromMs,
            long toMs,
            RowMapper<T> mapper,
            Consumer<List<T>> batchInsert,
            MutableLiveData<List<T>> liveData) {

        executor.submit(() -> {
            try {
                List<T> data = httpGetSensor(path, fromMs, toMs, mapper);
                batchInsert.accept(data);
                liveData.postValue(data);
                Log.i(TAG, "fetch" + label + ": " + data.size() + " rows");
            } catch (Exception e) {
                Log.e(TAG, "fetch" + label + " failed: " + e.getMessage(), e);
                syncError.postValue(label + " history unavailable: " + e.getMessage());
                liveData.postValue(Collections.emptyList());
            }
        });
    }

    private <T> List<T> httpGetSensor(
            String path, long fromMs, long toMs, RowMapper<T> mapper) throws Exception {
        String json = httpGet(path, fromMs, toMs);
        JSONArray rows = new JSONObject(json).getJSONArray("rows");
        List<T> result = new ArrayList<>(rows.length());
        for (int i = 0; i < rows.length(); i++) {
            result.add(mapper.map(rows.getJSONObject(i)));
        }
        return result;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String httpGet(String path, long fromMs, long toMs) throws Exception {
        URL url = new URL(baseUrl + path + "?from=" + fromMs + "&to=" + toMs);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException("HTTP " + status + " from " + url);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    // ── Inner interfaces ──────────────────────────────────────────────────────

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(JSONObject row) throws Exception;
    }
}
