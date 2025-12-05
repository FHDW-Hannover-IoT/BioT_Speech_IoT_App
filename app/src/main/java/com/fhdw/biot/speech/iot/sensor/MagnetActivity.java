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
import database.entities.MagnetData;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

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
    private Button xVonButton, xBisButton, yVonButton, yBisButton, zVonButton, zBisButton;

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

        yBisButton = findViewById(R.id.button_y_bis);
        yVonButton = findViewById(R.id.button_y_von);

        zBisButton = findViewById(R.id.button_z_bis);
        zVonButton = findViewById(R.id.button_z_von);

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
                    xBisButton.setText("");
                    xVonButton.setText("");
                });

        // Reset Y-axis chart zoom/pan and clear corresponding date labels.
        ImageButton resetGyro = findViewById(R.id.resetY);
        resetGyro.setOnClickListener(
                view -> {
                    lineChartMagnetY.fitScreen();
                    yBisButton.setText("");
                    zVonButton.setText("");
                });

        // Reset Z-axis chart zoom/pan and clear its date labels.
        ImageButton resetMagnet = findViewById(R.id.resetZ);
        resetMagnet.setOnClickListener(
                view -> {
                    lineChartMagnetZ.fitScreen();
                    zBisButton.setText("");
                    zVonButton.setText("");
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

        // --------------------------------------------------------------------
        // LiveData observation: keep charts synced with DB content
        // --------------------------------------------------------------------
        // Observes all MagnetData rows in the database. Whenever the underlying
        // table changes, Room triggers this observer and the charts are
        // updated accordingly.
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAllMagnetData()
                .observe(
                        this,
                        magnetDataList -> {
                            if (magnetDataList != null && !magnetDataList.isEmpty()) {
                                // Use the first timestamp as reference for "elapsed time".
                                long firstTimestamp = magnetDataList.get(0).timestamp;

                                setupChart(lineChartMagnetX, "X-Achse", firstTimestamp);
                                setupChart(lineChartMagnetY, "Y-Achse", firstTimestamp);
                                setupChart(lineChartMagnetZ, "Z-Achse", firstTimestamp);

                                displayDataInCharts(magnetDataList);
                            }
                        });
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

        // Start with both ends = now. These will be updated below.
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        // Fetch the oldest magnetometer timestamp and use it as the initial "from" date.
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getOldestMagnetTimestamp()
                .observe(
                        this,
                        oldestTimestamp -> {
                            if (oldestTimestamp != null && oldestTimestamp > 0) {
                                dateFromCalendar.setTimeInMillis(oldestTimestamp);
                                // Set up "von" (from) buttons now that we have a baseline.
                                setupFromDatePickers(xVonButton, yVonButton, zVonButton);
                            }
                        });

        // "Bis" (to) buttons will use the current date as default.
        setupToDatePickers(xBisButton, yBisButton, zBisButton);
    }

    /**
     * Attaches DatePickers to the three "von" buttons and sets their initial text.
     *
     * <p>For each: - opens a calendar dialog, - updates {@link #dateFromCalendar}, - triggers
     * {@link #updateChartsWithDateFilter()}.
     */
    private void setupFromDatePickers(Button xVonButton, Button yVonButton, Button zVonButton) {
        DatePickerHandler.createForButton(
                xVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                MagnetActivity.this);

        DatePickerHandler.createForButton(
                yVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                MagnetActivity.this);

        DatePickerHandler.createForButton(
                zVonButton,
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                MagnetActivity.this);

        // Show initial "from" date on all axes.
        xVonButton.setText(formatCalendarDate(dateFromCalendar));
        yVonButton.setText(formatCalendarDate(dateFromCalendar));
        zVonButton.setText(formatCalendarDate(dateFromCalendar));
    }

    /**
     * Attaches DatePickers to the three "bis" buttons and sets their initial text to the current
     * value of {@link #dateToCalendar} (today).
     */
    private void setupToDatePickers(Button xBisButton, Button yBisButton, Button zBisButton) {
        DatePickerHandler.createForButton(
                xBisButton,
                calendar -> {
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                MagnetActivity.this);

        DatePickerHandler.createForButton(
                yBisButton,
                calendar -> {
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                MagnetActivity.this);

        DatePickerHandler.createForButton(
                zBisButton,
                calendar -> {
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                },
                MagnetActivity.this);

        // Initial "to" date is today for all axes.
        xBisButton.setText(formatCalendarDate(dateToCalendar));
        yBisButton.setText(formatCalendarDate(dateToCalendar));
        zBisButton.setText(formatCalendarDate(dateToCalendar));
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

        // We want to include the entire "to" day, so we set the upper bound
        // to 23:59:59 of the chosen date.
        Calendar adjustedToCalendar = (Calendar) dateToCalendar.clone();
        adjustedToCalendar.add(Calendar.DAY_OF_MONTH, 0);
        adjustedToCalendar.set(Calendar.HOUR_OF_DAY, 23);
        adjustedToCalendar.set(Calendar.MINUTE, 59);
        adjustedToCalendar.set(Calendar.SECOND, 59);

        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getMagnetDataBetween(
                        dateFromCalendar.getTimeInMillis(), adjustedToCalendar.getTimeInMillis())
                .observe(
                        this,
                        filteredData -> {
                            if (filteredData != null && !filteredData.isEmpty()) {
                                // Use first sample in filtered range as base timestamp.
                                long firstTimestamp = filteredData.get(0).timestamp;

                                setupChart(lineChartMagnetX, "X-Achse", firstTimestamp);
                                setupChart(lineChartMagnetY, "Y-Achse", firstTimestamp);
                                setupChart(lineChartMagnetZ, "Z-Achse", firstTimestamp);

                                displayDataInCharts(filteredData);
                            } else {
                                // No data in range → clear charts so old graphs are not misleading.
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
}
