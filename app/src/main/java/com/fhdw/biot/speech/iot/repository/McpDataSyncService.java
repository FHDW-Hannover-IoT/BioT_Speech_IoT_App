package com.fhdw.biot.speech.iot.repository;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.fhdw.biot.speech.iot.BuildConfig;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
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
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * McpDataSyncService — fetches historical sensor data from the LLM/FastAPI server and inserts it
 * into the local Room database in paginated chunks.
 *
 * <p>Fetch strategy:
 *
 * <ol>
 *   <li>{@link #fetchInitial()} — called on app start; pulls the last {@code
 *       LLM_INITIAL_FETCH_HOURS} of data in 500-row pages so charts have data immediately.
 *   <li>{@link #fetchRange(long, long)} — called when the user selects a time filter wider than
 *       what is already in Room; extends the fetch window backwards in 500-row pages.
 * </ol>
 *
 * <p>Each page is inserted into Room and posted to LiveData immediately, so charts update
 * progressively instead of waiting for a single large response.
 *
 * <p>HTTP keep-alive is preserved (no {@code disconnect()} call) so the JVM connection pool reuses
 * the same TCP socket across all pages and all three sensor endpoints.
 */
public class McpDataSyncService {

    private static final String TAG = "McpDataSyncService";

    private static final String PATH_ACCEL = "/data/accel";
    private static final String PATH_GYRO = "/data/gyro";
    private static final String PATH_MAGNET = "/data/magnet";

    private final SensorRepository repository;
    private volatile String baseUrl;
    private final ExecutorService executor;

    // ── Oldest fetched timestamp per sensor ───────────────────────────────────
    // Long.MAX_VALUE = nothing fetched yet (Room is always cleared on startup).
    // Written only from within executor tasks — no race conditions on the single-thread pool.
    private long oldestFetchedAccelMs = Long.MAX_VALUE;
    private long oldestFetchedGyroMs = Long.MAX_VALUE;
    private long oldestFetchedMagnetMs = Long.MAX_VALUE;

    // ── LiveData ──────────────────────────────────────────────────────────────

    private final MutableLiveData<List<AccelData>> accelHistory = new MutableLiveData<>();
    private final MutableLiveData<List<GyroData>> gyroHistory = new MutableLiveData<>();
    private final MutableLiveData<List<MagnetData>> magnetHistory = new MutableLiveData<>();
    private final MutableLiveData<String> syncError = new MutableLiveData<>();

    public LiveData<List<AccelData>> accelHistory() {
        return accelHistory;
    }

    public LiveData<List<GyroData>> gyroHistory() {
        return gyroHistory;
    }

    public LiveData<List<MagnetData>> magnetHistory() {
        return magnetHistory;
    }

    public LiveData<String> syncError() {
        return syncError;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public McpDataSyncService(SensorRepository repository, String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl;
        this.executor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "mcp-sync-thread");
                            t.setDaemon(true);
                            return t;
                        });
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches the last {@code LLM_INITIAL_FETCH_HOURS} hours of data for all three sensors in
     * 500-row pages. Called once on app start. Charts begin updating after the first page.
     */
    public void fetchInitial() {
        long toMs = System.currentTimeMillis();
        long fromMs = toMs - (BuildConfig.LLM_INITIAL_FETCH_HOURS * 3_600_000L);
        executor.submit(
                () -> {
                    fetchPagedAccel(fromMs, toMs);
                    fetchPagedGyro(fromMs, toMs);
                    fetchPagedMagnet(fromMs, toMs);
                });
    }

    /**
     * Extends the local Room cache to cover {@code [fromMs, toMs]} for all three sensors. Only
     * fetches the gap between {@code fromMs} and what is already in Room — skips sensors whose data
     * already covers the requested range.
     *
     * <p>Call this whenever the user selects a time filter wider than the initial 1-hour window.
     *
     * @param fromMs start of the desired range in epoch milliseconds
     * @param toMs end of the desired range in epoch milliseconds
     */
    public void fetchRange(long fromMs, long toMs) {
        executor.submit(
                () -> {
                    if (fromMs < oldestFetchedAccelMs) {
                        long fetchTo =
                                Math.min(
                                        toMs,
                                        oldestFetchedAccelMs == Long.MAX_VALUE
                                                ? toMs
                                                : oldestFetchedAccelMs - 1);
                        fetchPagedAccel(fromMs, fetchTo);
                    }
                    if (fromMs < oldestFetchedGyroMs) {
                        long fetchTo =
                                Math.min(
                                        toMs,
                                        oldestFetchedGyroMs == Long.MAX_VALUE
                                                ? toMs
                                                : oldestFetchedGyroMs - 1);
                        fetchPagedGyro(fromMs, fetchTo);
                    }
                    if (fromMs < oldestFetchedMagnetMs) {
                        long fetchTo =
                                Math.min(
                                        toMs,
                                        oldestFetchedMagnetMs == Long.MAX_VALUE
                                                ? toMs
                                                : oldestFetchedMagnetMs - 1);
                        fetchPagedMagnet(fromMs, fetchTo);
                    }
                });
    }

    // ── Per-sensor paginated fetchers ─────────────────────────────────────────

    private void fetchPagedAccel(long fromMs, long toMs) {
        List<AccelData> accumulated = new ArrayList<>();
        long cursor = fromMs;
        try {
            while (true) {
                JSONArray rows = fetchPage(PATH_ACCEL, cursor, toMs);
                if (rows.length() == 0) break;

                List<AccelData> page = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    AccelData d = new AccelData();
                    d.timestamp = r.getLong("timestamp");
                    d.accelX = (float) r.getDouble("x");
                    d.accelY = (float) r.getDouble("y");
                    d.accelZ = (float) r.getDouble("z");
                    page.add(d);
                }
                repository.insertAccelBatch(page);
                accumulated.addAll(page);
                accelHistory.postValue(new ArrayList<>(accumulated));
                Log.i(TAG, "Accel page: " + page.size() + " rows (cursor=" + cursor + ")");

                if (page.size() < BuildConfig.LLM_FETCH_PAGE_SIZE) break;
                cursor = rows.getJSONObject(rows.length() - 1).getLong("timestamp") + 1;
            }
            oldestFetchedAccelMs = Math.min(oldestFetchedAccelMs, fromMs);
        } catch (Exception e) {
            Log.e(TAG, "fetchPagedAccel failed: " + e.getMessage(), e);
            syncError.postValue("Accel history unavailable: " + e.getMessage());
            if (accumulated.isEmpty()) accelHistory.postValue(Collections.emptyList());
        }
    }

    private void fetchPagedGyro(long fromMs, long toMs) {
        List<GyroData> accumulated = new ArrayList<>();
        long cursor = fromMs;
        try {
            while (true) {
                JSONArray rows = fetchPage(PATH_GYRO, cursor, toMs);
                if (rows.length() == 0) break;

                List<GyroData> page = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    GyroData d = new GyroData();
                    d.timestamp = r.getLong("timestamp");
                    d.gyroX = (float) r.getDouble("x");
                    d.gyroY = (float) r.getDouble("y");
                    d.gyroZ = (float) r.getDouble("z");
                    page.add(d);
                }
                repository.insertGyroBatch(page);
                accumulated.addAll(page);
                gyroHistory.postValue(new ArrayList<>(accumulated));
                Log.i(TAG, "Gyro page: " + page.size() + " rows (cursor=" + cursor + ")");

                if (page.size() < BuildConfig.LLM_FETCH_PAGE_SIZE) break;
                cursor = rows.getJSONObject(rows.length() - 1).getLong("timestamp") + 1;
            }
            oldestFetchedGyroMs = Math.min(oldestFetchedGyroMs, fromMs);
        } catch (Exception e) {
            Log.e(TAG, "fetchPagedGyro failed: " + e.getMessage(), e);
            syncError.postValue("Gyro history unavailable: " + e.getMessage());
            if (accumulated.isEmpty()) gyroHistory.postValue(Collections.emptyList());
        }
    }

    private void fetchPagedMagnet(long fromMs, long toMs) {
        List<MagnetData> accumulated = new ArrayList<>();
        long cursor = fromMs;
        try {
            while (true) {
                JSONArray rows = fetchPage(PATH_MAGNET, cursor, toMs);
                if (rows.length() == 0) break;

                List<MagnetData> page = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    MagnetData d = new MagnetData();
                    d.timestamp = r.getLong("timestamp");
                    d.magnetX = (float) r.getDouble("x");
                    d.magnetY = (float) r.getDouble("y");
                    d.magnetZ = (float) r.getDouble("z");
                    page.add(d);
                }
                repository.insertMagnetBatch(page);
                accumulated.addAll(page);
                magnetHistory.postValue(new ArrayList<>(accumulated));
                Log.i(TAG, "Magnet page: " + page.size() + " rows (cursor=" + cursor + ")");

                if (page.size() < BuildConfig.LLM_FETCH_PAGE_SIZE) break;
                cursor = rows.getJSONObject(rows.length() - 1).getLong("timestamp") + 1;
            }
            oldestFetchedMagnetMs = Math.min(oldestFetchedMagnetMs, fromMs);
        } catch (Exception e) {
            Log.e(TAG, "fetchPagedMagnet failed: " + e.getMessage(), e);
            syncError.postValue("Magnet history unavailable: " + e.getMessage());
            if (accumulated.isEmpty()) magnetHistory.postValue(Collections.emptyList());
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    /**
     * Fetches one page of sensor rows from the server. Does NOT call {@code disconnect()} — the JVM
     * keep-alive pool reuses the socket across consecutive pages and across sensor endpoints.
     */
    private JSONArray fetchPage(String path, long fromMs, long toMs) throws Exception {
        URL url =
                new URL(
                        baseUrl
                                + path
                                + "?from="
                                + fromMs
                                + "&to="
                                + toMs
                                + "&limit="
                                + BuildConfig.LLM_FETCH_PAGE_SIZE);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setConnectTimeout(BuildConfig.LLM_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(BuildConfig.LLM_READ_TIMEOUT_MS);

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("HTTP " + status + " from " + url);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return new JSONObject(sb.toString()).getJSONArray("rows");
    }
}
