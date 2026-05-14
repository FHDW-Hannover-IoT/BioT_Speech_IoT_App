package com.fhdw.biot.speech.iot.graph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
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
import com.fhdw.biot.speech.iot.config.BiotApplication;
import com.fhdw.biot.speech.iot.database.entities.AccelData;
import com.fhdw.biot.speech.iot.database.entities.GyroData;
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.repository.SensorRepository;
import com.fhdw.biot.speech.iot.sensor.AccelActivity;
import com.fhdw.biot.speech.iot.sensor.GyroActivity;
import com.fhdw.biot.speech.iot.sensor.MagnetActivity;
import com.fhdw.biot.speech.iot.settings.SettingsActivity;
import com.fhdw.biot.speech.iot.util.DateTimePickerHandler;
import com.fhdw.biot.speech.iot.voice.VoiceCommandExecutor;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

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

    private SensorRepository sensorRepository;
    private LiveData<List<AccelData>> currentAccelLiveData;
    private LiveData<List<GyroData>> currentGyroLiveData;
    private LiveData<List<MagnetData>> currentMagLiveData;

    // -----------------------------------------------
    // DATASETS FOR ALL AXES AND TOTAL MAGNITUDE
    // Each axis is a list of segments so gaps in sensor data appear as
    // disconnected lines rather than bridged interpolations (TC15).
    // -----------------------------------------------
    private List<LineDataSet> lineDataAccelx, lineDataAccely, lineDataAccelz, lineDataAccelTotal;
    private List<LineDataSet> lineDataGyrox, lineDataGyroy, lineDataGyroz, lineDataGyroTotal;
    private List<LineDataSet> lineDataMagx, lineDataMagy, lineDataMagz, lineDataMagTotal;

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
    private boolean isUserInteracting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_graph);
        sensorRepository = ((BiotApplication) getApplication()).getContainer().sensorRepository();

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

        // Attach marker views — persist across data reloads since setupChart never clears them
        lineChartAccel.setMarkerView(new SensorMarkerView(this, "m/s²"));
        lineChartGyro.setMarkerView(new SensorMarkerView(this, "°/s"));
        lineChartMag.setMarkerView(new SensorMarkerView(this, "µT"));
        lineChartAccel.setHighlightPerTapEnabled(true);
        lineChartGyro.setHighlightPerTapEnabled(true);
        lineChartMag.setHighlightPerTapEnabled(true);

        attachGestureTracking(lineChartAccel, lineChartGyro, lineChartMag);

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

        // Always wire both buttons immediately so they work even when the DB is empty.
        setupFromDatePickers();
        setupToDatePickers();

        // Update the from-date to the oldest DB record once data is available.
        sensorRepository
                .getOldestAccelTimestamp()
                .observe(
                        this,
                        oldest -> {
                            if (oldest != null && oldest > 0 && !isTenMinuteFilterActive) {
                                dateFromCalendar.setTimeInMillis(oldest);
                                updateChartsWithDateFilter();
                            }
                        });

        toggleTenMinutesFilter();
    }

    private void setupFromDatePickers() {
        DateTimePickerHandler.createForButton(
                xVonButton,
                cal -> {
                    stopSlidingWindow();
                    dateFromCalendar = cal;
                    updateChartsWithDateFilter();
                },
                this);
    }

    private void setupToDatePickers() {
        DateTimePickerHandler.createForButton(
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
        currentAccelLiveData = sensorRepository.getAccelBetween(fromTime, toTime);
        currentAccelLiveData.observe(
                this,
                data -> {
                    if (data != null && !data.isEmpty()) {
                        initializeAccelDataSets(data, fromTime, toTime);
                    } else {
                        lineDataAccelx =
                                lineDataAccely = lineDataAccelz = lineDataAccelTotal = null;
                        setupChart(lineChartAccel, "Beschleunigung", fromTime);
                        pinXAxisRange(lineChartAccel, 0, toTime - fromTime);
                    }
                    updateAccelChart();
                });

        // ============================
        // GYRO DATA
        // ============================
        currentGyroLiveData = sensorRepository.getGyroBetween(fromTime, toTime);
        currentGyroLiveData.observe(
                this,
                data -> {
                    if (data != null && !data.isEmpty()) {
                        initializeGyroDataSets(data, fromTime, toTime);
                    } else {
                        lineDataGyrox = lineDataGyroy = lineDataGyroz = lineDataGyroTotal = null;
                        setupChart(lineChartGyro, "Gyroskop", fromTime);
                        pinXAxisRange(lineChartGyro, 0, toTime - fromTime);
                    }
                    updateAccelChart();
                });

        // ============================
        // MAGNET DATA
        // ============================
        currentMagLiveData = sensorRepository.getMagnetBetween(fromTime, toTime);
        currentMagLiveData.observe(
                this,
                data -> {
                    if (data != null && !data.isEmpty()) {
                        initializeMagDataSets(data, fromTime, toTime);
                    } else {
                        lineDataMagx = lineDataMagy = lineDataMagz = lineDataMagTotal = null;
                        setupChart(lineChartMag, "Magnetfeld", fromTime);
                        pinXAxisRange(lineChartMag, 0, toTime - fromTime);
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

        for (CheckBox box : all) {
            box.setChecked(true);
            box.setOnCheckedChangeListener((button, isChecked) -> updateAccelChart());
        }
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

                        if (!isUserInteracting) {
                            long now = System.currentTimeMillis();

                            if (!isStartPointFixed) {
                                long tenMinutesAgo = now - (10 * 60 * 1000);
                                dateFromCalendar.setTimeInMillis(tenMinutesAgo);
                            }

                            dateToCalendar.setTimeInMillis(now);

                            syncDateButtonTexts();
                            updateChartsWithDateFilter();
                        }

                        slidingWindowHandler.postDelayed(this, 5000);
                    }
                };
        slidingWindowHandler.post(slidingWindowRunnable);
    }

    private void attachGestureTracking(LineChart... charts) {
        for (LineChart chart : charts) {
            chart.setOnChartGestureListener(
                    new OnChartGestureListener() {
                        @Override
                        public void onChartGestureStart(
                                MotionEvent me,
                                ChartTouchListener.ChartGesture lastPerformedGesture) {
                            isUserInteracting = true;
                        }

                        @Override
                        public void onChartGestureEnd(
                                MotionEvent me,
                                ChartTouchListener.ChartGesture lastPerformedGesture) {
                            isUserInteracting = false;
                            // Exit drag-tracking mode and dismiss all markers
                            for (LineChart c : charts) {
                                c.setHighlightPerDragEnabled(false);
                                c.highlightValue(null);
                            }
                        }

                        @Override
                        public void onChartLongPressed(MotionEvent me) {
                            // Activate drag-tracking mode on all charts simultaneously
                            for (LineChart c : charts) {
                                c.setHighlightPerDragEnabled(true);
                            }
                        }

                        @Override
                        public void onChartDoubleTapped(MotionEvent me) {}

                        @Override
                        public void onChartSingleTapped(MotionEvent me) {}

                        @Override
                        public void onChartFling(
                                MotionEvent me1,
                                MotionEvent me2,
                                float velocityX,
                                float velocityY) {}

                        @Override
                        public void onChartScale(MotionEvent me, float scaleX, float scaleY) {}

                        @Override
                        public void onChartTranslate(MotionEvent me, float dX, float dY) {}
                    });
        }
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

    private String makeDateTimeString(Calendar calendar) {
        return DateTimePickerHandler.format(calendar);
    }

    // =====================================================================
    // DATASET CONSTRUCTION FOR EACH SENSOR
    // Converts DB rows → LineDataSet objects for charts
    // =====================================================================

    private void initializeAccelDataSets(List<AccelData> list, long fromTime, long toTime) {
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

        lineDataAccelx = GraphUtils.buildSegmented(xs, "X-Achse", Color.CYAN);
        lineDataAccely = GraphUtils.buildSegmented(ys, "Y-Achse", Color.WHITE);
        lineDataAccelz = GraphUtils.buildSegmented(zs, "Z-Achse", Color.GREEN);
        lineDataAccelTotal = GraphUtils.buildSegmented(totals, "Summe", Color.RED);

        applyAbsoluteXAxis(lineChartAccel, first);
        pinXAxisRange(lineChartAccel, fromTime - first, toTime - first);
    }

    private void initializeGyroDataSets(List<GyroData> list, long fromTime, long toTime) {
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

        lineDataGyrox = GraphUtils.buildSegmented(xs, "X-Achse", Color.CYAN);
        lineDataGyroy = GraphUtils.buildSegmented(ys, "Y-Achse", Color.WHITE);
        lineDataGyroz = GraphUtils.buildSegmented(zs, "Z-Achse", Color.GREEN);
        lineDataGyroTotal = GraphUtils.buildSegmented(totals, "Summe", Color.RED);

        applyAbsoluteXAxis(lineChartGyro, first);
        pinXAxisRange(lineChartGyro, fromTime - first, toTime - first);
    }

    private void initializeMagDataSets(List<MagnetData> list, long fromTime, long toTime) {
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

        lineDataMagx = GraphUtils.buildSegmented(xs, "X-Achse", Color.CYAN);
        lineDataMagy = GraphUtils.buildSegmented(ys, "Y-Achse", Color.WHITE);
        lineDataMagz = GraphUtils.buildSegmented(zs, "Z-Achse", Color.GREEN);
        lineDataMagTotal = GraphUtils.buildSegmented(totals, "Summe", Color.RED);

        applyAbsoluteXAxis(lineChartMag, first);
        pinXAxisRange(lineChartMag, fromTime - first, toTime - first);
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
            if (AccelXCheck.isChecked()) lineDataAccelx.forEach(accel::addDataSet);
            if (AccelYCheck.isChecked()) lineDataAccely.forEach(accel::addDataSet);
            if (AccelZCheck.isChecked()) lineDataAccelz.forEach(accel::addDataSet);
            if (AccelSumCheck.isChecked()) lineDataAccelTotal.forEach(accel::addDataSet);

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
            if (GyroXCheck.isChecked()) lineDataGyrox.forEach(gyro::addDataSet);
            if (GyroYCheck.isChecked()) lineDataGyroy.forEach(gyro::addDataSet);
            if (GyroZCheck.isChecked()) lineDataGyroz.forEach(gyro::addDataSet);
            if (GyroSumCheck.isChecked()) lineDataGyroTotal.forEach(gyro::addDataSet);

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
            if (MagXCheck.isChecked()) lineDataMagx.forEach(mag::addDataSet);
            if (MagYCheck.isChecked()) lineDataMagy.forEach(mag::addDataSet);
            if (MagZCheck.isChecked()) lineDataMagz.forEach(mag::addDataSet);
            if (MagSumCheck.isChecked()) lineDataMagTotal.forEach(mag::addDataSet);

            if (mag.getDataSetCount() > 0) {
                lineChartMag.setData(mag);
                lineChartMag.invalidate();
            } else {
                lineChartMag.clear();
            }
        }
    }

    // =====================================================================
    // VOICE FILTER BROADCAST RECEIVER
    // Handles FILTER_ACTION sent by VoiceCommandExecutor / LlmQueryHandler.
    // =====================================================================

    private final BroadcastReceiver filterReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int minutes = intent.getIntExtra(VoiceCommandExecutor.EXTRA_FILTER_MINUTES, -1);
                    if (minutes < 0) return;
                    stopSlidingWindow();
                    long now = System.currentTimeMillis();
                    if (minutes == 0) {
                        dateFromCalendar.setTimeInMillis(0);
                        dateToCalendar.setTimeInMillis(now);
                    } else {
                        dateFromCalendar.setTimeInMillis(now - (long) minutes * 60_000);
                        dateToCalendar.setTimeInMillis(now);
                    }
                    syncDateButtonTexts();
                    updateChartsWithDateFilter();
                }
            };

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(
                this,
                filterReceiver,
                new IntentFilter("com.fhdw.biot.speech.iot.FILTER_ACTION"),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        // Re-apply chart data so any Settings changes (DP toggle/epsilon) take effect immediately.
        updateChartsWithDateFilter();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(filterReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSlidingWindow();
    }
}
