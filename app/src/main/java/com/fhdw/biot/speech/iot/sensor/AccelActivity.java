package com.fhdw.biot.speech.iot.sensor;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Spinner;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.config.BiotApplication;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.BaseChartActivity;
import com.fhdw.biot.speech.iot.graph.IFilterableChart;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.repository.SensorRepository;
import com.fhdw.biot.speech.iot.voice.VoiceCommandExecutor;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * AccelActivity ------------- Screen that visualizes accelerometer data in three separate line
 * charts (X, Y, Z axes) and lets the user filter by date range or "last 10 minutes".
 *
 * <p>Responsibilities: - Navigation between sensor screens and main screen. - Fetching accel data
 * from Room via DB.sensorDao(). - Applying time filters via date pickers. - Mapping DB entities →
 * MPAndroidChart entries. - Delegating chart styling to BaseChartActivity.
 */
public class AccelActivity extends BaseChartActivity implements IFilterableChart {

    /** Individual charts for each axis of the accelerometer. */
    private LineChart lineChartAccelX, lineChartAccelY, lineChartAccelZ;

    /** Selected date range for filtering. */
    private Calendar dateFromCalendar;

    private Calendar dateToCalendar;

    private Spinner spinnerTimeframe;
    private int selectedMinutes = 10;

    /** Chart visibility checkboxes. */
    private CheckBox cbChartX, cbChartY, cbChartZ;

    private SensorRepository sensorRepository;
    private LiveData<List<AccelData>> currentLiveData;

    private Handler slidingWindowHandler = new Handler(Looper.getMainLooper());
    private Runnable slidingWindowRunnable;
    /** True when the sliding window is auto-advancing; false when the user picked a manual range. */
    private boolean isTenMinuteFilterActive = false;

    /**
     * Threading model for chart rendering (keeps the main thread free):
     *
     * <pre>
     * LiveData observer (main thread)
     *   └─► chartExecutor (1 thread) — runs Douglas-Peucker simplification
     *         ├─► axisExecutor thread 1 — builds X Entry list
     *         ├─► axisExecutor thread 2 — builds Y Entry list
     *         └─► axisExecutor thread 3 — builds Z Entry list
     *               └─► runOnUiThread → setData + invalidate (main thread)
     *                     └─► CountDownLatch → pinViewport after all 3 finish
     * </pre>
     *
     * Deadlock is impossible: main thread never waits on any background thread.
     * The 3-second latch timeout is a safety guard — building 600 entries takes <10 ms.
     */
    // Coordinator: runs DP, then dispatches axis tasks. Single thread avoids
    // multiple concurrent DP runs stacking up when Room fires rapid updates.
    private final java.util.concurrent.ExecutorService chartExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    // One thread per axis — X, Y, Z build their Entry lists simultaneously.
    private final java.util.concurrent.ExecutorService axisExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(3);
    // Incremented on each new render request; old in-flight renders check this
    // before posting to the UI and discard themselves if superseded.
    private final AtomicInteger renderGeneration = new AtomicInteger(0);

