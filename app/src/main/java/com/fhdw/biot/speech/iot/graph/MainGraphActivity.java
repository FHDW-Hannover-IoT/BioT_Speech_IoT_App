package com.fhdw.biot.speech.iot.graph;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.sensor.AccelActivity;
import com.fhdw.biot.speech.iot.sensor.GyroActivity;
import com.fhdw.biot.speech.iot.sensor.MagnetActivity;
import com.fhdw.biot.speech.iot.settings.SettingsActivity;
import com.fhdw.biot.speech.iot.util.DatePickerHandler;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import database.DB;
import database.entities.AccelData;
import database.entities.GyroData;
import database.entities.MagnetData;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * MainGraphActivity ----------------- THIS IS THE MAIN VISUALIZATION SCREEN FOR THE ENTIRE APP.
 *
 * <p>PURPOSE OF THIS ACTIVITY: • Visualize all 3 sensors (Accel, Gyro, Magnet) at once. • Allow
 * users to toggle individual axes (X, Y, Z) and the total magnitude. • Allow dynamic filtering by
 * date range. • Automatically redraw the graphs anytime: - the database updates, - the user changes
 * the date range, - the user toggles checkboxes.
 *
 * <p>INTERNAL STRUCTURE: 1. Three LineCharts → Accel, Gyro, Magnet. 2. Each chart can display up to
 * 4 datasets: - X axis, Y axis, Z axis, and total magnitude (sqrt(x²+y²+z²)) 3. The Activity
 * obtains data from Room using LiveData so updates are automatic. 4. Date ranges are applied using
 * DB filters and LiveData re-queries. 5. The actual drawing logic comes from BaseChartActivity.
 *
 * <p>This Activity is essentially a "dashboard" combining multiple real-time sensor signals.
 */
public class MainGraphActivity extends BaseChartActivity {

    // -----------------------------------------------
    // CHARTS — one for each sensor category
    // -----------------------------------------------
    private LineChart lineChartAccel, lineChartGyro, lineChartMag;

    // -----------------------------------------------
    // DATE FILTER RANGE
    // -----------------------------------------------
    private Calendar dateFromCalendar;
    private Calendar dateToCalendar;

    private LiveData<List<AccelData>> currentAccelLiveData;
    private LiveData<List<GyroData>> currentGyroLiveData;
    private LiveData<List<MagnetData>> currentMagLiveData;

    // -----------------------------------------------
    // DATASETS FOR ALL AXES AND TOTAL MAGNITUDE
    // These objects hold the chart data before deciding
    // which datasets will be shown depending on checkboxes.
    // -----------------------------------------------
    private LineDataSet lineDataAccelx, lineDataAccely, lineDataAccelz, lineDataAccelTotal;
    private LineDataSet lineDataGyrox, lineDataGyroy, lineDataGyroz, lineDataGyroTotal;
    private LineDataSet lineDataMagx, lineDataMagy, lineDataMagz, lineDataMagTotal;

    // -----------------------------------------------
    // CHECKBOXES controlling which lines are visible
    // -----------------------------------------------
    private CheckBox AccelXCheck, AccelYCheck, AccelZCheck, AccelSumCheck;
    private CheckBox GyroXCheck, GyroYCheck, GyroZCheck, GyroSumCheck;
    private CheckBox MagXCheck, MagYCheck, MagZCheck, MagSumCheck;

    // Buttons for date pickers
    private Button xVonButton, xBisButton;

    private Button btnFilterLast10Min;

    private long startTime = 0; // initial timestamp for axis formatting (seconds)

