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

public class GyroActivity extends BaseChartActivity {

    private LineChart lineChartGyroX, lineChartGyroY, lineChartGyroZ;
    private Calendar dateFromCalendar;
    private Calendar dateToCalendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gyroskop);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.gyro),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        Button buttonAccel = findViewById(R.id.btnPrevAccel);
        buttonAccel.setOnClickListener(
                view -> {
                    Intent intent = new Intent(GyroActivity.this, AccelActivity.class);
                    startActivity(intent);
                });

        Button buttonMagnet = findViewById(R.id.btnNextMagnet);
        buttonMagnet.setOnClickListener(
                view -> {
                    Intent intent = new Intent(GyroActivity.this, MagnetActivity.class);
                    startActivity(intent);
                });

        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(GyroActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(GyroActivity.this, EreignisActivity.class);
                    intent.putExtra("SENSOR_FILTER", "GYRO");
                    startActivity(intent);
                });

        lineChartGyroX = findViewById(R.id.lineChartGyroX);
        lineChartGyroY = findViewById(R.id.lineChartGyroY);
        lineChartGyroZ = findViewById(R.id.lineChartGyroZ);

        // Initial chart setup
        setupChart(lineChartGyroX, "X-Achse", 0);
        setupChart(lineChartGyroY, "Y-Achse", 0);
        setupChart(lineChartGyroZ, "Z-Achse", 0);

        // Setup DatePickers
        setupDatePickers();

        // Observe LiveData and update charts automatically
        DB.getDatabase(getApplicationContext()).sensorDao().getAllGyroData().observe(this, gyroDataList -> {
            if (gyroDataList != null && !gyroDataList.isEmpty()) {
                long firstTimestamp = gyroDataList.get(0).timestamp;
                setupChart(lineChartGyroX, "X-Achse", firstTimestamp);
                setupChart(lineChartGyroY, "Y-Achse", firstTimestamp);
                setupChart(lineChartGyroZ, "Z-Achse", firstTimestamp);
                displayDataInCharts(gyroDataList);
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

        dateFromCalendar = Calendar.getInstance();
        dateToCalendar = Calendar.getInstance();

        DB.getDatabase(getApplicationContext()).sensorDao().getOldestGyroTimestamp().observe(this, oldestTimestamp -> {
            if (oldestTimestamp != null && oldestTimestamp > 0) {
                dateFromCalendar.setTimeInMillis(oldestTimestamp);
                setupFromDatePickers(xVonButton, yVonButton, zVonButton);
            }
        });

        setupToDatePickers(xBisButton, yBisButton, zBisButton);
    }

    private void setupFromDatePickers(Button xVonButton, Button yVonButton, Button zVonButton) {
        DatePickerHandler.createForButton(xVonButton, calendar -> {
            dateFromCalendar = calendar;
            updateChartsWithDateFilter();
        }, GyroActivity.this);

        DatePickerHandler.createForButton(yVonButton, calendar -> {
            dateFromCalendar = calendar;
            updateChartsWithDateFilter();
        }, GyroActivity.this);

        DatePickerHandler.createForButton(zVonButton, calendar -> {
            dateFromCalendar = calendar;
            updateChartsWithDateFilter();
        }, GyroActivity.this);

        // Setze anfängliches Datum
        xVonButton.setText(formatCalendarDate(dateFromCalendar));
        yVonButton.setText(formatCalendarDate(dateFromCalendar));
        zVonButton.setText(formatCalendarDate(dateFromCalendar));
    }

    private void setupToDatePickers(Button xBisButton, Button yBisButton, Button zBisButton) {
        DatePickerHandler.createForButton(xBisButton, calendar -> {
            dateToCalendar = calendar;
            updateChartsWithDateFilter();
        }, GyroActivity.this);

        DatePickerHandler.createForButton(yBisButton, calendar -> {
            dateToCalendar = calendar;
            updateChartsWithDateFilter();
        }, GyroActivity.this);

        DatePickerHandler.createForButton(zBisButton, calendar -> {
            dateToCalendar = calendar;
            updateChartsWithDateFilter();
        }, GyroActivity.this);

        // Setze anfängliches Datum (heute)
        xBisButton.setText(formatCalendarDate(dateToCalendar));
        yBisButton.setText(formatCalendarDate(dateToCalendar));
        zBisButton.setText(formatCalendarDate(dateToCalendar));
    }

    private void updateChartsWithDateFilter() {
        if (dateFromCalendar == null || dateToCalendar == null) {
            return;
        }

        // Setze "bis"-Datum auf Ende des Tages, um den ganzen Tag zu erfassen
        Calendar adjustedToCalendar = (Calendar) dateToCalendar.clone();
        adjustedToCalendar.add(Calendar.DAY_OF_MONTH, 0);
        adjustedToCalendar.set(Calendar.HOUR_OF_DAY, 23);
        adjustedToCalendar.set(Calendar.MINUTE, 59);
        adjustedToCalendar.set(Calendar.SECOND, 59);

        DB.getDatabase(getApplicationContext()).sensorDao().getGyroDataBetween(
                dateFromCalendar.getTimeInMillis(),
                adjustedToCalendar.getTimeInMillis()
        ).observe(this, filteredData -> {
            if (filteredData != null && !filteredData.isEmpty()) {
                long firstTimestamp = filteredData.get(0).timestamp;
                setupChart(lineChartGyroX, "X-Achse", firstTimestamp);
                setupChart(lineChartGyroY, "Y-Achse", firstTimestamp);
                setupChart(lineChartGyroZ, "Z-Achse", firstTimestamp);
                displayDataInCharts(filteredData);
            } else {
                // Leere Charts, wenn kein Datum im Bereich vorhanden
                lineChartGyroX.clear();
                lineChartGyroY.clear();
                lineChartGyroZ.clear();
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

    private void displayDataInCharts(List<GyroData> gyroDataList) {
        if (gyroDataList == null || gyroDataList.isEmpty()) {
            return;
        }

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        long firstTimestamp = gyroDataList.get(0).timestamp;

        for (GyroData data : gyroDataList) {
            float elapsedTime = data.timestamp - firstTimestamp;
            entriesX.add(new Entry(elapsedTime, data.gyroX));
            entriesY.add(new Entry(elapsedTime, data.gyroY));
            entriesZ.add(new Entry(elapsedTime, data.gyroZ));
        }

        setData(lineChartGyroX, entriesX, "X-Achse", Color.WHITE);
        setData(lineChartGyroY, entriesY, "Y-Achse", Color.WHITE);
        setData(lineChartGyroZ, entriesZ, "Z-Achse", Color.WHITE);
    }
}
