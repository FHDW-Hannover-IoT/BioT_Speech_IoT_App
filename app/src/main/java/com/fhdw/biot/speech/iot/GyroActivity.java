package com.fhdw.biot.speech.iot;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class GyroActivity extends BaseChartActivity {

    private LineChart lineChartGyroX, lineChartGyroY, lineChartGyroZ;
    private long startTime = 0;

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

        lineChartGyroX = findViewById(R.id.lineChartGyroX);
        lineChartGyroY = findViewById(R.id.lineChartGyroY);
        lineChartGyroZ = findViewById(R.id.lineChartGyroZ);

        // Initial chart setup
        setupChart(lineChartGyroX, "X-Achse", 0);
        setupChart(lineChartGyroY, "Y-Achse", 0);
        setupChart(lineChartGyroZ, "Z-Achse", 0);

        loadDataFromDatabase();
    }

    private void loadDataFromDatabase() {
        DB.databaseWriteExecutor.execute(
                () -> {
                    List<GyroData> gyroDataList =
                            DB.getDatabase(getApplicationContext()).sensorDao().getAllGyroData();

                    if (!gyroDataList.isEmpty()) {
                        startTime = gyroDataList.get(0).timestamp;
                    }

                    runOnUiThread(
                            () -> {
                                if (startTime > 0) {
                                    setupChart(lineChartGyroX, "X-Achse", startTime);
                                    setupChart(lineChartGyroY, "Y-Achse", startTime);
                                    setupChart(lineChartGyroZ, "Z-Achse", startTime);
                                }
                                displayDataInCharts(gyroDataList);
                            });
                });
    }

    private void displayDataInCharts(List<GyroData> gyroDataList) {
        if (gyroDataList.isEmpty()) {
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
