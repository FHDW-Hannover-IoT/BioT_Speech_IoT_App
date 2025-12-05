package com.fhdw.biot.speech.iot.graph;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fhdw.biot.speech.iot.util.DatePickerHandler;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.sensor.AccelActivity;
import com.fhdw.biot.speech.iot.events.EreignisActivity;
import com.fhdw.biot.speech.iot.sensor.GyroActivity;
import com.fhdw.biot.speech.iot.sensor.MagnetActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import database.entities.AccelData;
import database.entities.GyroData;
import database.entities.MagnetData;
import database.DB;

/**
 * MainGraphActivity
 * -----------------
 * THIS IS THE MAIN VISUALIZATION SCREEN FOR THE ENTIRE APP.
 *
 * PURPOSE OF THIS ACTIVITY:
 *  • Visualize all 3 sensors (Accel, Gyro, Magnet) at once.
 *  • Allow users to toggle individual axes (X, Y, Z) and the total magnitude.
 *  • Allow dynamic filtering by date range.
 *  • Automatically redraw the graphs anytime:
 *        - the database updates,
 *        - the user changes the date range,
 *        - the user toggles checkboxes.
 *
 * INTERNAL STRUCTURE:
 *  1. Three LineCharts → Accel, Gyro, Magnet.
 *  2. Each chart can display up to 4 datasets:
 *        - X axis, Y axis, Z axis, and total magnitude (sqrt(x²+y²+z²))
 *  3. The Activity obtains data from Room using LiveData so updates are automatic.
 *  4. Date ranges are applied using DB filters and LiveData re-queries.
 *  5. The actual drawing logic comes from BaseChartActivity.
 *
 * This Activity is essentially a "dashboard" combining multiple real-time sensor signals.
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
    private Button xVonButton, xBisButton, yVonButton, yBisButton, zVonButton, zBisButton;

    private long startTime = 0; // initial timestamp for axis formatting (seconds)

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
        ButtonWerte.setOnClickListener(v ->
                startActivity(new Intent(MainGraphActivity.this, MainActivity.class)));

        // Setup dynamic visibility controls (checkboxes)
        setupCheckboxes();

        // ------------------------------------------------------------
        // DATE PICKER BUTTON REFERENCES
        // ------------------------------------------------------------
        xVonButton = findViewById(R.id.button_Accel_von);
        xBisButton = findViewById(R.id.button_Accel_bis);

        yVonButton = findViewById(R.id.button_Gyro_von);
        yBisButton = findViewById(R.id.button_Gyro_bis);

        zVonButton = findViewById(R.id.button_Mag_von);
        zBisButton = findViewById(R.id.button_Mag_bis);

        // ------------------------------------------------------------
        // SENSOR NAVIGATION BUTTONS
        // ------------------------------------------------------------
        findViewById(R.id.btnGyro).setOnClickListener(
                v -> startActivity(new Intent(MainGraphActivity.this, GyroActivity.class)));

        findViewById(R.id.btnAccel).setOnClickListener(
                v -> startActivity(new Intent(MainGraphActivity.this, AccelActivity.class)));

        findViewById(R.id.btnMagnet).setOnClickListener(
                v -> startActivity(new Intent(MainGraphActivity.this, MagnetActivity.class)));

        // Ereignis view
        findViewById(R.id.notification_button).setOnClickListener(v -> {
            Intent intent = new Intent(MainGraphActivity.this, EreignisActivity.class);
            intent.putExtra("SENSOR_FILTER", "ALL");
            startActivity(intent);
        });

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
        findViewById(R.id.resetAccel).setOnClickListener(v -> {
            lineChartAccel.fitScreen();
            xVonButton.setText("");
            xBisButton.setText("");
        });

        findViewById(R.id.resetGyro).setOnClickListener(v -> {
            lineChartGyro.fitScreen();
            yVonButton.setText("");
            yBisButton.setText("");
        });

        findViewById(R.id.resetMagnet).setOnClickListener(v -> {
            lineChartMag.fitScreen();
            zVonButton.setText("");
            zBisButton.setText("");
        });

        // ------------------------------------------------------------
        // REAL-TIME DATA OBSERVERS (LiveData)
        // These keep the UI always up-to-date.
        // ------------------------------------------------------------
        observeAccelData();
        observeGyroData();
        observeMagnetData();

        // Finally setup date pickers for filtering
        setupDatePickers();
    }

    // =====================================================================
    // DATE RANGE SETUP
    // =====================================================================

    /**
     * Reads the oldest available timestamp from DB → sets initial "from" date.
     */
    private void setupDatePickers() {
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getOldestAccelTimestamp()
                .observe(this, oldest -> {
                    if (oldest != null && oldest > 0) {
                        dateFromCalendar.setTimeInMillis(oldest);
                    }
                });

        setupFromDatePickers();
        setupToDatePickers();

        updateChartsWithDateFilter();
    }

    /**
     * Creates the three “from date” pickers (Accel/Gyro/Magnet).
     */
    private void setupFromDatePickers() {
        DatePickerHandler.OnDateSelectedListener listener = cal -> {
            dateFromCalendar = cal;
            updateChartsWithDateFilter();
        };

        DatePickerHandler.createForButton(xVonButton, listener, this);
        DatePickerHandler.createForButton(yVonButton, listener, this);
        DatePickerHandler.createForButton(zVonButton, listener, this);

        // Initially empty → user decides manually
        xVonButton.setText("");
        yVonButton.setText("");
        zVonButton.setText("");
    }

    /**
     * Creates the three “to date” pickers.
     */
    private void setupToDatePickers() {
        DatePickerHandler.OnDateSelectedListener listener = cal -> {
            dateToCalendar = cal;
            updateChartsWithDateFilter();
        };

        DatePickerHandler.createForButton(xBisButton, listener, this);
        DatePickerHandler.createForButton(yBisButton, listener, this);
        DatePickerHandler.createForButton(zBisButton, listener, this);

        xBisButton.setText("");
        yBisButton.setText("");
        zBisButton.setText("");
    }

    // =====================================================================
    // DATE FILTER APPLICATION
    // =====================================================================

    /**
     * Applies date range filter to all sensors (Accel/Gyro/Magnet)
     * and recomputes datasets.
     */
    private void updateChartsWithDateFilter() {
        if (dateFromCalendar == null || dateToCalendar == null) return;

        // Extend end date to include full day
        Calendar adjustedTo = (Calendar) dateToCalendar.clone();
        adjustedTo.set(Calendar.HOUR_OF_DAY, 23);
        adjustedTo.set(Calendar.MINUTE, 59);
        adjustedTo.set(Calendar.SECOND, 59);

        long start = dateFromCalendar.getTimeInMillis();
        long end = adjustedTo.getTimeInMillis();

        // ============================
        // ACCEL DATA
        // ============================
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAccelDataBetween(start, end)
                .observe(this, data -> {
                    if (data != null && !data.isEmpty())
                        initializeAccelDataSets(data);
                    else {
                        lineDataAccelx = lineDataAccely = lineDataAccelz = lineDataAccelTotal = null;
                        setupChart(lineChartAccel, "Beschleunigung", 0);
                    }
                    updateAccelChart();
                });

        // ============================
        // GYRO DATA
        // ============================
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getGyroDataBetween(start, end)
                .observe(this, data -> {
                    if (data != null && !data.isEmpty())
                        initializeGyroDataSets(data);
                    else {
                        lineDataGyrox = lineDataGyroy = lineDataGyroz = lineDataGyroTotal = null;
                        setupChart(lineChartGyro, "Gyroskop", 0);
                    }
                    updateAccelChart();
                });

        // ============================
        // MAGNET DATA
        // ============================
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getMagnetDataBetween(start, end)
                .observe(this, data -> {
                    if (data != null && !data.isEmpty())
                        initializeMagDataSets(data);
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
     * Links every checkbox to a listener → whenever user toggles a dataset,
     * we rebuild the charts instantly.
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

    // =====================================================================
    // LIVE DATA OBSERVERS — REAL-TIME DATABASE UPDATES
    // =====================================================================

    private void observeAccelData() {
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAllAccelData()
                .observe(this, list -> {
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
                .observe(this, list -> {
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
                .observe(this, list -> {
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
        ArrayList<Entry> xs = new ArrayList<>();
        ArrayList<Entry> ys = new ArrayList<>();
        ArrayList<Entry> zs = new ArrayList<>();
        ArrayList<Entry> totals = new ArrayList<>();

        long first = list.get(0).timestamp;

        for (AccelData d : list) {
            float t = d.timestamp - first;
            xs.add(new Entry(t, d.accelX));
            ys.add(new Entry(t, d.accelY));
            zs.add(new Entry(t, d.accelZ));
            totals.add(new Entry(t, (float) Math.sqrt(d.accelX*d.accelX + d.accelY*d.accelY + d.accelZ*d.accelZ)));
        }

        lineDataAccelx = new LineDataSet(xs, "X-Achse"); lineDataAccelx.setColor(Color.CYAN);
        lineDataAccely = new LineDataSet(ys, "Y-Achse"); lineDataAccely.setColor(Color.WHITE);
        lineDataAccelz = new LineDataSet(zs, "Z-Achse"); lineDataAccelz.setColor(Color.GREEN);
        lineDataAccelTotal = new LineDataSet(totals, "Summe"); lineDataAccelTotal.setColor(Color.RED);
    }

    private void initializeGyroDataSets(List<GyroData> list) {
        ArrayList<Entry> xs = new ArrayList<>();
        ArrayList<Entry> ys = new ArrayList<>();
        ArrayList<Entry> zs = new ArrayList<>();
        ArrayList<Entry> totals = new ArrayList<>();

        long first = list.get(0).timestamp;

        for (GyroData d : list) {
            float t = d.timestamp - first;
            xs.add(new Entry(t, d.gyroX));
            ys.add(new Entry(t, d.gyroY));
            zs.add(new Entry(t, d.gyroZ));
            totals.add(new Entry(t, (float) Math.sqrt(d.gyroX*d.gyroX + d.gyroY*d.gyroY + d.gyroZ*d.gyroZ)));
        }

        lineDataGyrox = new LineDataSet(xs, "X-Achse"); lineDataGyrox.setColor(Color.CYAN);
        lineDataGyroy = new LineDataSet(ys, "Y-Achse"); lineDataGyroy.setColor(Color.WHITE);
        lineDataGyroz = new LineDataSet(zs, "Z-Achse"); lineDataGyroz.setColor(Color.GREEN);
        lineDataGyroTotal = new LineDataSet(totals, "Summe"); lineDataGyroTotal.setColor(Color.RED);
    }

    private void initializeMagDataSets(List<MagnetData> list) {
        ArrayList<Entry> xs = new ArrayList<>();
        ArrayList<Entry> ys = new ArrayList<>();
        ArrayList<Entry> zs = new ArrayList<>();
        ArrayList<Entry> totals = new ArrayList<>();

        long first = list.get(0).timestamp;

        for (MagnetData d : list) {
            float t = d.timestamp - first;
            xs.add(new Entry(t, d.magnetX));
            ys.add(new Entry(t, d.magnetY));
            zs.add(new Entry(t, d.magnetZ));
            totals.add(new Entry(t, (float) Math.sqrt(d.magnetX*d.magnetX + d.magnetY*d.magnetY + d.magnetZ*d.magnetZ)));
        }

        lineDataMagx = new LineDataSet(xs, "X-Achse"); lineDataMagx.setColor(Color.CYAN);
        lineDataMagy = new LineDataSet(ys, "Y-Achse"); lineDataMagy.setColor(Color.WHITE);
        lineDataMagz = new LineDataSet(zs, "Z-Achse"); lineDataMagz.setColor(Color.GREEN);
        lineDataMagTotal = new LineDataSet(totals, "Summe"); lineDataMagTotal.setColor(Color.RED);
    }

    // =====================================================================
    // CHART UPDATE PIPELINE
    // Combines checkbox visibility + dataset contents.
    // =====================================================================

    /**
     * This method builds the final LineData objects for each chart,
     * depending on which checkboxes are active.
     */
    private void updateAccelChart() {

        // Prevent null access before data is loaded
        if (lineDataAccelx == null || lineDataGyrox == null || lineDataMagx == null)
            return;

        // ------------------------ ACCEL CHART ------------------------
        LineData accel = new LineData();
        if (AccelXCheck.isChecked()) accel.addDataSet(lineDataAccelx);
        if (AccelYCheck.isChecked()) accel.addDataSet(lineDataAccely);
        if (AccelZCheck.isChecked()) accel.addDataSet(lineDataAccelz);
        if (AccelSumCheck.isChecked()) accel.addDataSet(lineDataAccelTotal);

        lineChartAccel.setData(accel);
        lineChartAccel.invalidate();

        // ------------------------ GYRO CHART ------------------------
        LineData gyro = new LineData();
        if (GyroXCheck.isChecked()) gyro.addDataSet(lineDataGyrox);
        if (GyroYCheck.isChecked()) gyro.addDataSet(lineDataGyroy);
        if (GyroZCheck.isChecked()) gyro.addDataSet(lineDataGyroz);
        if (GyroSumCheck.isChecked()) gyro.addDataSet(lineDataGyroTotal);

        lineChartGyro.setData(gyro);
        lineChartGyro.invalidate();

        // ------------------------ MAGNET CHART ------------------------
        LineData mag = new LineData();
        if (MagXCheck.isChecked()) mag.addDataSet(lineDataMagx);
        if (MagYCheck.isChecked()) mag.addDataSet(lineDataMagy);
        if (MagZCheck.isChecked()) mag.addDataSet(lineDataMagz);
        if (MagSumCheck.isChecked()) mag.addDataSet(lineDataMagTotal);

        lineChartMag.setData(mag);
        lineChartMag.invalidate();
    }
}
