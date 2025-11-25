package com.fhdw.biot.speech.iot;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MagnetActivity extends BaseChartActivity {

    private LineChart lineChartMagnetX, lineChartMagnetY, lineChartMagnetZ;
    private Calendar dateFromCalendar;
    private Calendar dateToCalendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_magnetfeld);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.magnet),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        Button buttonGyro = findViewById(R.id.btnPrevGyro);
        buttonGyro.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MagnetActivity.this, GyroActivity.class);
                    startActivity(intent);
                });

        Button buttonAccel = findViewById(R.id.btnNextAccel);
        buttonAccel.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MagnetActivity.this, AccelActivity.class);
                    startActivity(intent);
                });

        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MagnetActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MagnetActivity.this, EreignisActivity.class);
                    intent.putExtra("SENSOR_FILTER", "MAGNET");
                    startActivity(intent);
                });

        lineChartMagnetX = findViewById(R.id.lineChartMagnetX);
        lineChartMagnetY = findViewById(R.id.lineChartMagnetY);
        lineChartMagnetZ = findViewById(R.id.lineChartMagnetZ);

        // Initial chart setup
        setupChart(lineChartMagnetX, "X-Achse", 0);
        setupChart(lineChartMagnetY, "Y-Achse", 0);
        setupChart(lineChartMagnetZ, "Z-Achse", 0);

        // Setup DatePickers
        setupDatePickers();

        // Observe LiveData and update charts automatically
        DB.getDatabase(getApplicationContext()).sensorDao().getAllMagnetData().observe(this, magnetDataList -> {
            if (magnetDataList != null && !magnetDataList.isEmpty()) {
                long firstTimestamp = magnetDataList.get(0).timestamp;
                setupChart(lineChartMagnetX, "X-Achse", firstTimestamp);
                setupChart(lineChartMagnetY, "Y-Achse", firstTimestamp);
                setupChart(lineChartMagnetZ, "Z-Achse", firstTimestamp);
                displayDataInCharts(magnetDataList);
            }
        });
    }

    private void setupDatePickers() {
        Button xVonButton = findViewById(R.id.button_x_von);
        Button xBisButton = findViewById(R.id.button_x_bis);
        Button yVonButton = findViewById(R.id.button_y_von);
        Button yBisButton = findViewById(R.id.button_y_bis);
        Button zVonButton = findViewById(R.id.button_z_von);
        Button zBisButton = findViewById(R.id.button_z_bis);

        // Initialisiere die Calendar-Objekte
        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        // Beobachte das älteste Datum aus der Datenbank
        DB.getDatabase(getApplicationContext()).sensorDao().getOldestMagnetTimestamp().observe(this, oldestTimestamp -> {
            if (oldestTimestamp != null && oldestTimestamp > 0) {
                dateFromCalendar.setTimeInMillis(oldestTimestamp);
                // Setze das "von"-Datum auf das älteste Datum
                setupFromDatePickers(xVonButton, yVonButton, zVonButton);
            }
        });

        // Setze das "bis"-Datum auf das aktuelle Datum
        setupToDatePickers(xBisButton, yBisButton, zBisButton);
    }

    private void setupFromDatePickers(Button xVonButton, Button yVonButton, Button zVonButton) {
        DatePickerHandler.createForButton(xVonButton, calendar -> {
            dateFromCalendar = calendar;
            updateChartsWithDateFilter();
        }, MagnetActivity.this);

        DatePickerHandler.createForButton(yVonButton, calendar -> {
            dateFromCalendar = calendar;
            updateChartsWithDateFilter();
        }, MagnetActivity.this);

        DatePickerHandler.createForButton(zVonButton, calendar -> {
            dateFromCalendar = calendar;
            updateChartsWithDateFilter();
        }, MagnetActivity.this);

        xVonButton.setText(formatCalendarDate(dateFromCalendar));
        yVonButton.setText(formatCalendarDate(dateFromCalendar));
        zVonButton.setText(formatCalendarDate(dateFromCalendar));
    }

    private void setupToDatePickers(Button xBisButton, Button yBisButton, Button zBisButton) {
        DatePickerHandler.createForButton(xBisButton, calendar -> {
            dateToCalendar = calendar;
            updateChartsWithDateFilter();
        }, MagnetActivity.this);

        DatePickerHandler.createForButton(yBisButton, calendar -> {
            dateToCalendar = calendar;
            updateChartsWithDateFilter();
        }, MagnetActivity.this);

        DatePickerHandler.createForButton(zBisButton, calendar -> {
            dateToCalendar = calendar;
            updateChartsWithDateFilter();
        }, MagnetActivity.this);

        xBisButton.setText(formatCalendarDate(dateToCalendar));
        yBisButton.setText(formatCalendarDate(dateToCalendar));
        zBisButton.setText(formatCalendarDate(dateToCalendar));
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

        DB.getDatabase(getApplicationContext()).sensorDao().getMagnetDataBetween(
                dateFromCalendar.getTimeInMillis(),
                adjustedToCalendar.getTimeInMillis()
        ).observe(this, filteredData -> {
            if (filteredData != null && !filteredData.isEmpty()) {
                long firstTimestamp = filteredData.get(0).timestamp;
                setupChart(lineChartMagnetX, "X-Achse", firstTimestamp);
                setupChart(lineChartMagnetY, "Y-Achse", firstTimestamp);
                setupChart(lineChartMagnetZ, "Z-Achse", firstTimestamp);
                displayDataInCharts(filteredData);
            } else {
                lineChartMagnetX.clear();
                lineChartMagnetY.clear();
                lineChartMagnetZ.clear();
            }
        });
    }

    private String formatCalendarDate(Calendar calendar) {
        return String.format(java.util.Locale.GERMANY, "%02d.%02d.%04d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        );
    }

    private void displayDataInCharts(List<MagnetData> magnetDataList) {
        if (magnetDataList == null || magnetDataList.isEmpty()) {
            return;
        }

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        long firstTimestamp = magnetDataList.get(0).timestamp;

        for (MagnetData data : magnetDataList) {
            float elapsedTime = data.timestamp - firstTimestamp;
            entriesX.add(new Entry(elapsedTime, data.magnetX));
            entriesY.add(new Entry(elapsedTime, data.magnetY));
            entriesZ.add(new Entry(elapsedTime, data.magnetZ));
        }

        setData(lineChartMagnetX, entriesX, "X-Achse", Color.WHITE);
        setData(lineChartMagnetY, entriesY, "Y-Achse", Color.WHITE);
        setData(lineChartMagnetZ, entriesZ, "Z-Achse", Color.WHITE);
    }
}