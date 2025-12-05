package com.fhdw.biot.speech.iot;

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

public class MainGraphActivity extends BaseChartActivity {

    private LineChart lineChartAccel, lineChartGyro, lineChartMag;
    private Calendar dateFromCalendar;
    private Calendar dateToCalendar;
    private LineDataSet lineDataAccelx,
            lineDataAccely,
            lineDataAccelz,
            lineDataAccelTotal,
            lineDataGyrox,
            lineDataGyroy,
            lineDataGyroz,
            lineDataGyroTotal,
            lineDataMagx,
            lineDataMagy,
            lineDataMagz,
            lineDataMagTotal;
    private CheckBox AccelXCheck,
            AccelYCheck,
            AccelZCheck,
            AccelSumCheck,
            GyroXCheck,
            GyroYCheck,
            GyroZCheck,
            GyroSumCheck,
            MagXCheck,
            MagYCheck,
            MagZCheck,
            MagSumCheck;
    private Button xVonButton, xBisButton, yVonButton, yBisButton, zVonButton, zBisButton;
    private long startTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_graph);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        Button ButtonWerte = findViewById(R.id.werteansicht);
        ButtonWerte.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainGraphActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        setupCheckboxes();

        DatePickerHandler datePickerHandler = new DatePickerHandler(MainGraphActivity.this);

        xBisButton = findViewById(R.id.button_Accel_bis);

        xVonButton = findViewById(R.id.button_Accel_von);

        yBisButton = findViewById(R.id.button_Gyro_bis);

        yVonButton = findViewById(R.id.button_Gyro_von);

        zBisButton = findViewById(R.id.button_Mag_bis);

        zVonButton = findViewById(R.id.button_Mag_von);

        Button buttonGyro = findViewById(R.id.btnGyro);
        buttonGyro.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainGraphActivity.this, GyroActivity.class);
                    startActivity(intent);
                });

        Button buttonAccel = findViewById(R.id.btnAccel);
        buttonAccel.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainGraphActivity.this, AccelActivity.class);
                    startActivity(intent);
                });

        Button buttonMagnet = findViewById(R.id.btnMagnet);
        buttonMagnet.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainGraphActivity.this, MagnetActivity.class);
                    startActivity(intent);
                });

        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainGraphActivity.this, EreignisActivity.class);
                    intent.putExtra("SENSOR_FILTER", "ALL");
                    startActivity(intent);
                });

        lineChartAccel = findViewById(R.id.lineChartAccel);
        lineChartGyro = findViewById(R.id.lineChartGyro);
        lineChartMag = findViewById(R.id.lineChartMag);

        setupChart(lineChartAccel, "Beschleunigung", 0);
        setupChart(lineChartGyro, "Gyroskop", 0);
        setupChart(lineChartMag, "Magnetfeld", 0);

        ImageButton resetAccel = findViewById(R.id.resetAccel);
        resetAccel.setOnClickListener(
                view -> {
                    lineChartAccel.fitScreen();
                    xBisButton.setText("");
                    xVonButton.setText("");
                });

        ImageButton resetGyro = findViewById(R.id.resetGyro);
        resetGyro.setOnClickListener(
                view -> {
                    lineChartGyro.fitScreen();
                    yBisButton.setText("");
                    zVonButton.setText("");
                });

        ImageButton resetMagnet = findViewById(R.id.resetMagnet);
        resetMagnet.setOnClickListener(
                view -> {
                    lineChartMag.fitScreen();
                    zBisButton.setText("");
                    zVonButton.setText("");
                });

        setupChart(lineChartAccel, "Beschleunigung", 0);
        setupChart(lineChartGyro, "Gyroskop", 0);
        setupChart(lineChartMag, "Magnetfeld", 0);

        observeAccelData();
        observeGyroData();
        observeMagnetData();

        setupDatePickers();
    }

    private void setupDatePickers() {

        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getOldestAccelTimestamp()
                .observe(
                        this,
                        oldestTimestamp -> {
                            if (oldestTimestamp != null && oldestTimestamp > 0) {
                                dateFromCalendar.setTimeInMillis(oldestTimestamp);
                            }
                        });

        setupFromDatePickers();
        setupToDatePickers();

        updateChartsWithDateFilter();
    }

    private void setupFromDatePickers() {
        DatePickerHandler.OnDateSelectedListener fromListener =
                calendar -> {
                    dateFromCalendar = calendar;
                    updateChartsWithDateFilter();
                };
        DatePickerHandler.createForButton(xVonButton, fromListener, this);

        DatePickerHandler.createForButton(yVonButton, fromListener, this);

        DatePickerHandler.createForButton(zVonButton, fromListener, this);

        // Setze anfängliches Datum
        xVonButton.setText("");
        yVonButton.setText("");
        zVonButton.setText("");
    }

    private void setupToDatePickers() {
        DatePickerHandler.OnDateSelectedListener toListener =
                calendar -> {
                    dateToCalendar = calendar;
                    updateChartsWithDateFilter();
                };
        DatePickerHandler.createForButton(xBisButton, toListener, this);
        DatePickerHandler.createForButton(yBisButton, toListener, this);
        DatePickerHandler.createForButton(zBisButton, toListener, this);

        xBisButton.setText("");
        yBisButton.setText("");
        zBisButton.setText("");
    }

    private void updateChartsWithDateFilter() {
        if (dateFromCalendar == null || dateToCalendar == null) {
            return;
        }

        Calendar adjustedToCalendar = (Calendar) dateToCalendar.clone();
        adjustedToCalendar.add(Calendar.DAY_OF_MONTH, 0);
        adjustedToCalendar.set(Calendar.HOUR_OF_DAY, 23);
        adjustedToCalendar.set(Calendar.MINUTE, 59);
        adjustedToCalendar.set(Calendar.SECOND, 59);

        final long startTime = dateFromCalendar.getTimeInMillis();
        final long endTime = adjustedToCalendar.getTimeInMillis();

        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAccelDataBetween(startTime, endTime)
                .observe(
                        this,
                        filteredData -> {
                            if (filteredData != null && !filteredData.isEmpty()) {
                                initializeAccelDataSets(filteredData);
                            } else {
                                lineDataAccelx =
                                        lineDataAccely = lineDataAccelz = lineDataAccelTotal = null;
                                setupChart(lineChartAccel, "Beschleunigung", 0);
                            }
                            updateAccelChart();
                        });

        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getGyroDataBetween(startTime, endTime)
                .observe(
                        this,
                        filteredData -> {
                            if (filteredData != null && !filteredData.isEmpty()) {
                                initializeGyroDataSets(filteredData);
                            } else {
                                lineDataGyrox =
                                        lineDataGyroy = lineDataGyroz = lineDataGyroTotal = null;
                                setupChart(lineChartGyro, "Gyroskop", 0);
                            }
                            updateAccelChart();
                        });

        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getMagnetDataBetween(startTime, endTime)
                .observe(
                        this,
                        filteredData -> {
                            if (filteredData != null && !filteredData.isEmpty()) {
                                initializeMagDataSets(filteredData);
                            } else {
                                lineDataMagx =
                                        lineDataMagy = lineDataMagz = lineDataMagTotal = null;
                                setupChart(lineChartMag, "Magnetfeld", 0);
                            }
                            updateAccelChart();
                        });
    }

    private String formatCalendarDate(Calendar calendar) {
        return String.format(
                java.util.Locale.GERMANY,
                "%02d.%02d.%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR));
    }

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

        AccelXCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        AccelYCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        AccelZCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        AccelSumCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());

        GyroXCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        GyroYCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        GyroZCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        GyroSumCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());

        MagXCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        MagYCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        MagZCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
        MagSumCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateAccelChart());
    }

    private void observeAccelData() {
        DB.getDatabase(getApplicationContext())
                .sensorDao()
                .getAllAccelData()
                .observe(
                        this,
                        accelDataList -> {
                            if (accelDataList != null && !accelDataList.isEmpty()) {
                                long firstTimestamp = accelDataList.get(0).timestamp;
                                setupChart(lineChartAccel, "Beschleunigung", firstTimestamp);
                                initializeAccelDataSets(accelDataList);
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
                        gyroDataList -> {
                            if (gyroDataList != null && !gyroDataList.isEmpty()) {
                                long firstTimestamp = gyroDataList.get(0).timestamp;
                                setupChart(lineChartGyro, "Gyroskop", firstTimestamp);
                                initializeGyroDataSets(gyroDataList);
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
                        magnetDataList -> {
                            if (magnetDataList != null && !magnetDataList.isEmpty()) {
                                long firstTimestamp = magnetDataList.get(0).timestamp;
                                setupChart(lineChartMag, "Magnetfeld", firstTimestamp);
                                initializeMagDataSets(magnetDataList);
                                updateAccelChart();
                            }
                        });
    }

    private void initializeAccelDataSets(List<AccelData> accelDataList) {
        ArrayList<Entry> entriesAccelX = new ArrayList<>();
        ArrayList<Entry> entriesAccelY = new ArrayList<>();
        ArrayList<Entry> entriesAccelZ = new ArrayList<>();
        ArrayList<Entry> entriesAccelTotal = new ArrayList<>();

        long firstTimestamp = accelDataList.get(0).timestamp;

        for (AccelData data : accelDataList) {
            float elapsedTime = data.timestamp - firstTimestamp;
            entriesAccelX.add(new Entry(elapsedTime, data.accelX));
            entriesAccelY.add(new Entry(elapsedTime, data.accelY));
            entriesAccelZ.add(new Entry(elapsedTime, data.accelZ));

            float sum =
                    (float)
                            Math.sqrt(
                                    data.accelX * data.accelX
                                            + data.accelY * data.accelY
                                            + data.accelZ * data.accelZ);
            entriesAccelTotal.add(new Entry(elapsedTime, sum));
        }

        lineDataAccelx = new LineDataSet(entriesAccelX, "X-Achse");
        lineDataAccelx.setColor(Color.CYAN);
        lineDataAccely = new LineDataSet(entriesAccelY, "Y-Achse");
        lineDataAccely.setColor(Color.WHITE);
        lineDataAccelz = new LineDataSet(entriesAccelZ, "Z-Achse");
        lineDataAccelz.setColor(Color.GREEN);
        lineDataAccelTotal = new LineDataSet(entriesAccelTotal, "Summe");
        lineDataAccelTotal.setColor(Color.RED);
    }

    private void initializeGyroDataSets(List<GyroData> gyroDataList) {
        ArrayList<Entry> entriesGyroX = new ArrayList<>();
        ArrayList<Entry> entriesGyroY = new ArrayList<>();
        ArrayList<Entry> entriesGyroZ = new ArrayList<>();
        ArrayList<Entry> entriesGyroTotal = new ArrayList<>();

        long firstTimestamp = gyroDataList.get(0).timestamp;

        for (GyroData data : gyroDataList) {
            float elapsedTime = data.timestamp - firstTimestamp;
            entriesGyroX.add(new Entry(elapsedTime, data.gyroX));
            entriesGyroY.add(new Entry(elapsedTime, data.gyroY));
            entriesGyroZ.add(new Entry(elapsedTime, data.gyroZ));

            float sum =
                    (float)
                            Math.sqrt(
                                    data.gyroX * data.gyroX
                                            + data.gyroY * data.gyroY
                                            + data.gyroZ * data.gyroZ);
            entriesGyroTotal.add(new Entry(elapsedTime, sum));
        }

        lineDataGyrox = new LineDataSet(entriesGyroX, "X-Achse");
        lineDataGyrox.setColor(Color.CYAN);
        lineDataGyroy = new LineDataSet(entriesGyroY, "Y-Achse");
        lineDataGyroy.setColor(Color.WHITE);
        lineDataGyroz = new LineDataSet(entriesGyroZ, "Z-Achse");
        lineDataGyroz.setColor(Color.GREEN);
        lineDataGyroTotal = new LineDataSet(entriesGyroTotal, "Summe");
        lineDataGyroTotal.setColor(Color.RED);
    }

    private void initializeMagDataSets(List<MagnetData> magnetDataList) {
        ArrayList<Entry> entriesMagX = new ArrayList<>();
        ArrayList<Entry> entriesMagY = new ArrayList<>();
        ArrayList<Entry> entriesMagZ = new ArrayList<>();
        ArrayList<Entry> entriesMagTotal = new ArrayList<>();

        long firstTimestamp = magnetDataList.get(0).timestamp;

        for (MagnetData data : magnetDataList) {
            float elapsedTime = data.timestamp - firstTimestamp;
            entriesMagX.add(new Entry(elapsedTime, data.magnetX));
            entriesMagY.add(new Entry(elapsedTime, data.magnetY));
            entriesMagZ.add(new Entry(elapsedTime, data.magnetZ));

            float sum =
                    (float)
                            Math.sqrt(
                                    data.magnetX * data.magnetX
                                            + data.magnetY * data.magnetY
                                            + data.magnetZ * data.magnetZ);
            entriesMagTotal.add(new Entry(elapsedTime, sum));
        }

        lineDataMagx = new LineDataSet(entriesMagX, "X-Achse");
        lineDataMagx.setColor(Color.CYAN);
        lineDataMagy = new LineDataSet(entriesMagY, "Y-Achse");
        lineDataMagy.setColor(Color.WHITE);
        lineDataMagz = new LineDataSet(entriesMagZ, "Z-Achse");
        lineDataMagz.setColor(Color.GREEN);
        lineDataMagTotal = new LineDataSet(entriesMagTotal, "Summe");
        lineDataMagTotal.setColor(Color.RED);
    }

    private void updateAccelChart() {
        if (lineDataAccelx == null) return;
        if (lineDataGyrox == null) return;
        if (lineDataMagx == null) return;

        LineData lineData = new LineData();
        if (AccelXCheck.isChecked()) {
            lineData.addDataSet(lineDataAccelx);
        }

        if (AccelYCheck.isChecked()) {
            lineData.addDataSet(lineDataAccely);
        }

        if (AccelZCheck.isChecked()) {
            lineData.addDataSet(lineDataAccelz);
        }

        if (AccelSumCheck.isChecked()) {
            lineData.addDataSet(lineDataAccelTotal);
        }

        lineChartAccel.setData(lineData);
        lineChartAccel.invalidate();

        LineData lineData2 = new LineData();
        if (GyroXCheck.isChecked()) {
            lineData2.addDataSet(lineDataGyrox);
        }

        if (GyroYCheck.isChecked()) {
            lineData2.addDataSet(lineDataGyroy);
        }

        if (GyroZCheck.isChecked()) {
            lineData2.addDataSet(lineDataGyroz);
        }

        if (GyroSumCheck.isChecked()) {
            lineData2.addDataSet(lineDataGyroTotal);
        }

        lineChartGyro.setData(lineData2);
        lineChartGyro.invalidate();

        LineData lineData3 = new LineData();
        if (MagXCheck.isChecked()) {
            lineData3.addDataSet(lineDataMagx);
        }

        if (MagYCheck.isChecked()) {
            lineData3.addDataSet(lineDataMagy);
        }

        if (MagZCheck.isChecked()) {
            lineData3.addDataSet(lineDataMagz);
        }

        if (MagSumCheck.isChecked()) {
            lineData3.addDataSet(lineDataMagTotal);
        }

        lineChartMag.setData(lineData3);
        lineChartMag.invalidate();
    }
}
