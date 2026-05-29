package com.fhdw.biot.speech.iot.sensor;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.BaseChartActivity;
import com.fhdw.biot.speech.iot.graph.DouglasPeukerAlg;
import com.fhdw.biot.speech.iot.graph.EpsilonCalculator;
import com.fhdw.biot.speech.iot.graph.IFilterableChart;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.repository.SensorRepository;
import com.fhdw.biot.speech.iot.util.DateTimePickerHandler;
import com.fhdw.biot.speech.iot.voice.VoiceCommandExecutor;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * GyroActivity ------------ Screen that visualizes gyroscope sensor data in three separate line
 * charts (X, Y, Z axes) and lets the user filter the visible range by date.
 *
 * <p>Responsibilities: - Navigation between sensor screens (Accel / Magnet) and main screen. -
 * Connecting to Room (DB.sensorDao()) to load stored GyroData. - Handling from/to date selection
 * via DatePickerHandler. - Mapping GyroData → MPAndroidChart entries and rendering them. - Using
 * BaseChartActivity for common chart styling/behaviour.
 */
public class GyroActivity extends BaseChartActivity implements IFilterableChart {

    /** Individual charts for each gyroscope axis. */
    private LineChart lineChartGyroX, lineChartGyroY, lineChartGyroZ;

    /** Selected date range used when querying the database. */
    private Calendar dateFromCalendar;

    private Calendar dateToCalendar;

    /** Buttons that display and modify the filter range ("von" / "bis" per axis). */
    private Button xVonButton, xBisButton;

    /** Optional reference start time (not strictly needed, kept for future use). */
    private long startTime = 0;

    private Handler slidingWindowHandler = new Handler(Looper.getMainLooper());
    private Runnable slidingWindowRunnable;
    private boolean isTenMinuteFilterActive = false;

    private Spinner spinnerTimeframe;
    private int selectedMinutes = 10;

    private CheckBox cbChartX, cbChartY, cbChartZ;

    private SensorRepository sensorRepository;
    private LiveData<List<GyroData>> currentLiveData;