    /** Anchor for the X-axis origin. Set to the first sample's timestamp so elapsed
     *  time (ms) is used as the X value instead of absolute epoch time. Reset when
     *  the user picks a new date range via the date pickers. */
    private long windowStart = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beschleunigung);
        sensorRepository = ((BiotApplication) getApplication()).getContainer().sensorRepository();

        // When McpDataSyncService delivers fetched history, anchor the sliding window to
        // the latest data timestamp. Skipped if the user manually picked a date range.
        ((BiotApplication) getApplication()).getContainer().mcpDataSync()
                .accelHistory()
                .observe(this, fetched -> {
                    if (fetched == null || fetched.isEmpty()) return;
                    if (!isTenMinuteFilterActive) return;
                    long latestTs = fetched.get(fetched.size() - 1).timestamp;
                    long windowMs = (long) selectedMinutes * 60_000L;
                    dateFromCalendar.setTimeInMillis(latestTs - windowMs);
                    dateToCalendar.setTimeInMillis(latestTs);
                    android.util.Log.i("AccelActivity", "GRAPH_UI: accelHistory fired rows=" + fetched.size() + " latestTs=" + latestTs + " window=" + selectedMinutes + "min");
                    updateChartsWithDateFilter();
                });

        // Ensure content is not hidden under system bars (status/navigation).
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.accel),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        // --------------------------------------------------------------------
        // Navigation buttons
        // --------------------------------------------------------------------

        // Go to previous sensor screen: MagnetActivity
        Button buttonMagnet = findViewById(R.id.btnPrevMagnet);
        buttonMagnet.setOnClickListener(
                view -> {
                    Intent intent = new Intent(AccelActivity.this, MagnetActivity.class);
                    startActivity(intent);
                });

        // Go to next sensor screen: GyroActivity
        Button buttonGyro = findViewById(R.id.btnNextGyro);
        buttonGyro.setOnClickListener(
                view -> {
                    Intent intent = new Intent(AccelActivity.this, GyroActivity.class);
                    startActivity(intent);
                });

        // Home button: return to main values / MQTT screen
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(AccelActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        // Ereignis button: open list of events filtered for ACCEL
        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(AccelActivity.this, EreignisActivity.class);
                    intent.putExtra("SENSOR_FILTER", "ACCEL");
                    startActivity(intent);
                });

        // --------------------------------------------------------------------
        // Timeframe filter spinner
        // --------------------------------------------------------------------

        spinnerTimeframe = findViewById(R.id.spinner_timeframe);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this, R.array.timeframe_labels, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimeframe.setAdapter(adapter);
        spinnerTimeframe.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] minutes = {10, 30, 60, 480, 1440, 10080};
                selectedMinutes = minutes[position];
                applyTimeFilter(selectedMinutes);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // --------------------------------------------------------------------
        // Chart views
        // --------------------------------------------------------------------

        lineChartAccelX = findViewById(R.id.lineChartAccelX);
        lineChartAccelY = findViewById(R.id.lineChartAccelY);
        lineChartAccelZ = findViewById(R.id.lineChartAccelZ);

        // Reset zoom/pan for each chart and clear date labels on buttons.
        ImageButton resetAccel = findViewById(R.id.resetX);
        resetAccel.setOnClickListener(
                view -> {
                    lineChartAccelX.fitScreen();
                    lineChartAccelY.fitScreen();
                    lineChartAccelZ.fitScreen();
                });

        // Initial "empty" chart configuration; actual data will come from DB.
        setupChart(lineChartAccelX, "X-Achse", 0);
        setupChart(lineChartAccelY, "Y-Achse", 0);
        setupChart(lineChartAccelZ, "Z-Achse", 0);

        // Chart visibility checkboxes
        cbChartX = findViewById(R.id.cbChartX);
        cbChartY = findViewById(R.id.cbChartY);
        cbChartZ = findViewById(R.id.cbChartZ);
        if (cbChartX != null)
            cbChartX.setOnCheckedChangeListener(
                    (b, c) -> lineChartAccelX.setVisibility(c ? View.VISIBLE : View.GONE));
        if (cbChartY != null)
            cbChartY.setOnCheckedChangeListener(
                    (b, c) -> lineChartAccelY.setVisibility(c ? View.VISIBLE : View.GONE));
        if (cbChartZ != null)
            cbChartZ.setOnCheckedChangeListener(
                    (b, c) -> lineChartAccelZ.setVisibility(c ? View.VISIBLE : View.GONE));

        // Configure date pickers and default date range.
        setupDatePickers();
    }

    // ------------------------------------------------------------------------
    // Date picker setup and range handling
    // ------------------------------------------------------------------------

    /**
     * Initializes the default date range for the filters. "Von" is set to the oldest accel sample;
     * "Bis" is set to "today".
     */
    private void setupDatePickers() {
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        isTenMinuteFilterActive = true;
        startSlidingWindow();
        checkVoiceFilterIntent();
    }

    private void startSlidingWindow() {
        slidingWindowHandler.removeCallbacks(slidingWindowRunnable);
        slidingWindowRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        if (!isTenMinuteFilterActive) return;

                        long now = System.currentTimeMillis();

                        dateFromCalendar.setTimeInMillis(now - (selectedMinutes * 60 * 1000L));

                        dateToCalendar.setTimeInMillis(now);

                        updateChartsWithDateFilter();

                        slidingWindowHandler.postDelayed(this, 5000);
                    }
                };

        slidingWindowHandler.post(slidingWindowRunnable);
    }

    private void stopSlidingWindow() {
        isTenMinuteFilterActive = false;
        if (slidingWindowHandler != null && slidingWindowRunnable != null) {
            slidingWindowHandler.removeCallbacks(slidingWindowRunnable);
        }
    }

    /**
     * Re-queries the DB using the current [dateFromCalendar, dateToCalendar] range and updates all
     * three charts with the filtered accel data.
     */
    private void updateChartsWithDateFilter() {
        if (dateFromCalendar == null || dateToCalendar == null) {
            return;
        }

        if (currentLiveData != null) {
            currentLiveData.removeObservers(this);
        }

        long fromTime = dateFromCalendar.getTimeInMillis();
        long toTime;

        if (isTenMinuteFilterActive) {
            toTime = dateToCalendar.getTimeInMillis();
        } else {
            Calendar adjustedToCalendar = (Calendar) dateToCalendar.clone();
            adjustedToCalendar.set(Calendar.HOUR_OF_DAY, 23);
            adjustedToCalendar.set(Calendar.MINUTE, 59);
            adjustedToCalendar.set(Calendar.SECOND, 59);
            toTime = adjustedToCalendar.getTimeInMillis();
        }

        String resolution = selectedMinutes <= 30 ? "raw" : selectedMinutes <= 1440 ? "1min" : "1hour";
        android.util.Log.d("AccelActivity", "GRAPH_UI: querying accel from=" + fromTime + " to=" + toTime + " window=" + selectedMinutes + "min res=" + resolution);
        currentLiveData = sensorRepository.getAccelBetween(fromTime, toTime, resolution);

        currentLiveData.observe(
                this,
                filteredData -> {
                    if (filteredData != null && !filteredData.isEmpty()) {
                        android.util.Log.i("AccelActivity", "GRAPH_LOAD: start accel rows=" + filteredData.size() + " from=" + fromTime + " to=" + toTime);
                        long firstTimestamp = filteredData.get(0).timestamp;
                        setupChart(lineChartAccelX, "X-Achse", firstTimestamp);
                        setupChart(lineChartAccelY, "Y-Achse", firstTimestamp);
                        setupChart(lineChartAccelZ, "Z-Achse", firstTimestamp);
                        displayDataInCharts(filteredData);
                    } else {
                        android.util.Log.w("AccelActivity", "GRAPH_UI: observer EMPTY from=" + fromTime + " to=" + toTime);
                        lineChartAccelX.clear();
                        lineChartAccelY.clear();
                        lineChartAccelZ.clear();
                    }
                });
    }

    // ------------------------------------------------------------------------
    // Data → Chart mapping
    // ------------------------------------------------------------------------

    /**
     * Converts a list of AccelData entities into three sets of MPAndroidChart entries (X/Y/Z) and
     * renders them on the respective charts.
     *
     * <p>X-axis values are "elapsed milliseconds since first sample".
     */
    private void displayDataInCharts(List<AccelData> list) {
        if (list.isEmpty()) return;
        final long wStart = (windowStart == 0) ? list.get(0).timestamp : windowStart;
        if (windowStart == 0) windowStart = wStart;
        final int gen = renderGeneration.incrementAndGet();

        android.util.Log.i("AccelActivity", "GRAPH_LOAD: start accel gen=" + gen + " rows=" + list.size());

        chartExecutor.execute(() -> {
            if (renderGeneration.get() != gen) {
                android.util.Log.d("AccelActivity", "GRAPH_LOAD: accel gen=" + gen + " stale, discarding");
                return;
            }

            CountDownLatch latch = new CountDownLatch(3);

            axisExecutor.submit(() -> {
                ArrayList<Entry> entries = new ArrayList<>(list.size());
                for (AccelData d : list) entries.add(new Entry(d.timestamp - wStart, d.accelX));
                runOnUiThread(() -> { setData(lineChartAccelX, entries, "X", Color.CYAN); latch.countDown(); });
            });
            axisExecutor.submit(() -> {
                ArrayList<Entry> entries = new ArrayList<>(list.size());
                for (AccelData d : list) entries.add(new Entry(d.timestamp - wStart, d.accelY));
                runOnUiThread(() -> { setData(lineChartAccelY, entries, "Y", Color.GREEN); latch.countDown(); });
            });
            axisExecutor.submit(() -> {
                ArrayList<Entry> entries = new ArrayList<>(list.size());
                for (AccelData d : list) entries.add(new Entry(d.timestamp - wStart, d.accelZ));
                runOnUiThread(() -> { setData(lineChartAccelZ, entries, "Z", Color.YELLOW); latch.countDown(); });
            });

            try {
                if (!latch.await(3, TimeUnit.SECONDS)) {
                    android.util.Log.e("AccelActivity", "GRAPH_LOAD: accel gen=" + gen + " axis timeout — possible deadlock, abandoning render");
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (renderGeneration.get() == gen) {
                runOnUiThread(() -> {
                    pinViewport(lineChartAccelX, lineChartAccelY, lineChartAccelZ);
                    android.util.Log.i("AccelActivity", "GRAPH_LOAD: end accel gen=" + gen + " rendered=" + list.size() + " points");
                });
            }
        });
    }

    private void pinViewport(LineChart... charts) {
        for (LineChart chart : charts) {
            if (chart.getData() == null) continue;
            chart.fitScreen();
        }
    }

    private void checkVoiceFilterIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        int minutes = intent.getIntExtra(VoiceCommandExecutor.EXTRA_FILTER_MINUTES, 0);
        if (minutes > 0) {
            applyTimeFilter(minutes);
            // Remove the extra so it is not reapplied on configuration changes
            intent.removeExtra(VoiceCommandExecutor.EXTRA_FILTER_MINUTES);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSlidingWindow();
        chartExecutor.shutdown();
        axisExecutor.shutdown();
    }

    @Override
    public void applyTimeFilter(int minutes) {
        if (minutes <= 0) {
            clearFilter();
            return;
        }
        stopSlidingWindow();
        long now = System.currentTimeMillis();
        dateFromCalendar.setTimeInMillis(now - ((long) minutes * 60 * 1000));
        dateToCalendar.setTimeInMillis(now);
        isTenMinuteFilterActive = true;
        updateChartsWithDateFilter();
        startSlidingWindow();
    }

    @Override
    public void clearFilter() {
        stopSlidingWindow();
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();
        windowStart = 0;
        updateChartsWithDateFilter();
    }

    @Override
    public boolean isFilterActive() {
        return isTenMinuteFilterActive;
    }
}
