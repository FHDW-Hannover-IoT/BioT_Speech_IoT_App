package com.fhdw.biot.speech.iot.sensor;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.BaseChartActivity;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.util.DatePickerHandler;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import database.DB;
import database.entities.MagnetData;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * MagnetActivity -------------- Screen that visualizes magnetometer sensor data in three separate
 * line charts (X, Y, Z axes) and lets the user filter the visible range by date.
 *
 * <p>Responsibilities: - Navigation between other sensor screens (Gyro / Accel) and the main
 * screen. - Fetching magnetometer data from the Room database. - Providing date-range filtering via
 * DatePickerHandler. - Mapping MagnetData rows into MPAndroidChart entries and rendering them. -
 * Reusing BaseChartActivity for common chart styling / behaviour.
 */
public class MagnetActivity extends BaseChartActivity {

    /** Individual charts for X, Y and Z axis values of the magnetometer. */
    private LineChart lineChartMagnetX, lineChartMagnetY, lineChartMagnetZ;

    /** Selected date range used when querying the database. */
    private Calendar dateFromCalendar;

    private Calendar dateToCalendar;

    /** Buttons used to show / pick "from" and "to" dates for each axis. */
    private Button xVonButton, xBisButton;

    /** Quick filter button: show only last 10 minutes. */
    private Button btnFilterLast10Min;

    private LiveData<List<MagnetData>> currentLiveData;

    private Handler slidingWindowHandler = new Handler(Looper.getMainLooper());
    private Runnable slidingWindowRunnable;
    private boolean isTenMinuteFilterActive = false;

