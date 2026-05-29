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
 * McpDataSyncService — fetches historical sensor data from the LLM/FastAPI server and inserts
 * it into the local Room database in paginated chunks.
 *
 * <p>Fetch strategy:
 * <ol>
 *   <li>{@link #fetchInitial()} — called on app start; pulls the last
 *       {@code LLM_INITIAL_FETCH_HOURS} of data in 500-row pages so charts have data immediately.
 *   <li>{@link #fetchRange(long, long)} — called when the user selects a time filter wider than
 *       what is already in Room; extends the fetch window backwards in 500-row pages.
 * </ol>
 *
 * <p>Each page is inserted into Room and posted to LiveData immediately, so charts update
 * progressively instead of waiting for a single large response.
 *
 * <p>HTTP keep-alive is preserved (no {@code disconnect()} call) so the JVM connection pool
 * reuses the same TCP socket across all pages and all three sensor endpoints.
 */
public class McpDataSyncService {

    private static final String TAG = "McpDataSyncService";

    private static final String PATH_ACCEL  = "/data/accel";
    private static final String PATH_GYRO   = "/data/gyro";
    private static final String PATH_MAGNET = "/data/magnet";

    private final SensorRepository repository;
    private volatile String baseUrl;
    private final ExecutorService executor;

    // ── Oldest fetched timestamp per sensor ───────────────────────────────────
    // Long.MAX_VALUE = nothing fetched yet (Room is always cleared on startup).
    // Written only from within executor tasks — no race conditions on the single-thread pool.
    private long oldestFetchedAccelMs  = Long.MAX_VALUE;
    private long oldestFetchedGyroMs   = Long.MAX_VALUE;
    private long oldestFetchedMagnetMs = Long.MAX_VALUE;

    // ── LiveData ──────────────────────────────────────────────────────────────

    // Observed by sensor activities to anchor the chart window to the latest
    // fetched timestamp rather than phone clock time (avoids sliding-window miss
    // when seeded data timestamps are behind the phone's current time).
    private final MutableLiveData<List<AccelData>>  accelHistory  = new MutableLiveData<>();
    private final MutableLiveData<List<GyroData>>   gyroHistory   = new MutableLiveData<>();
    private final MutableLiveData<List<MagnetData>> magnetHistory = new MutableLiveData<>();

    public LiveData<List<AccelData>>  accelHistory()  { return accelHistory; }
    public LiveData<List<GyroData>>   gyroHistory()   { return gyroHistory; }
    public LiveData<List<MagnetData>> magnetHistory() { return magnetHistory; }

    // ─────────────────────────────────────────────────────────────────────────

    public McpDataSyncService(SensorRepository repository, String baseUrl) {
        this.repository = repository;
        this.baseUrl    = baseUrl;
        this.executor   = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mcp-sync-thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Updates the base URL at runtime when the user changes the server IP in Settings.
     * Volatile field ensures the change is visible to the background executor thread immediately.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches three resolution tiers for all sensors on app start:
     * <ul>
     *   <li><b>raw</b>  — last 1 hour; dense data for the 10-min / 30-min sliding window.</li>
     *   <li><b>1min</b> — last 24 hours; one point per minute for the 1h–24h views.</li>
     *   <li><b>1hour</b>— all available history; one point per hour for the >24h views.</li>
     * </ul>
     * After the raw tier finishes, {@code accelHistory} / {@code gyroHistory} / {@code magnetHistory}
     * are posted so chart activities can anchor their sliding windows immediately.
     */
    public void fetchInitial() {
        long now    = System.currentTimeMillis();
        long rawFrom   = now - 3_600_000L;          // 1 hour
        long min1From  = now - 86_400_000L;          // 24 hours
        long hour1From = 0L;                          // all available history
        Log.i(TAG, "GRAPH_FETCH: fetchInitial baseUrl=" + baseUrl + " tiers=raw/1min/1hour");
        executor.submit(() -> {
            // Tier 1 — raw (last 1 h); post LiveData so activities can anchor their windows
            fetchPagedAccel(rawFrom,   now, "raw");
            fetchPagedGyro(rawFrom,    now, "raw");
            fetchPagedMagnet(rawFrom,  now, "raw");

            // Tier 2 — 1-min aggregates (last 24 h)
            fetchPagedAccel(min1From,  now, "1min");
            fetchPagedGyro(min1From,   now, "1min");
            fetchPagedMagnet(min1From, now, "1min");

            // Tier 3 — 1-hour aggregates (full history)
            fetchPagedAccel(hour1From,  now, "1hour");
            fetchPagedGyro(hour1From,   now, "1hour");
            fetchPagedMagnet(hour1From, now, "1hour");

            Log.i(TAG, "GRAPH_FETCH: fetchInitial complete (3 tiers)");
        });
    }

    /**
     * Fetches a gap range for all three sensors — used when the user picks a manual date range
     * via the date pickers that extends beyond what {@code fetchInitial} already covers.
     * Resolution is auto-selected from the window size.
     *
     * @param fromMs start of the desired range in epoch milliseconds
     * @param toMs   end of the desired range in epoch milliseconds
     */
    public void fetchRange(long fromMs, long toMs) {
        long windowMs  = toMs - fromMs;
        String res = windowMs < 3_600_000L ? "raw" : windowMs <= 86_400_000L ? "1min" : "1hour";
        executor.submit(() -> {
            if (fromMs < oldestFetchedAccelMs) {
                long fetchTo = oldestFetchedAccelMs == Long.MAX_VALUE ? toMs : oldestFetchedAccelMs - 1;
                fetchPagedAccel(fromMs, Math.min(toMs, fetchTo), res);
            }
            if (fromMs < oldestFetchedGyroMs) {
                long fetchTo = oldestFetchedGyroMs == Long.MAX_VALUE ? toMs : oldestFetchedGyroMs - 1;
                fetchPagedGyro(fromMs, Math.min(toMs, fetchTo), res);
            }
            if (fromMs < oldestFetchedMagnetMs) {
                long fetchTo = oldestFetchedMagnetMs == Long.MAX_VALUE ? toMs : oldestFetchedMagnetMs - 1;
                fetchPagedMagnet(fromMs, Math.min(toMs, fetchTo), res);
            }
        });
    }

    // ── Per-sensor paginated fetchers ─────────────────────────────────────────

    private void fetchPagedAccel(long fromMs, long toMs, String resolution) {
        List<AccelData> accumulated = new ArrayList<>();
        long cursor = fromMs;
        try {
            while (true) {
                JSONArray rows = fetchPage(PATH_ACCEL, cursor, toMs, resolution);
                if (rows.length() == 0) break;

                List<AccelData> page = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    AccelData d = new AccelData();
                    d.timestamp  = r.getLong("timestamp");
                    d.accelX     = (float) r.getDouble("x");
                    d.accelY     = (float) r.getDouble("y");
                    d.accelZ     = (float) r.getDouble("z");
                    d.resolution = resolution;
                    page.add(d);
                }
                repository.insertAccelBatch(page);
                accumulated.addAll(page);
                if ("raw".equals(resolution)) accelHistory.postValue(new ArrayList<>(accumulated));
                Log.i(TAG, "GRAPH_FETCH: accel[" + resolution + "] page " + page.size() + " rows cursor=" + cursor + " total=" + accumulated.size());

                if (page.size() < BuildConfig.LLM_FETCH_PAGE_SIZE) break;
                cursor = rows.getJSONObject(rows.length() - 1).getLong("timestamp") + 1;
            }
            oldestFetchedAccelMs = Math.min(oldestFetchedAccelMs, fromMs);
            Log.i(TAG, "GRAPH_FETCH: accel[" + resolution + "] done total=" + accumulated.size());
        } catch (Exception e) {
            Log.e(TAG, "fetchPagedAccel[" + resolution + "] failed: " + e.getMessage(), e);
            if (accumulated.isEmpty() && "raw".equals(resolution)) accelHistory.postValue(Collections.emptyList());
        }
    }

    private void fetchPagedGyro(long fromMs, long toMs, String resolution) {
        List<GyroData> accumulated = new ArrayList<>();
        long cursor = fromMs;
        try {
            while (true) {
                JSONArray rows = fetchPage(PATH_GYRO, cursor, toMs, resolution);
                if (rows.length() == 0) break;

                List<GyroData> page = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    GyroData d = new GyroData();
                    d.timestamp  = r.getLong("timestamp");
                    d.gyroX      = (float) r.getDouble("x");
                    d.gyroY      = (float) r.getDouble("y");
                    d.gyroZ      = (float) r.getDouble("z");
                    d.resolution = resolution;
                    page.add(d);
                }
                repository.insertGyroBatch(page);
                accumulated.addAll(page);
                if ("raw".equals(resolution)) gyroHistory.postValue(new ArrayList<>(accumulated));
                Log.i(TAG, "GRAPH_FETCH: gyro[" + resolution + "] page " + page.size() + " rows cursor=" + cursor + " total=" + accumulated.size());

                if (page.size() < BuildConfig.LLM_FETCH_PAGE_SIZE) break;
                cursor = rows.getJSONObject(rows.length() - 1).getLong("timestamp") + 1;
            }
            oldestFetchedGyroMs = Math.min(oldestFetchedGyroMs, fromMs);
            Log.i(TAG, "GRAPH_FETCH: gyro[" + resolution + "] done total=" + accumulated.size());
        } catch (Exception e) {
            Log.e(TAG, "fetchPagedGyro[" + resolution + "] failed: " + e.getMessage(), e);
            if (accumulated.isEmpty() && "raw".equals(resolution)) gyroHistory.postValue(Collections.emptyList());
        }
    }

    private void fetchPagedMagnet(long fromMs, long toMs, String resolution) {
        List<MagnetData> accumulated = new ArrayList<>();
        long cursor = fromMs;
        try {
            while (true) {
                JSONArray rows = fetchPage(PATH_MAGNET, cursor, toMs, resolution);
                if (rows.length() == 0) break;

                List<MagnetData> page = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    MagnetData d = new MagnetData();
                    d.timestamp  = r.getLong("timestamp");
                    d.magnetX    = (float) r.getDouble("x");
                    d.magnetY    = (float) r.getDouble("y");
                    d.magnetZ    = (float) r.getDouble("z");
                    d.resolution = resolution;
                    page.add(d);
                }
                repository.insertMagnetBatch(page);
                accumulated.addAll(page);
                if ("raw".equals(resolution)) magnetHistory.postValue(new ArrayList<>(accumulated));
                Log.i(TAG, "GRAPH_FETCH: magnet[" + resolution + "] page " + page.size() + " rows cursor=" + cursor + " total=" + accumulated.size());

                if (page.size() < BuildConfig.LLM_FETCH_PAGE_SIZE) break;
                cursor = rows.getJSONObject(rows.length() - 1).getLong("timestamp") + 1;
            }
            oldestFetchedMagnetMs = Math.min(oldestFetchedMagnetMs, fromMs);
            Log.i(TAG, "GRAPH_FETCH: magnet[" + resolution + "] done total=" + accumulated.size());
        } catch (Exception e) {
            Log.e(TAG, "fetchPagedMagnet[" + resolution + "] failed: " + e.getMessage(), e);
            if (accumulated.isEmpty() && "raw".equals(resolution)) magnetHistory.postValue(Collections.emptyList());
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    /**
     * Fetches one page of sensor rows from the server with an explicit resolution tier.
     * Does NOT call {@code disconnect()} — the JVM keep-alive pool reuses the socket
     * across consecutive pages and across sensor endpoints.
     */
    private JSONArray fetchPage(String path, long fromMs, long toMs, String resolution) throws Exception {
        URL url = new URL(baseUrl + path
                + "?from=" + fromMs
                + "&to="   + toMs
                + "&limit=" + BuildConfig.LLM_FETCH_PAGE_SIZE
                + "&resolution=" + resolution);
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
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return new JSONObject(sb.toString()).getJSONArray("rows");
    }
}
