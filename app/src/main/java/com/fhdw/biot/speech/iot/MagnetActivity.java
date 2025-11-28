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
import java.util.List;

public class MagnetActivity extends BaseChartActivity {

    private LineChart lineChartMagnetX, lineChartMagnetY, lineChartMagnetZ;
    private long startTime = 0;

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

        DatePickerHandler datePickerHandler = new DatePickerHandler(MagnetActivity.this);

        Button xBisButton = findViewById(R.id.button_x_bis);
        datePickerHandler.setupButton(xBisButton);

        Button xVonButton = findViewById(R.id.button_x_von);
        datePickerHandler.setupButton(xVonButton);

        Button yBisButton = findViewById(R.id.button_y_bis);
        datePickerHandler.setupButton(yBisButton);

        Button yVonButton = findViewById(R.id.button_y_von);
        datePickerHandler.setupButton(yVonButton);

        Button zBisButton = findViewById(R.id.button_z_bis);
        datePickerHandler.setupButton(zBisButton);

        Button zVonButton = findViewById(R.id.button_z_von);
        datePickerHandler.setupButton(zVonButton);

        lineChartMagnetX = findViewById(R.id.lineChartMagnetX);
        lineChartMagnetY = findViewById(R.id.lineChartMagnetY);
        lineChartMagnetZ = findViewById(R.id.lineChartMagnetZ);

        ImageButton resetAccel = findViewById(R.id.resetX);
        resetAccel.setOnClickListener(
                view -> {
                    lineChartMagnetX.fitScreen();
                    xBisButton.setText("");
                    xVonButton.setText("");
                });

        ImageButton resetGyro = findViewById(R.id.resetY);
        resetGyro.setOnClickListener(
                view -> {
                    lineChartMagnetY.fitScreen();
                    yBisButton.setText("");
                    zVonButton.setText("");
                });

        ImageButton resetMagnet = findViewById(R.id.resetZ);
        resetMagnet.setOnClickListener(
                view -> {
                    lineChartMagnetZ.fitScreen();
                    zBisButton.setText("");
                    zVonButton.setText("");
                });

        // Initial chart setup
        setupChart(lineChartMagnetX, "X-Achse", 0);
        setupChart(lineChartMagnetY, "Y-Achse", 0);
        setupChart(lineChartMagnetZ, "Z-Achse", 0);

        loadDataFromDatabase();
    }

    private void loadDataFromDatabase() {
        DB.databaseWriteExecutor.execute(
                () -> {
                    List<MagnetData> magnetDataList =
                            DB.getDatabase(getApplicationContext()).sensorDao().getAllMagnetData();

                    if (!magnetDataList.isEmpty()) {
                        startTime = magnetDataList.get(0).timestamp;
                    }

                    runOnUiThread(
                            () -> {
                                // Re-setup charts with the formatter now that we have a start time
                                if (startTime > 0) {
                                    setupChart(lineChartMagnetX, "X-Achse", startTime);
                                    setupChart(lineChartMagnetY, "Y-Achse", startTime);
                                    setupChart(lineChartMagnetZ, "Z-Achse", startTime);
                                }
                                displayDataInCharts(magnetDataList);
                            });
                });
    }

    private void displayDataInCharts(List<MagnetData> magnetDataList) {
        if (magnetDataList.isEmpty()) {
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