    private boolean isStartPointFixed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_magnetfeld);

        // --------------------------------------------------------------------
        // Window insets handling (edge-to-edge UI + system bars)
        // --------------------------------------------------------------------
        // Ensures that the root view is padded so content doesn't sit under
        // the status / navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.magnet),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        // --------------------------------------------------------------------
        // Navigation buttons
        // --------------------------------------------------------------------

        // Go back to the Gyro screen (previous sensor).
        Button buttonGyro = findViewById(R.id.btnPrevGyro);
        buttonGyro.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MagnetActivity.this, GyroActivity.class);
                    startActivity(intent);
                });

        // Go forward to the Accel screen (next sensor).
        Button buttonAccel = findViewById(R.id.btnNextAccel);
        buttonAccel.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MagnetActivity.this, AccelActivity.class);
                    startActivity(intent);
                });

        // Home button: go back to main MQTT / values screen.
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MagnetActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        // Ereignis button: open event list, filtered to magnetometer events.
        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MagnetActivity.this, EreignisActivity.class);
                    intent.putExtra("SENSOR_FILTER", "MAGNET");
                    startActivity(intent);
                });

        // Date picker helper (used to wire up the date buttons).
        DatePickerHandler datePickerHandler = new DatePickerHandler(MagnetActivity.this);

        // --------------------------------------------------------------------
        // Date range buttons
        // --------------------------------------------------------------------
        xBisButton = findViewById(R.id.button_x_bis);
        xVonButton = findViewById(R.id.button_x_von);

        // Quick filter: show last 10 minutes worth of accel data.
        btnFilterLast10Min = findViewById(R.id.btn_x_10min);
        btnFilterLast10Min.setOnClickListener(view -> toggleTenMinutesFilter());

        // --------------------------------------------------------------------
        // Chart references
        // --------------------------------------------------------------------
        lineChartMagnetX = findViewById(R.id.lineChartMagnetX);
        lineChartMagnetY = findViewById(R.id.lineChartMagnetY);
        lineChartMagnetZ = findViewById(R.id.lineChartMagnetZ);

        // --------------------------------------------------------------------
        // Reset buttons for each axis
        // --------------------------------------------------------------------

        // Reset X-axis chart zoom/pan and clear the date labels for X.
        ImageButton resetAccel = findViewById(R.id.resetX);
        resetAccel.setOnClickListener(
                view -> {
                    lineChartMagnetX.fitScreen();
                    lineChartMagnetY.fitScreen();
                    lineChartMagnetZ.fitScreen();
                    xBisButton.setText("");
                    xVonButton.setText("");
                });

        // --------------------------------------------------------------------
        // Initial chart setup (before any data is loaded)
        // --------------------------------------------------------------------
        // startTime = 0 here, so X-axis will initially show raw values.
        // After we know the first timestamp from DB, we re-setup with that.
        setupChart(lineChartMagnetX, "X-Achse", 0);
        setupChart(lineChartMagnetY, "Y-Achse", 0);
        setupChart(lineChartMagnetZ, "Z-Achse", 0);

        // Configure date pickers for "from" and "to" range.
        setupDatePickers();
    }

    // ------------------------------------------------------------------------
    // Date picker setup
    // ------------------------------------------------------------------------

    /**
     * Initializes the date range and wires up the "von" / "bis" date pickers.
     *
     * <p>Behaviour: - "from" date initially set to the oldest magnetometer sample in DB. - "to"
     * date initially set to today. - On any date change, the charts are re-filtered.
     */
    private void setupDatePickers() {
        // Initialize Calendar objects with "now".
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        LiveData<Long> oldestTimestampLiveData =
                DB.getDatabase(getApplicationContext()).sensorDao().getOldestMagnetTimestamp();

        oldestTimestampLiveData.observe(
                this,
                new androidx.lifecycle.Observer<Long>() {
                    @Override
                    public void onChanged(Long oldestTimestamp) {
                        if (oldestTimestamp != null && oldestTimestamp > 0) {

                            if (!isTenMinuteFilterActive) {
                                dateFromCalendar.setTimeInMillis(oldestTimestamp);
                                syncDateButtonTexts();
                                updateChartsWithDateFilter();
                            }

                            oldestTimestampLiveData.removeObserver(this);
                        }
                    }
                });

        isTenMinuteFilterActive = true;
        isStartPointFixed = false;
        btnFilterLast10Min.setBackgroundColor(ContextCompat.getColor(this, R.color.button));
        startSlidingWindow();
    }

    /**
     * Attaches DatePickers to the three "von" buttons and sets their initial text.
     *
     * <p>For each: - opens a calendar dialog, - updates {@link #dateFromCalendar}, - triggers
     * {@link #updateChartsWithDateFilter()}.
     */
    private void setupFromDatePickers(Button xVonButton) {
        DatePickerHandler.createForButton(
                xVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                MagnetActivity.this);

        // Show initial "from" date on all axes.
        xVonButton.setText(formatCalendarDate(dateFromCalendar));
    }

    /**
     * Attaches DatePickers to the three "bis" buttons and sets their initial text to the current
     * value of {@link #dateToCalendar} (today).
     */
    private void setupToDatePickers(Button xBisButton) {
        DatePickerHandler.createForButton(
                xBisButton,
                calendar -> {
                    stopSlidingWindow();
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                MagnetActivity.this);

        // Initial "to" date is today for all axes.
        xBisButton.setText(formatCalendarDate(dateToCalendar));
    }

    /**
     * Convenience filter: sets the date range to "now minus 10 minutes" to "now", refreshes the
     * charts immediately and updates it every 5 seconds.
     */
    private void toggleTenMinutesFilter() {
        if (!isTenMinuteFilterActive) {
            isTenMinuteFilterActive = true;
            isStartPointFixed = false;
            btnFilterLast10Min.setBackgroundColor(ContextCompat.getColor(this, R.color.button));
            startSlidingWindow();

        } else if (!isStartPointFixed) {
            isStartPointFixed = true;
            btnFilterLast10Min.setBackgroundColor(ContextCompat.getColor(this, R.color.header));

        } else {
            isStartPointFixed = false;
            btnFilterLast10Min.setBackgroundColor(ContextCompat.getColor(this, R.color.button));

            long now = System.currentTimeMillis();
            dateFromCalendar.setTimeInMillis(now - (10 * 60 * 1000));
            syncDateButtonTexts();
            updateChartsWithDateFilter();
        }
    }

    private void startSlidingWindow() {
        slidingWindowHandler.removeCallbacks(slidingWindowRunnable);
        slidingWindowRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        if (!isTenMinuteFilterActive) return;

                        long now = System.currentTimeMillis();

                        if (!isStartPointFixed) {
                            long tenMinutesAgo = now - (10 * 60 * 1000);
                            dateFromCalendar.setTimeInMillis(tenMinutesAgo);
                        }

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
        isStartPointFixed = false;
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
     * Re-queries the database for the selected [from, to] date range and updates all three
     * magnetometer charts with the results.
     *
     * <p>If there is no data for the current range, all charts are cleared.
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

        currentLiveData =
                DB.getDatabase(getApplicationContext())
                        .sensorDao()
                        .getMagnetDataBetween(fromTime, toTime);

        currentLiveData.observe(
                this,
                filteredData -> {
                    if (filteredData != null && !filteredData.isEmpty()) {
                        long firstTimestamp = filteredData.get(0).timestamp;

                        // Use earliest row in range as X-axis start.
                        setupChart(lineChartMagnetX, "X-Achse", firstTimestamp);
                        setupChart(lineChartMagnetY, "Y-Achse", firstTimestamp);
                        setupChart(lineChartMagnetZ, "Z-Achse", firstTimestamp);

                        displayDataInCharts(filteredData);
                    } else {
                        // No data in this range → clear charts to avoid stale plots.
                        lineChartMagnetX.clear();
                        lineChartMagnetY.clear();
                        lineChartMagnetZ.clear();
                    }
                });
    }

    /** Formats a Calendar into "dd.MM.yyyy" for button labels. */
    private String formatCalendarDate(Calendar calendar) {
        return String.format(
                java.util.Locale.GERMANY,
                "%02d.%02d.%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR));
    }

    /** Formats a Calendar into "dd.MM.yyyy" (no time) – used for the quick-filter labels. */
    private String makeDateTimeString(Calendar calendar) {
        return String.format(
                Locale.GERMAN,
                "%02d.%02d.%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR));
    }

    // ------------------------------------------------------------------------
    // Data → chart mapping
    // ------------------------------------------------------------------------

    /**
     * Converts a list of {@link MagnetData} rows into three separate sets of MPAndroidChart entries
     * (X, Y, Z) and renders them.
     *
     * <p>X-axis values = "elapsed milliseconds since firstTimestamp", so the charts show
     * time-relative data instead of absolute wall-clock time.
     */
    private void displayDataInCharts(List<MagnetData> magnetDataList) {
        if (magnetDataList == null || magnetDataList.isEmpty()) {
            return;
        }

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        // Reference timestamp. All samples are plotted relative to this.
        long firstTimestamp = magnetDataList.get(0).timestamp;

        // Build entries for each axis based on elapsed time from the first sample.
        for (MagnetData data : magnetDataList) {
            float elapsedTime = data.timestamp - firstTimestamp; // ms since first sample
            entriesX.add(new Entry(elapsedTime, data.magnetX));
            entriesY.add(new Entry(elapsedTime, data.magnetY));
            entriesZ.add(new Entry(elapsedTime, data.magnetZ));
        }

        // Use BaseChartActivity helper to actually feed data into the charts.
        setData(lineChartMagnetX, entriesX, "X-Achse", Color.WHITE);
        setData(lineChartMagnetY, entriesY, "Y-Achse", Color.WHITE);
        setData(lineChartMagnetZ, entriesZ, "Z-Achse", Color.WHITE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSlidingWindow();
    }
}