    private long windowStart = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gyroskop);
        sensorRepository = ((BiotApplication) getApplication()).getContainer().sensorRepository();

        // When McpDataSyncService delivers fetched history, anchor the sliding window to
        // the latest data timestamp. Skipped if the user manually picked a date range.
        ((BiotApplication) getApplication()).getContainer().mcpDataSync()
                .gyroHistory()
                .observe(this, fetched -> {
                    if (fetched == null || fetched.isEmpty()) return;
                    if (!isTenMinuteFilterActive) return;
                    long latestTs = fetched.get(fetched.size() - 1).timestamp;
                    long windowMs = (long) selectedMinutes * 60_000L;
                    dateFromCalendar.setTimeInMillis(latestTs - windowMs);
                    dateToCalendar.setTimeInMillis(latestTs);
                    android.util.Log.i("GyroActivity", "GRAPH_UI: gyroHistory fired rows=" + fetched.size() + " latestTs=" + latestTs + " window=" + selectedMinutes + "min");
                    updateChartsWithDateFilter();
                });

        // --------------------------------------------------------------------
        // Window insets handling (status bar / navigation bar)
        // --------------------------------------------------------------------
        // Ensures that the root view is padded so content is not drawn under
        // system bars when using edge-to-edge layouts.
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.gyro),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        // --------------------------------------------------------------------
        // Navigation buttons
        // --------------------------------------------------------------------

        // Move to previous sensor screen: accelerometer charts.
        Button buttonAccel = findViewById(R.id.btnPrevAccel);
        buttonAccel.setOnClickListener(
                view -> {
                    Intent intent = new Intent(GyroActivity.this, AccelActivity.class);
                    startActivity(intent);
                });

        // Move to next sensor screen: magnetometer charts.
        Button buttonMagnet = findViewById(R.id.btnNextMagnet);
        buttonMagnet.setOnClickListener(
                view -> {
                    Intent intent = new Intent(GyroActivity.this, MagnetActivity.class);
                    startActivity(intent);
                });

        // Home button: return to the main values / MQTT screen.
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(GyroActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        // Ereignis button: open list of stored events, pre-filtered to GYRO events.
        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(GyroActivity.this, EreignisActivity.class);
                    intent.putExtra("SENSOR_FILTER", "GYRO");
                    startActivity(intent);
                });

        // --------------------------------------------------------------------
        // Chart references
        // --------------------------------------------------------------------
        lineChartGyroX = findViewById(R.id.lineChartGyroX);
        lineChartGyroY = findViewById(R.id.lineChartGyroY);
        lineChartGyroZ = findViewById(R.id.lineChartGyroZ);

        // --------------------------------------------------------------------
        // Date filter buttons
        // --------------------------------------------------------------------
        xBisButton = findViewById(R.id.button_x_bis);
        xVonButton = findViewById(R.id.button_x_von);

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
        // Reset buttons for each axis chart
        // --------------------------------------------------------------------

        // Reset X-axis chart zoom/pan and clear its date labels.
        ImageButton resetAccel = findViewById(R.id.resetX);
        resetAccel.setOnClickListener(
                view -> {
                    lineChartGyroX.fitScreen();
                    lineChartGyroY.fitScreen();
                    lineChartGyroZ.fitScreen();
                    xBisButton.setText("");
                    xVonButton.setText("");
                });

        // --------------------------------------------------------------------
        // Initial chart setup (before any data is loaded)
        // --------------------------------------------------------------------
        // At this point startTime is 0, so the X-axis formatter will not yet
        // convert to "seconds since first sample". This is updated once real
        // data is loaded and we know the earliest timestamp.
        setupChart(lineChartGyroX, "X-Achse", 0);
        setupChart(lineChartGyroY, "Y-Achse", 0);
        setupChart(lineChartGyroZ, "Z-Achse", 0);

        cbChartX = findViewById(R.id.cbChartX);
        cbChartY = findViewById(R.id.cbChartY);
        cbChartZ = findViewById(R.id.cbChartZ);
        if (cbChartX != null)
            cbChartX.setOnCheckedChangeListener(
                    (b, c) -> lineChartGyroX.setVisibility(c ? View.VISIBLE : View.GONE));
        if (cbChartY != null)
            cbChartY.setOnCheckedChangeListener(
                    (b, c) -> lineChartGyroY.setVisibility(c ? View.VISIBLE : View.GONE));
        if (cbChartZ != null)
            cbChartZ.setOnCheckedChangeListener(
                    (b, c) -> lineChartGyroZ.setVisibility(c ? View.VISIBLE : View.GONE));

        setupDatePickers();
    }

    // ------------------------------------------------------------------------
    // Date picker configuration
    // ------------------------------------------------------------------------

    /**
     * Initializes date range state and wires up DatePickers to the buttons.
     *
     * <p>Behaviour: - "from" date initially = oldest stored gyro sample in the database. - "to"
     * date initially = today. - whenever the user picks a date, charts are re-filtered.
     */
    private void setupDatePickers() {
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        setupFromDatePickers(xVonButton);
        setupToDatePickers(xBisButton);

        isTenMinuteFilterActive = true;
        startSlidingWindow();
        checkVoiceFilterIntent();
    }

    /**
     * Attaches DatePickers to the three "von" buttons and sets their texts to the current value of
     * {@link #dateFromCalendar}.
     *
     * <p>For each button: - opens a calendar dialog, - updates {@link #dateFromCalendar}, - calls
     * {@link #updateChartsWithDateFilter()} so the data refreshes.
     */
    private void setupFromDatePickers(Button xVonButton) {
        DateTimePickerHandler.createForButton(
                xVonButton,
                calendar -> {
                    stopSlidingWindow();
                    dateFromCalendar = calendar;
                    windowStart = 0;
                    updateChartsWithDateFilter();
                },
                GyroActivity.this);
        xVonButton.setText(makeDateTimeString(dateFromCalendar));
    }

    private void setupToDatePickers(Button xBisButton) {
        DateTimePickerHandler.createForButton(
                xBisButton,
                calendar -> {
                    stopSlidingWindow();
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                GyroActivity.this);
        xBisButton.setText(makeDateTimeString(dateToCalendar));
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

                        syncDateButtonTexts();
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

    /** Updates all six date filter buttons to match the current from/to calendars. */
    private void syncDateButtonTexts() {
        xVonButton.setText(makeDateTimeString(dateFromCalendar));

        // "Bis"-Buttons
        xBisButton.setText(makeDateTimeString(dateToCalendar));
    }

    // ------------------------------------------------------------------------
    // Filtering + updating charts
    // ------------------------------------------------------------------------

    /**
     * Re-queries the database with the currently selected [from, to] date range and updates all
     * three gyroscope charts with the result.
     *
     * <p>If there are no values in the selected range, the charts are cleared.
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

        currentLiveData = sensorRepository.getGyroBetween(fromTime, toTime);

        currentLiveData.observe(
                this,
                filteredData -> {
                    if (filteredData != null && !filteredData.isEmpty()) {
                        long firstTimestamp = filteredData.get(0).timestamp;

                        // Use earliest row in range as X-axis start.
                        setupChart(lineChartGyroX, "X-Achse", firstTimestamp);
                        setupChart(lineChartGyroY, "Y-Achse", firstTimestamp);
                        setupChart(lineChartGyroZ, "Z-Achse", firstTimestamp);

                        displayDataInCharts(filteredData);
                    } else {
                        // No data in this range → clear charts to avoid stale plots.
                        lineChartGyroX.clear();
                        lineChartGyroY.clear();
                        lineChartGyroZ.clear();
                    }
                });
    }

    private String makeDateTimeString(Calendar calendar) {
        return DateTimePickerHandler.format(calendar);
    }

    // ------------------------------------------------------------------------
    // Data → chart mapping
    // ------------------------------------------------------------------------

    /**
     * Converts a list of {@link GyroData} rows into three separate MPAndroidChart datasets (X, Y, Z
     * axes) and renders them on the charts.
     *
     * <p>X-axis values are "elapsed milliseconds since firstTimestamp", so the charts show
     * time-relative data instead of absolute timestamps.
     */
    private void displayDataInCharts(List<GyroData> gyroDataList) {
        if (gyroDataList == null || gyroDataList.isEmpty()) return;

        if (windowStart == 0) windowStart = gyroDataList.get(0).timestamp;

        long durationMs = (long) selectedMinutes * 60_000L;
        float epsilon = EpsilonCalculator.calculateScaledEpsilon(this, gyroDataList, durationMs);
        List<GyroData> dataToRender = DouglasPeukerAlg.simplify(gyroDataList, epsilon);

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        for (GyroData data : dataToRender) {
            float t = data.timestamp - windowStart;
            entriesX.add(new Entry(t, data.gyroX));
            entriesY.add(new Entry(t, data.gyroY));
            entriesZ.add(new Entry(t, data.gyroZ));
        }

        setData(lineChartGyroX, entriesX, "X", Color.CYAN);
        setData(lineChartGyroY, entriesY, "Y", Color.GREEN);
        setData(lineChartGyroZ, entriesZ, "Z", Color.YELLOW);

        pinViewport(lineChartGyroX, lineChartGyroY, lineChartGyroZ);
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
            intent.removeExtra(VoiceCommandExecutor.EXTRA_FILTER_MINUTES);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSlidingWindow();
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
        syncDateButtonTexts();
        updateChartsWithDateFilter();
        startSlidingWindow();
    }

    @Override
    public void clearFilter() {
        stopSlidingWindow();
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();
        windowStart = 0;
        syncDateButtonTexts();
        updateChartsWithDateFilter();
    }

    @Override
    public boolean isFilterActive() {
        return isTenMinuteFilterActive;
    }
}
