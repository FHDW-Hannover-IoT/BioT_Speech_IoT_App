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
import com.fhdw.biot.speech.iot.database.entities.MagnetData;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.graph.BaseChartActivity;
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
 * MagnetActivity -------------- Screen that visualizes magnetometer sensor data in three separate
 * line charts (X, Y, Z axes) and lets the user filter the visible range by date.
 *
 * <p>Responsibilities: - Navigation between other sensor screens (Gyro / Accel) and the main
 * screen. - Fetching magnetometer data from the Room database. - Providing date-range filtering via
 * DatePickerHandler. - Mapping MagnetData rows into MPAndroidChart entries and rendering them. -
 * Reusing BaseChartActivity for common chart styling / behaviour.
 */
public class MagnetActivity extends BaseChartActivity implements IFilterableChart {

    /** Individual charts for X, Y and Z axis values of the magnetometer. */
    private LineChart lineChartMagnetX, lineChartMagnetY, lineChartMagnetZ;

    /** Selected date range used when querying the database. */
    private Calendar dateFromCalendar;

    private Calendar dateToCalendar;

    /** Buttons used to show / pick "from" and "to" dates for each axis. */
    private Button xVonButton, xBisButton;

    private Spinner spinnerTimeframe;
    private int selectedMinutes = 10;

    private CheckBox cbChartX, cbChartY, cbChartZ;

    private SensorRepository sensorRepository;
    private LiveData<List<MagnetData>> currentLiveData;

    private Handler slidingWindowHandler = new Handler(Looper.getMainLooper());
    private Runnable slidingWindowRunnable;
    private boolean isTenMinuteFilterActive = false;
    private long windowStart = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_magnetfeld);
        sensorRepository = ((BiotApplication) getApplication()).getContainer().sensorRepository();

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

        // --------------------------------------------------------------------
        // Date range buttons
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

        cbChartX = findViewById(R.id.cbChartX);
        cbChartY = findViewById(R.id.cbChartY);
        cbChartZ = findViewById(R.id.cbChartZ);
        if (cbChartX != null)
            cbChartX.setOnCheckedChangeListener(
                    (b, c) -> lineChartMagnetX.setVisibility(c ? View.VISIBLE : View.GONE));
        if (cbChartY != null)
            cbChartY.setOnCheckedChangeListener(
                    (b, c) -> lineChartMagnetY.setVisibility(c ? View.VISIBLE : View.GONE));
        if (cbChartZ != null)
            cbChartZ.setOnCheckedChangeListener(
                    (b, c) -> lineChartMagnetZ.setVisibility(c ? View.VISIBLE : View.GONE));

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
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        setupFromDatePickers(xVonButton);
        setupToDatePickers(xBisButton);

        isTenMinuteFilterActive = true;
        startSlidingWindow();
        checkVoiceFilterIntent();
    }

    /**
     * Attaches DatePickers to the three "von" buttons and sets their initial text.
     *
     * <p>For each: - opens a calendar dialog, - updates {@link #dateFromCalendar}, - triggers
     * {@link #updateChartsWithDateFilter()}.
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
                MagnetActivity.this);
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
                MagnetActivity.this);
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

        currentLiveData = sensorRepository.getMagnetBetween(fromTime, toTime);

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

    private String makeDateTimeString(Calendar calendar) {
        return DateTimePickerHandler.format(calendar);
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
        if (magnetDataList == null || magnetDataList.isEmpty()) return;

        if (windowStart == 0) windowStart = magnetDataList.get(0).timestamp;

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        for (MagnetData data : magnetDataList) {
            float t = data.timestamp - windowStart;
            entriesX.add(new Entry(t, data.magnetX));
            entriesY.add(new Entry(t, data.magnetY));
            entriesZ.add(new Entry(t, data.magnetZ));
        }

        setData(lineChartMagnetX, entriesX, "X", Color.CYAN);
        setData(lineChartMagnetY, entriesY, "Y", Color.GREEN);
        setData(lineChartMagnetZ, entriesZ, "Z", Color.YELLOW);

        pinViewport(lineChartMagnetX, lineChartMagnetY, lineChartMagnetZ);
    }

    private void pinViewport(LineChart... charts) {
        for (LineChart chart : charts) {
            if (chart.getData() == null) continue;
            chart.setVisibleXRangeMaximum(120_000f);
            chart.moveViewToX(chart.getData().getXMax());
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
