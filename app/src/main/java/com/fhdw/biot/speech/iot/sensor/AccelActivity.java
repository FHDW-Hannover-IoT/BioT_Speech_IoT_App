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
import database.entities.AccelData;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * AccelActivity ------------- Screen that visualizes accelerometer data in three separate line
 * charts (X, Y, Z axes) and lets the user filter by date range or "last 10 minutes".
 *
 * <p>Responsibilities: - Navigation between sensor screens and main screen. - Fetching accel data
 * from Room via DB.sensorDao(). - Applying time filters via date pickers. - Mapping DB entities →
 * MPAndroidChart entries. - Delegating chart styling to BaseChartActivity.
 */
public class AccelActivity extends BaseChartActivity {

    /** Individual charts for each axis of the accelerometer. */
    private LineChart lineChartAccelX, lineChartAccelY, lineChartAccelZ;

    /** Optional: start time reference (currently unused but kept for extensions). */
    private long startTime = 0;

    /** Selected date range for filtering. */
    private Calendar dateFromCalendar;

    private Calendar dateToCalendar;

    /** Date filter buttons ("von" / "bis" for X, Y, Z charts). */
    private Button xVonButton, xBisButton;

    /** Quick filter button: show only last 10 minutes. */
    private Button btnFilterLast10Min;

    private LiveData<List<AccelData>> currentLiveData;

    private Handler slidingWindowHandler = new Handler(Looper.getMainLooper());
    private Runnable slidingWindowRunnable;
    private boolean isTenMinuteFilterActive = false;
    private boolean isStartPointFixed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beschleunigung);

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

        // Helper for hooking DatePickers to buttons (used below).
        DatePickerHandler datePickerHandler = new DatePickerHandler(AccelActivity.this);

        // --------------------------------------------------------------------
        // Date filter buttons
        // --------------------------------------------------------------------

        xBisButton = findViewById(R.id.button_x_bis);
        xVonButton = findViewById(R.id.button_x_von);

        // Quick filter: show last 10 minutes worth of accel data.
        btnFilterLast10Min = findViewById(R.id.btn_x_10min);
        btnFilterLast10Min.setOnClickListener(view -> toggleTenMinutesFilter());

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
                    xBisButton.setText("");
                    xVonButton.setText("");
                });

        // Initial "empty" chart configuration; actual data will come from DB.
        setupChart(lineChartAccelX, "X-Achse", 0);
        setupChart(lineChartAccelY, "Y-Achse", 0);
        setupChart(lineChartAccelZ, "Z-Achse", 0);

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
        // Initialize Calendar objects with "now".
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        LiveData<Long> oldestTimestampLiveData =
                DB.getDatabase(getApplicationContext()).sensorDao().getOldestAccelTimestamp();

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

    /** Wires up DatePickers for all "von" buttons and sets their initial text. */
    private void setupFromDatePickers(Button xVonButton) {
        DatePickerHandler.createForButton(
                xVonButton,
                calendar -> {
                    // Update lower bound of filter and refresh charts.
                    stopSlidingWindow();
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);

        // Show the initial "from" date on all three buttons.
        xVonButton.setText(formatCalendarDate(dateFromCalendar));
    }

    /** Wires up DatePickers for all "bis" buttons and sets their initial text. */
    private void setupToDatePickers(Button xBisButton) {
        DatePickerHandler.createForButton(
                xBisButton,
                calendar -> {
                    stopSlidingWindow();
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);

        // Show the initial "to" date (today) on all three buttons.
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

        currentLiveData =
                DB.getDatabase(getApplicationContext())
                        .sensorDao()
                        .getAccelDataBetween(fromTime, toTime);

        currentLiveData.observe(
                this,
                filteredData -> {
                    if (filteredData != null && !filteredData.isEmpty()) {
                        long firstTimestamp = filteredData.get(0).timestamp;

                        // Use earliest row in range as X-axis start.
                        setupChart(lineChartAccelX, "X-Achse", firstTimestamp);
                        setupChart(lineChartAccelY, "Y-Achse", firstTimestamp);
                        setupChart(lineChartAccelZ, "Z-Achse", firstTimestamp);

                        displayDataInCharts(filteredData);
                    } else {
                        // No data in this range → clear charts to avoid stale plots.
                        lineChartAccelX.clear();
                        lineChartAccelY.clear();
                        lineChartAccelZ.clear();
                    }
                });
    }

    /** Formats a Calendar into "dd.MM.yyyy" for showing in date filter buttons. */
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
    // Data → Chart mapping
    // ------------------------------------------------------------------------

    /**
     * Converts a list of AccelData entities into three sets of MPAndroidChart entries (X/Y/Z) and
     * renders them on the respective charts.
     *
     * <p>X-axis values are "elapsed milliseconds since first sample".
     */
    private void displayDataInCharts(List<AccelData> accelDataList) {
        if (accelDataList.isEmpty()) {
            return;
        }

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        long firstTimestamp = accelDataList.get(0).timestamp;

        for (AccelData data : accelDataList) {
            float elapsedTime = data.timestamp - firstTimestamp; // ms offset

            entriesX.add(new Entry(elapsedTime, data.accelX));
            entriesY.add(new Entry(elapsedTime, data.accelY));
            entriesZ.add(new Entry(elapsedTime, data.accelZ));
        }

        // Delegate dataset creation + chart styling to BaseChartActivity.
        setData(lineChartAccelX, entriesX, "X-Achse", Color.WHITE);
        setData(lineChartAccelY, entriesY, "Y-Achse", Color.WHITE);
        setData(lineChartAccelZ, entriesZ, "Z-Achse", Color.WHITE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSlidingWindow();
    }
}