    private Handler slidingWindowHandler = new Handler(Looper.getMainLooper());
    private Runnable slidingWindowRunnable;
    private boolean isTenMinuteFilterActive = false;
    private boolean isStartPointFixed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_graph);

        // ------------------------------------------------------------
        // SAFE INSETS (dynamic padding for status/navigation bars)
        // ------------------------------------------------------------
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets bar = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bar.left, bar.top, bar.right, bar.bottom);
                    return insets;
                });

        // ------------------------------------------------------------
        // NAVIGATION BUTTONS
        // ------------------------------------------------------------
        Button ButtonWerte = findViewById(R.id.werteansicht);
        ButtonWerte.setOnClickListener(
                v -> startActivity(new Intent(MainGraphActivity.this, MainActivity.class)));

        // Setup dynamic visibility controls (checkboxes)
        setupCheckboxes();

        // ------------------------------------------------------------
        // DATE PICKER BUTTON REFERENCES
        // ------------------------------------------------------------
        xVonButton = findViewById(R.id.button_Accel_von);
        xBisButton = findViewById(R.id.button_Accel_bis);

        // ------------------------------------------------------------
        // SENSOR NAVIGATION BUTTONS
        // ------------------------------------------------------------
        findViewById(R.id.btnGyro)
                .setOnClickListener(
                        v -> startActivity(new Intent(MainGraphActivity.this, GyroActivity.class)));

        findViewById(R.id.btnAccel)
                .setOnClickListener(
                        v ->
                                startActivity(
                                        new Intent(MainGraphActivity.this, AccelActivity.class)));

        findViewById(R.id.btnMagnet)
                .setOnClickListener(
                        v ->
                                startActivity(
                                        new Intent(MainGraphActivity.this, MagnetActivity.class)));

        // Ereignis view
        findViewById(R.id.notification_button)
                .setOnClickListener(
                        v -> {
                            Intent intent =
                                    new Intent(MainGraphActivity.this, EreignisActivity.class);
                            intent.putExtra("SENSOR_FILTER", "ALL");
                            startActivity(intent);
                        });

        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(
                view ->
                        startActivity(
                                new android.content.Intent(
                                        MainGraphActivity.this, SettingsActivity.class)));

        // Quick filter: show last 10 minutes worth of accel data.
        btnFilterLast10Min = findViewById(R.id.btn_x_10min);
        btnFilterLast10Min.setOnClickListener(view -> toggleTenMinutesFilter());

        // ------------------------------------------------------------
        // CHART REFERENCES
        // ------------------------------------------------------------
        lineChartAccel = findViewById(R.id.lineChartAccel);
        lineChartGyro = findViewById(R.id.lineChartGyro);
        lineChartMag = findViewById(R.id.lineChartMag);

        // Initial chart setup (no data yet)
        setupChart(lineChartAccel, "Beschleunigung", 0);
        setupChart(lineChartGyro, "Gyroskop", 0);
        setupChart(lineChartMag, "Magnetfeld", 0);

        // Reset buttons restore the zoom and clear date fields
        findViewById(R.id.resetAccel)
                .setOnClickListener(
                        v -> {
                            lineChartAccel.fitScreen();
                            lineChartGyro.fitScreen();
                            lineChartMag.fitScreen();
                            xVonButton.setText("");
                            xBisButton.setText("");
                        });

        // Finally setup date pickers for filtering
        setupDatePickers();
    }

    // =====================================================================
    // DATE RANGE SETUP
    // =====================================================================

    /** Reads the oldest available timestamp from DB → sets initial "from" date. */
    private void setupDatePickers() {
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        LiveData<Long> oldestTimestampLiveData =
                DB.getDatabase(getApplicationContext()).sensorDao().getOldestAccelTimestamp();

        oldestTimestampLiveData.observe(
                this,
                new androidx.lifecycle.Observer<Long>() {
                    @Override
                    public void onChanged(Long oldest) {
                        if (oldest != null && oldest > 0) {
                            if (!isTenMinuteFilterActive) {
                                dateFromCalendar.setTimeInMillis(oldest);
                            }
                            setupFromDatePickers();

                            oldestTimestampLiveData.removeObserver(this);
                        }
                    }
                });

        setupToDatePickers();
        toggleTenMinutesFilter();
    }

    private void setupFromDatePickers() {
        DatePickerHandler.createForButton(
                xVonButton,
                cal -> {
                    stopSlidingWindow();
                    dateFromCalendar = cal;
                    updateChartsWithDateFilter();
                },
                this);
    }

    private void setupToDatePickers() {
        DatePickerHandler.createForButton(
                xBisButton,
                cal -> {
                    stopSlidingWindow();
                    dateToCalendar = cal;
                    updateChartsWithDateFilter();
                },
                this);
    }

    // =====================================================================
    // DATE FILTER APPLICATION
    // =====================================================================

    /** Applies date range filter to all sensors (Accel/Gyro/Magnet) and recomputes datasets. */
    private void updateChartsWithDateFilter() {
        if (dateFromCalendar == null || dateToCalendar == null) return;

        if (currentAccelLiveData != null) currentAccelLiveData.removeObservers(this);
        if (currentGyroLiveData != null) currentGyroLiveData.removeObservers(this);
        if (currentMagLiveData != null) currentMagLiveData.removeObservers(this);

        long fromTime = dateFromCalendar.getTimeInMillis();
        long toTime;

        if (isTenMinuteFilterActive) {
            toTime = dateToCalendar.getTimeInMillis();
        } else {
            Calendar adjustedTo = (Calendar) dateToCalendar.clone();
            adjustedTo.set(Calendar.HOUR_OF_DAY, 23);
            adjustedTo.set(Calendar.MINUTE, 59);
            adjustedTo.set(Calendar.SECOND, 59);
            toTime = adjustedTo.getTimeInMillis();
        }

        // ============================
        // ACCEL DATA
        // ============================
        currentAccelLiveData =
                DB.getDatabase(getApplicationContext())
                        .sensorDao()
                        .getAccelDataBetween(fromTime, toTime);
        currentAccelLiveData.observe(
                this,
                data -> {
                    if (data != null && !data.isEmpty()) initializeAccelDataSets(data);
                    else {
                        lineDataAccelx =
                                lineDataAccely = lineDataAccelz = lineDataAccelTotal = null;
                        setupChart(lineChartAccel, "Beschleunigung", 0);
                    }
                    updateAccelChart();
                });

        // ============================
        // GYRO DATA
        // ============================
        currentGyroLiveData =
                DB.getDatabase(getApplicationContext())
                        .sensorDao()
                        .getGyroDataBetween(fromTime, toTime);
        currentGyroLiveData.observe(
                this,
                data -> {
                    if (data != null && !data.isEmpty()) initializeGyroDataSets(data);
                    else {
                        lineDataGyrox = lineDataGyroy = lineDataGyroz = lineDataGyroTotal = null;
                        setupChart(lineChartGyro, "Gyroskop", 0);
                    }
                    updateAccelChart();
                });

        // ============================
        // MAGNET DATA
        // ============================
        currentMagLiveData =
                DB.getDatabase(getApplicationContext())
                        .sensorDao()
                        .getMagnetDataBetween(fromTime, toTime);
        currentMagLiveData.observe(
                this,
                data -> {
                    if (data != null && !data.isEmpty()) initializeMagDataSets(data);
                    else {
                        lineDataMagx = lineDataMagy = lineDataMagz = lineDataMagTotal = null;
                        setupChart(lineChartMag, "Magnetfeld", 0);
                    }
                    updateAccelChart();
                });
    }

    // =====================================================================
    // CHECKBOX HANDLING
    // =====================================================================

    /**
     * Links every checkbox to a listener → whenever user toggles a dataset, we rebuild the charts
     * instantly.
     */
    private void setupCheckboxes() {
        AccelXCheck = findViewById(R.id.AccelxCheck);
        AccelYCheck = findViewById(R.id.AccelyCheck);
        AccelZCheck = findViewById(R.id.AccelzCheck);
        AccelSumCheck = findViewById(R.id.AccelSumCheck);

        GyroXCheck = findViewById(R.id.GyroxCheck);
        GyroYCheck = findViewById(R.id.GyroyCheck);
        GyroZCheck = findViewById(R.id.GyrozCheck);
        GyroSumCheck = findViewById(R.id.GyroSumCheck);

        MagXCheck = findViewById(R.id.MagxCheck);
        MagYCheck = findViewById(R.id.MagyCheck);
        MagZCheck = findViewById(R.id.MagzCheck);
        MagSumCheck = findViewById(R.id.MagSumCheck);

        // Whenever any of these changes, update charts
        CheckBox[] all = {
            AccelXCheck, AccelYCheck, AccelZCheck, AccelSumCheck,
            GyroXCheck, GyroYCheck, GyroZCheck, GyroSumCheck,
            MagXCheck, MagYCheck, MagZCheck, MagSumCheck
        };

        for (CheckBox box : all)
            box.setOnCheckedChangeListener((button, isChecked) -> updateAccelChart());
    }

    /**
     * Convenience filter: sets the date range to "now minus 10 minutes" to "now", refreshes the
     * charts immediately and updates it every 5 seconds.
     */
    private void toggleTenMinutesFilter() {
        if (!isTenMinuteFilterActive) {
            isTenMinuteFilterActive = true;
            isStartPointFixed = false;
            btnFilterLast10Min.setBackgroundColor(ContextCompat.getColor(this, R.color.header));
            startSlidingWindow();
        } else if (!isStartPointFixed) {
            isStartPointFixed = true;
            btnFilterLast10Min.setBackgroundColor(ContextCompat.getColor(this, R.color.button));
        } else {
            isStartPointFixed = false;
            isTenMinuteFilterActive = false;
            btnFilterLast10Min.setBackgroundColor(ContextCompat.getColor(this, R.color.button));
            stopSlidingWindow();
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
        isStartPointFixed = false; // Reset!
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

    /** Formats a Calendar into "dd.MM.yyyy" (no time) – used for the quick-filter labels. */
    private String makeDateTimeString(Calendar calendar) {
        return String.format(
                Locale.GERMAN,
                "%02d.%02d.%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR));
    }

    // =====================================================================
    // LIVE DATA OBSERVERS — REAL-TIME DATABASE UPDATES
    // =====================================================================

    private void observeAccelData() {
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAllAccelData()
                .observe(
                        this,
                        list -> {
                            if (list != null && !list.isEmpty()) {
                                long first = list.get(0).timestamp;
                                setupChart(lineChartAccel, "Beschleunigung", first);
                                initializeAccelDataSets(list);
                                updateAccelChart();
                            }
                        });
    }

    private void observeGyroData() {
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAllGyroData()
                .observe(
                        this,
                        list -> {
                            if (list != null && !list.isEmpty()) {
                                long first = list.get(0).timestamp;
                                setupChart(lineChartGyro, "Gyroskop", first);
                                initializeGyroDataSets(list);
                                updateAccelChart();
                            }
                        });
    }

    private void observeMagnetData() {
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAllMagnetData()
                .observe(
                        this,
                        list -> {
                            if (list != null && !list.isEmpty()) {
                                long first = list.get(0).timestamp;
                                setupChart(lineChartMag, "Magnetfeld", first);
                                initializeMagDataSets(list);
                                updateAccelChart();
                            }
                        });
    }

    // =====================================================================
    // DATASET CONSTRUCTION FOR EACH SENSOR
    // Converts DB rows → LineDataSet objects for charts
    // =====================================================================

    private void initializeAccelDataSets(List<AccelData> list) {
        // Check if Douglas-Peucker is enabled in Settings
        SharedPreferences prefs = getSharedPreferences("GraphSettings", MODE_PRIVATE);
        boolean dpEnabled = prefs.getBoolean("dp_enabled", false);

        List<AccelData> dataToUse = list;

        // Only apply Douglas-Peucker if enabled
        if (dpEnabled) {
            float epsilon = EpsilonCalculator.calculateEpsilon(this, list);
            dataToUse = DouglasPeukerAlg.simplify(list, epsilon);
        }

        ArrayList<Entry> xs = new ArrayList<>();
        ArrayList<Entry> ys = new ArrayList<>();
        ArrayList<Entry> zs = new ArrayList<>();
        ArrayList<Entry> totals = new ArrayList<>();

        long first = list.get(0).timestamp;

        for (AccelData d : dataToUse) {
            float t = d.timestamp - first;
            xs.add(new Entry(t, d.accelX));
            ys.add(new Entry(t, d.accelY));
            zs.add(new Entry(t, d.accelZ));
            totals.add(
                    new Entry(
                            t,
                            (float)
                                    Math.sqrt(
                                            d.accelX * d.accelX
                                                    + d.accelY * d.accelY
                                                    + d.accelZ * d.accelZ)));
        }

        lineDataAccelx = new LineDataSet(xs, "X-Achse");
        lineDataAccelx.setColor(Color.CYAN);
        lineDataAccelx.setDrawCircles(false);
        lineDataAccely = new LineDataSet(ys, "Y-Achse");
        lineDataAccely.setColor(Color.WHITE);
        lineDataAccely.setDrawCircles(false);
        lineDataAccelz = new LineDataSet(zs, "Z-Achse");
        lineDataAccelz.setColor(Color.GREEN);
        lineDataAccelz.setDrawCircles(false);
        lineDataAccelTotal = new LineDataSet(totals, "Summe");
        lineDataAccelTotal.setColor(Color.RED);
        lineDataAccelTotal.setDrawCircles(false);
    }

    private void initializeGyroDataSets(List<GyroData> list) {
        // Check if Douglas-Peucker is enabled in Settings
        SharedPreferences prefs = getSharedPreferences("GraphSettings", MODE_PRIVATE);
        boolean dpEnabled = prefs.getBoolean("dp_enabled", false);

        List<GyroData> dataToUse = list;

        // Only apply Douglas-Peucker if enabled
        if (dpEnabled) {
            float epsilon = EpsilonCalculator.calculateEpsilon(this, list);
            dataToUse = DouglasPeukerAlg.simplify(list, epsilon);
        }

        ArrayList<Entry> xs = new ArrayList<>();
        ArrayList<Entry> ys = new ArrayList<>();
        ArrayList<Entry> zs = new ArrayList<>();
        ArrayList<Entry> totals = new ArrayList<>();

        long first = list.get(0).timestamp;

        for (GyroData d : dataToUse) {
            float t = d.timestamp - first;
            xs.add(new Entry(t, d.gyroX));
            ys.add(new Entry(t, d.gyroY));
            zs.add(new Entry(t, d.gyroZ));
            totals.add(
                    new Entry(
                            t,
                            (float)
                                    Math.sqrt(
                                            d.gyroX * d.gyroX
                                                    + d.gyroY * d.gyroY
                                                    + d.gyroZ * d.gyroZ)));
        }

        lineDataGyrox = new LineDataSet(xs, "X-Achse");
        lineDataGyrox.setColor(Color.CYAN);
        lineDataGyrox.setDrawCircles(false);
        lineDataGyroy = new LineDataSet(ys, "Y-Achse");
        lineDataGyroy.setColor(Color.WHITE);
        lineDataGyroy.setDrawCircles(false);
        lineDataGyroz = new LineDataSet(zs, "Z-Achse");
        lineDataGyroz.setColor(Color.GREEN);
        lineDataGyroz.setDrawCircles(false);
        lineDataGyroTotal = new LineDataSet(totals, "Summe");
        lineDataGyroTotal.setColor(Color.RED);
        lineDataGyroTotal.setDrawCircles(false);
    }

    private void initializeMagDataSets(List<MagnetData> list) {
        // Check if Douglas-Peucker is enabled in Settings
        SharedPreferences prefs = getSharedPreferences("GraphSettings", MODE_PRIVATE);
        boolean dpEnabled = prefs.getBoolean("dp_enabled", false);

        List<MagnetData> dataToUse = list;

        // Only apply Douglas-Peucker if enabled
        if (dpEnabled) {
            float epsilon = EpsilonCalculator.calculateEpsilon(this, list);
            dataToUse = DouglasPeukerAlg.simplify(list, epsilon);
        }

        ArrayList<Entry> xs = new ArrayList<>();
        ArrayList<Entry> ys = new ArrayList<>();
        ArrayList<Entry> zs = new ArrayList<>();
        ArrayList<Entry> totals = new ArrayList<>();

        long first = list.get(0).timestamp;

        for (MagnetData d : dataToUse) {
            float t = d.timestamp - first;
            xs.add(new Entry(t, d.magnetX));
            ys.add(new Entry(t, d.magnetY));
            zs.add(new Entry(t, d.magnetZ));
            totals.add(
                    new Entry(
                            t,
                            (float)
                                    Math.sqrt(
                                            d.magnetX * d.magnetX
                                                    + d.magnetY * d.magnetY
                                                    + d.magnetZ * d.magnetZ)));
        }

        lineDataMagx = new LineDataSet(xs, "X-Achse");
        lineDataMagx.setColor(Color.CYAN);
        lineDataMagx.setDrawCircles(false);
        lineDataMagy = new LineDataSet(ys, "Y-Achse");
        lineDataMagy.setColor(Color.WHITE);
        lineDataMagy.setDrawCircles(false);
        lineDataMagz = new LineDataSet(zs, "Z-Achse");
        lineDataMagz.setColor(Color.GREEN);
        lineDataMagz.setDrawCircles(false);
        lineDataMagTotal = new LineDataSet(totals, "Summe");
        lineDataMagTotal.setColor(Color.RED);
        lineDataMagTotal.setDrawCircles(false);
    }

    // =====================================================================
    // CHART UPDATE PIPELINE
    // Combines checkbox visibility + dataset contents.
    // =====================================================================

    /**
     * This method builds the final LineData objects for each chart, depending on which checkboxes
     * are active.
     */
    private void updateAccelChart() {

        // Prevent null access before data is loaded
        if (lineDataAccelx == null && lineDataGyrox == null && lineDataMagx == null) return;

        // ------------------------ ACCEL CHART ------------------------
        if (lineDataAccelx != null) {
            LineData accel = new LineData();
            if (AccelXCheck.isChecked()) accel.addDataSet(lineDataAccelx);
            if (AccelYCheck.isChecked()) accel.addDataSet(lineDataAccely);
            if (AccelZCheck.isChecked()) accel.addDataSet(lineDataAccelz);
            if (AccelSumCheck.isChecked()) accel.addDataSet(lineDataAccelTotal);

            if (accel.getDataSetCount() > 0) {
                lineChartAccel.setData(accel);
                lineChartAccel.invalidate();
            } else {
                lineChartAccel.clear();
            }
        }

        // ------------------------ GYRO CHART ------------------------
        if (lineDataGyrox != null) {
            LineData gyro = new LineData();
            if (GyroXCheck.isChecked()) gyro.addDataSet(lineDataGyrox);
            if (GyroYCheck.isChecked()) gyro.addDataSet(lineDataGyroy);
            if (GyroZCheck.isChecked()) gyro.addDataSet(lineDataGyroz);
            if (GyroSumCheck.isChecked()) gyro.addDataSet(lineDataGyroTotal);

            if (gyro.getDataSetCount() > 0) {
                lineChartGyro.setData(gyro);
                lineChartGyro.invalidate();
            } else {
                lineChartGyro.clear();
            }
        }

        // ------------------------ MAGNET CHART ------------------------
        if (lineDataMagx != null) {
            LineData mag = new LineData();
            if (MagXCheck.isChecked()) mag.addDataSet(lineDataMagx);
            if (MagYCheck.isChecked()) mag.addDataSet(lineDataMagy);
            if (MagZCheck.isChecked()) mag.addDataSet(lineDataMagz);
            if (MagSumCheck.isChecked()) mag.addDataSet(lineDataMagTotal);

            if (mag.getDataSetCount() > 0) {
                lineChartMag.setData(mag);
                lineChartMag.invalidate();
            } else {
                lineChartMag.clear();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSlidingWindow();
    }
}
