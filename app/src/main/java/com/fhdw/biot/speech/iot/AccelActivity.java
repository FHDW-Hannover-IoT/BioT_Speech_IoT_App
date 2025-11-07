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

public class AccelActivity extends BaseChartActivity {

    private LineChart lineChartAccelX, lineChartAccelY, lineChartAccelZ;
    private long startTime = 0;
//Test
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beschleunigung);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.accel),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        Button buttonMagnet = findViewById(R.id.btnPrevMagnet);
        buttonMagnet.setOnClickListener(
                view -> {
                    Intent intent = new Intent(AccelActivity.this, MagnetActivity.class);
                    startActivity(intent);
                });

        Button buttonGyro = findViewById(R.id.btnNextGyro);
        buttonGyro.setOnClickListener(
                view -> {
                    Intent intent = new Intent(AccelActivity.this, GyroActivity.class);
                    startActivity(intent);
                });

        lineChartAccelX = findViewById(R.id.lineChartAccelX);
        lineChartAccelY = findViewById(R.id.lineChartAccelY);
        lineChartAccelZ = findViewById(R.id.lineChartAccelZ);

        // Initial chart
        setupChart(lineChartAccelX, "X-Achse", 0);
        setupChart(lineChartAccelY, "Y-Achse", 0);
        setupChart(lineChartAccelZ, "Z-Achse", 0);

        loadDataFromDatabase();
    }

    private void loadDataFromDatabase() {
        DB.databaseWriteExecutor.execute(
                () -> {
                    List<AccelData> accelDataList =
                            DB.getDatabase(getApplicationContext()).sensorDao().getAllAccelData();

                    if (!accelDataList.isEmpty()) {
                        startTime = accelDataList.get(0).timestamp;
                    }

                    runOnUiThread(
                            () -> {
                                // Charts with the formatter
                                if (startTime > 0) {
                                    setupChart(lineChartAccelX, "X-Achse", startTime);
                                    setupChart(lineChartAccelY, "Y-Achse", startTime);
                                    setupChart(lineChartAccelZ, "Z-Achse", startTime);
                                }
                                displayDataInCharts(accelDataList);
                            });
                });
    }

    private void displayDataInCharts(List<AccelData> accelDataList) {
        if (accelDataList.isEmpty()) {
            return;
        }

        ArrayList<Entry> entriesX = new ArrayList<>();
        ArrayList<Entry> entriesY = new ArrayList<>();
        ArrayList<Entry> entriesZ = new ArrayList<>();

        long firstTimestamp = accelDataList.get(0).timestamp;

        for (AccelData data : accelDataList) {
            float elapsedTime = data.timestamp - firstTimestamp;
            entriesX.add(new Entry(elapsedTime, data.accelX));
            entriesY.add(new Entry(elapsedTime, data.accelY));
            entriesZ.add(new Entry(elapsedTime, data.accelZ));
        }

        setData(lineChartAccelX, entriesX, "X-Achse", Color.WHITE);
        setData(lineChartAccelY, entriesY, "Y-Achse", Color.WHITE);
        setData(lineChartAccelZ, entriesZ, "Z-Achse", Color.WHITE);
    }
}
