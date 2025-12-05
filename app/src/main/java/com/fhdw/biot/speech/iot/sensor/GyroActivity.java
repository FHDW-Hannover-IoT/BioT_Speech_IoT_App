package com.fhdw.biot.speech.iot.sensor;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.BaseChartActivity;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.util.DatePickerHandler;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import database.DB;
import database.entities.GyroData;
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
public class GyroActivity extends BaseChartActivity {

    /** Individual charts for each gyroscope axis. */
    private LineChart lineChartGyroX, lineChartGyroY, lineChartGyroZ;

    /** Selected date range used when querying the database. */
    private Calendar dateFromCalendar;

    private Calendar dateToCalendar;

    /** Buttons that display and modify the filter range ("von" / "bis" per axis). */
    private Button xVonButton, xBisButton, yVonButton, yBisButton, zVonButton, zBisButton;

    /** Optional reference start time (not strictly needed, kept for future use). */
    private long startTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gyroskop);

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

        // Date picker helper (used below when wiring up the buttons).
        DatePickerHandler datePickerHandler = new DatePickerHandler(GyroActivity.this);

        // --------------------------------------------------------------------
        // Date filter buttons
        // --------------------------------------------------------------------
        xBisButton = findViewById(R.id.button_x_bis);
        xVonButton = findViewById(R.id.button_x_von);

        yBisButton = findViewById(R.id.button_y_bis);
        yVonButton = findViewById(R.id.button_y_von);

        zBisButton = findViewById(R.id.button_z_bis);
        zVonButton = findViewById(R.id.button_z_von);

        // --------------------------------------------------------------------
        // Reset buttons for each axis chart
        // --------------------------------------------------------------------

        // Reset X-axis chart zoom/pan and clear its date labels.
        ImageButton resetAccel = findViewById(R.id.resetX);
        resetAccel.setOnClickListener(
                view -> {
                    lineChartGyroX.fitScreen();
                    xBisButton.setText("");
                    xVonButton.setText("");
                });

        // Reset Y-axis chart zoom/pan and clear date labels.
        ImageButton resetGyro = findViewById(R.id.resetY);
        resetGyro.setOnClickListener(
                view -> {
                    lineChartGyroY.fitScreen();
                    yBisButton.setText("");
                    zVonButton.setText("");
                });

        // Reset Z-axis chart zoom/pan and clear date labels.
        ImageButton resetMagnet = findViewById(R.id.resetZ);
        resetMagnet.setOnClickListener(
                view -> {
                    lineChartGyroZ.fitScreen();
                    zBisButton.setText("");
                    zVonButton.setText("");
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

        // Configure and attach the date pickers (from/to).
        setupDatePickers();

        // --------------------------------------------------------------------
        // LiveData observation: keep charts in sync with DB
        // --------------------------------------------------------------------
        // We subscribe to all gyro samples in the DB. Whenever the underlying
        // Room table changes, this observer is called again and the charts
        // are updated automatically.
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAllGyroData()
                .observe(
                        this,
                        gyroDataList -> {
                            if (gyroDataList != null && !gyroDataList.isEmpty()) {
                                // Use first timestamp as base for "elapsed time" X-values.
                                long firstTimestamp = gyroDataList.get(0).timestamp;

                                setupChart(lineChartGyroX, "X-Achse", firstTimestamp);
                                setupChart(lineChartGyroY, "Y-Achse", firstTimestamp);
                                setupChart(lineChartGyroZ, "Z-Achse", firstTimestamp);

                                displayDataInCharts(gyroDataList);
                            }
                        });
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

        // Start with "now" for both ends of the range.
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        // Read the oldest gyro timestamp from DB and use it as initial "from" date.
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getOldestGyroTimestamp()
                .observe(
                        this,
                        oldestTimestamp -> {
                            if (oldestTimestamp != null && oldestTimestamp > 0) {
                                dateFromCalendar.setTimeInMillis(oldestTimestamp);
                                // Wire up "von" (from) buttons now that we have the initial value.
                                setupFromDatePickers(xVonButton, yVonButton, zVonButton);
                            }
                        });

        // Wire up "bis" (to) buttons, defaulting to today.
        setupToDatePickers(xBisButton, yBisButton, zBisButton);
    }

    /**
     * Attaches DatePickers to the three "von" buttons and sets their texts to the current value of
     * {@link #dateFromCalendar}.
     *
     * <p>For each button: - opens a calendar dialog, - updates {@link #dateFromCalendar}, - calls
     * {@link #updateChartsWithDateFilter()} so the data refreshes.
     */
    private void setupFromDatePickers(Button xVonButton, Button yVonButton, Button zVonButton) {
        DatePickerHandler.createForButton(
                xVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                GyroActivity.this);

        DatePickerHandler.createForButton(
                yVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                GyroActivity.this);

        DatePickerHandler.createForButton(
                zVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                GyroActivity.this);

        // Show initial "from" date on all three axes.
        xVonButton.setText(formatCalendarDate(dateFromCalendar));
        yVonButton.setText(formatCalendarDate(dateFromCalendar));
        zVonButton.setText(formatCalendarDate(dateFromCalendar));
    }

    /**
     * Attaches DatePickers to the three "bis" buttons and sets their texts to the current value of
     * {@link #dateToCalendar} (initially today).
     *
     * <p>Similar to {@link #setupFromDatePickers}, but updates the upper bound.
     */
    private void setupToDatePickers(Button xBisButton, Button yBisButton, Button zBisButton) {
        DatePickerHandler.createForButton(
                xBisButton,
                calendar -> {
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                GyroActivity.this);

        DatePickerHandler.createForButton(
                yBisButton,
                calendar -> {
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                GyroActivity.this);

        DatePickerHandler.createForButton(
                zBisButton,
                calendar -> {
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                GyroActivity.this);

        // Initial "to" date is the current day for all axes.
        xBisButton.setText(formatCalendarDate(dateToCalendar));
        yBisButton.setText(formatCalendarDate(dateToCalendar));
        zBisButton.setText(formatCalendarDate(dateToCalendar));
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

        // We want the full "to" day to be included. So we adjust the
        // upper bound to 23:59:59 of the chosen day.
        Calendar adjustedToCalendar = (Calendar) dateToCalendar.clone();
        adjustedToCalendar.add(Calendar.DAY_OF_MONTH, 0);
        adjustedToCalendar.set(Calendar.HOUR_OF_DAY, 23);
        adjustedToCalendar.set(Calendar.MINUTE, 59);
        adjustedToCalendar.set(Calendar.SECOND, 59);

        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getGyroDataBetween(
                        dateFromCalendar.getTimeInMillis(), adjustedToCalendar.getTimeInMillis())
                .observe(
                        this,
                        filteredData -> {
                            if (filteredData != null && !filteredData.isEmpty()) {
                                // Use first value in the filtered range as time reference.
                                long firstTimestamp = filteredData.get(0).timestamp;

                                setupChart(lineChartGyroX, "X-Achse", firstTimestamp);
                                setupChart(lineChartGyroY, "Y-Achse", firstTimestamp);
                                setupChart(lineChartGyroZ, "Z-Achse", firstTimestamp);

                                displayDataInCharts(filteredData);
                            } else {
                                // No data in the selected time window → clear
                                // charts so old data is not displayed.
                                lineChartGyroX.clear();
                                lineChartGyroY.clear();
                                lineChartGyroZ.clear();
                            }
                        });
    }

    /** Formats the given Calendar as "dd.MM.yyyy" for button labels. */
    private String formatCalendarDate(Calendar calendar) {
        return String.format(
                java.util.Locale.GERMANY,
                "%02d.%02d.%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR));
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
        if (gyroDataList == null || gyroDataList.isEmpty()) {
            return;
        }

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        // Reference timestamp; all other samples are relative to this.
        long firstTimestamp = gyroDataList.get(0).timestamp;

        // Convert each DB row into chart entries.
        for (GyroData data : gyroDataList) {
            float elapsedTime = data.timestamp - firstTimestamp; // milliseconds since first sample

            entriesX.add(new Entry(elapsedTime, data.gyroX));
            entriesY.add(new Entry(elapsedTime, data.gyroY));
            entriesZ.add(new Entry(elapsedTime, data.gyroZ));
        }

        // Delegate actual dataset creation + styling to BaseChartActivity.
        setData(lineChartGyroX, entriesX, "X-Achse", Color.WHITE);
        setData(lineChartGyroY, entriesY, "Y-Achse", Color.WHITE);
        setData(lineChartGyroZ, entriesZ, "Z-Achse", Color.WHITE);
    }
}
