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
import com.fhdw.biot.speech.iot.database.entities.AccelData;
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

    /** Optional: start time reference (currently unused but kept for extensions). */
    private long startTime = 0;

    /** Selected date range for filtering. */
    private Calendar dateFromCalendar;

    private Calendar dateToCalendar;

    /** Date filter buttons ("von" / "bis" for X, Y, Z charts). */
    private Button xVonButton, xBisButton;

    private Spinner spinnerTimeframe;
    private int selectedMinutes = 10;

    /** Chart visibility checkboxes. */
    private CheckBox cbChartX, cbChartY, cbChartZ;

    private SensorRepository sensorRepository;
    private LiveData<List<AccelData>> currentLiveData;

    private Handler slidingWindowHandler = new Handler(Looper.getMainLooper());
    private Runnable slidingWindowRunnable;
    private boolean isTenMinuteFilterActive = false;

    /** Fixed reference timestamp for X-axis; reset when user manually changes the date range. */
    private long windowStart = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beschleunigung);
        sensorRepository = ((BiotApplication) getApplication()).getContainer().sensorRepository();

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

        // Wire buttons immediately so they work even with an empty DB.
        setupFromDatePickers(xVonButton);
        setupToDatePickers(xBisButton);

        isTenMinuteFilterActive = true;
        startSlidingWindow();
        checkVoiceFilterIntent();
    }

    private void setupFromDatePickers(Button xVonButton) {
        DateTimePickerHandler.createForButton(
                xVonButton,
                calendar -> {
                    stopSlidingWindow();
                    dateFromCalendar = calendar;
                    windowStart = 0;
                    updateChartsWithDateFilter();
                },
                AccelActivity.this);
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
                AccelActivity.this);
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

        currentLiveData = sensorRepository.getAccelBetween(fromTime, toTime);

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

    private String makeDateTimeString(Calendar calendar) {
        return DateTimePickerHandler.format(calendar);
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
        if (accelDataList.isEmpty()) return;

        if (windowStart == 0) windowStart = accelDataList.get(0).timestamp;

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        for (AccelData data : accelDataList) {
            float t = data.timestamp - windowStart;
            entriesX.add(new Entry(t, data.accelX));
            entriesY.add(new Entry(t, data.accelY));
            entriesZ.add(new Entry(t, data.accelZ));
        }

        setData(lineChartAccelX, entriesX, "X", Color.CYAN);
        setData(lineChartAccelY, entriesY, "Y", Color.GREEN);
        setData(lineChartAccelZ, entriesZ, "Z", Color.YELLOW);

        pinViewport(lineChartAccelX, lineChartAccelY, lineChartAccelZ);
    }

    private void pinViewport(LineChart... charts) {
        for (LineChart chart : charts) {
            if (chart.getData() == null) continue;
            chart.setVisibleXRangeMaximum(120_000f); // default: show last 2 min
            chart.moveViewToX(chart.getData().getXMax());
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
