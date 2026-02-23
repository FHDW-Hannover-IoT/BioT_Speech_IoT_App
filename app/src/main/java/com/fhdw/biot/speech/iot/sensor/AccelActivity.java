package com.fhdw.biot.speech.iot.sensor;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
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
    private Button xVonButton, xBisButton, yVonButton, yBisButton, zVonButton, zBisButton;

    /** Quick filter button: show only last 10 minutes. */
    private Button btnFilterLast10Min;

    private LiveData<List<AccelData>> currentLiveData;

    private Handler slidingWindowHandler = new Handler(Looper.getMainLooper());
    private Runnable slidingWindowRunnable;
    private boolean isTenMinuteFilterActive = false;

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

        yBisButton = findViewById(R.id.button_y_bis);
        yVonButton = findViewById(R.id.button_y_von);

        zBisButton = findViewById(R.id.button_z_bis);
        zVonButton = findViewById(R.id.button_z_von);

        // Quick filter: show last 10 minutes worth of accel data.
        btnFilterLast10Min = findViewById(R.id.btn_x_10min);
        btnFilterLast10Min.setOnClickListener(view -> filterLastTenMinutes());

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
                    xBisButton.setText("");
                    xVonButton.setText("");
                });

        ImageButton resetGyro = findViewById(R.id.resetY);
        resetGyro.setOnClickListener(
                view -> {
                    lineChartAccelY.fitScreen();
                    yBisButton.setText("");
                    yVonButton.setText("");
                });

        ImageButton resetMagnet = findViewById(R.id.resetZ);
        resetMagnet.setOnClickListener(
                view -> {
                    lineChartAccelZ.fitScreen();
                    zBisButton.setText("");
                    zVonButton.setText("");
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

        // Observe the oldest timestamp in the DB and use it as initial "from" date.
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getOldestAccelTimestamp()
                .observe(
                        this,
                        oldestTimestamp -> {
                            if (oldestTimestamp != null && oldestTimestamp > 0) {
                                dateFromCalendar.setTimeInMillis(oldestTimestamp);
                                // Attach DatePickers to "von" buttons using this default.
                                setupFromDatePickers(xVonButton, yVonButton, zVonButton);
                            }
                        });

        // Attach DatePickers to "bis" buttons (initially today).
        setupToDatePickers(xBisButton, yBisButton, zBisButton);
        filterLastTenMinutes();
    }

    /** Wires up DatePickers for all "von" buttons and sets their initial text. */
    private void setupFromDatePickers(Button xVonButton, Button yVonButton, Button zVonButton) {
        DatePickerHandler.createForButton(
                xVonButton,
                calendar -> {
                    // Update lower bound of filter and refresh charts.
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);

        DatePickerHandler.createForButton(
                yVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);

        DatePickerHandler.createForButton(
                zVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);

        // Show the initial "from" date on all three buttons.
        xVonButton.setText(formatCalendarDate(dateFromCalendar));
        yVonButton.setText(formatCalendarDate(dateFromCalendar));
        zVonButton.setText(formatCalendarDate(dateFromCalendar));
    }

    /** Wires up DatePickers for all "bis" buttons and sets their initial text. */
    private void setupToDatePickers(Button xBisButton, Button yBisButton, Button zBisButton) {
        DatePickerHandler.createForButton(
                xBisButton,
                calendar -> {
                    stopSlidingWindow();
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);

        DatePickerHandler.createForButton(
                yBisButton,
                calendar -> {
                    stopSlidingWindow();
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);

        DatePickerHandler.createForButton(
                zBisButton,
                calendar -> {
                    stopSlidingWindow();
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);

        // Show the initial "to" date (today) on all three buttons.
        xBisButton.setText(formatCalendarDate(dateToCalendar));
        yBisButton.setText(formatCalendarDate(dateToCalendar));
        zBisButton.setText(formatCalendarDate(dateToCalendar));
    }

    /**
     * Convenience filter: sets the date range to "now minus 10 minutes" to "now", refreshes the
     * charts immediately and updates it every 5 seconds.
     */
    private void filterLastTenMinutes() {
        isTenMinuteFilterActive = true;
        slidingWindowHandler.removeCallbacks(slidingWindowRunnable);
        slidingWindowRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        if (!isTenMinuteFilterActive) return;

                        long now = System.currentTimeMillis();
                        long tenMinutesAgo = now - (10 * 60 * 1000);

                        dateToCalendar.setTimeInMillis(now);
                        dateFromCalendar.setTimeInMillis(tenMinutesAgo);
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
        yVonButton.setText(makeDateTimeString(dateFromCalendar));
        zVonButton.setText(makeDateTimeString(dateFromCalendar));

        // "Bis"-Buttons
        xBisButton.setText(makeDateTimeString(dateToCalendar));
        yBisButton.setText(makeDateTimeString(dateToCalendar));
        zBisButton.setText(makeDateTimeString(dateToCalendar));
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

        // Extend "bis" to the end of the chosen day (23:59:59), so full day is included.
        Calendar adjustedToCalendar = (Calendar) dateToCalendar.clone();
        adjustedToCalendar.add(Calendar.DAY_OF_MONTH, 0);
        adjustedToCalendar.set(Calendar.HOUR_OF_DAY, 23);
        adjustedToCalendar.set(Calendar.MINUTE, 59);
        adjustedToCalendar.set(Calendar.SECOND, 59);

        currentLiveData =
                DB.getDatabase(getApplicationContext())
                        .sensorDao()
                        .getAccelDataBetween(
                                dateFromCalendar.getTimeInMillis(),
                                adjustedToCalendar.getTimeInMillis());

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
