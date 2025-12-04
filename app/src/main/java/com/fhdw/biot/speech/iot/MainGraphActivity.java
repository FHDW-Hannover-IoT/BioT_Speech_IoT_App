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
import java.util.List;

public class MainGraphActivity extends BaseChartActivity {

    private LineChart lineChartAccel, lineChartGyro, lineChartMag;
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

        Button xBisButton = findViewById(R.id.button_Accel_bis);
        datePickerHandler.setupButton(xBisButton);

        Button xVonButton = findViewById(R.id.button_Accel_von);
        datePickerHandler.setupButton(xVonButton);

        Button yBisButton = findViewById(R.id.button_Gyro_bis);
        datePickerHandler.setupButton(yBisButton);

        Button yVonButton = findViewById(R.id.button_Gyro_von);
        datePickerHandler.setupButton(yVonButton);

        Button zBisButton = findViewById(R.id.button_Mag_bis);
        datePickerHandler.setupButton(zBisButton);

        Button zVonButton = findViewById(R.id.button_Mag_von);
        datePickerHandler.setupButton(zVonButton);

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

        loadDataFromDatabase();
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

    private void loadDataFromDatabase() {
        DB.databaseWriteExecutor.execute(
                () -> {
                    List<AccelData> accelDataList =
                            DB.getDatabase(getApplicationContext()).sensorDao().getAllAccelData();

                    if (!accelDataList.isEmpty()) {
                        startTime = accelDataList.get(0).timestamp;
                    }

                    List<GyroData> gyroDataList =
                            DB.getDatabase(getApplicationContext()).sensorDao().getAllGyroData();

                    if (!gyroDataList.isEmpty()) {
                        startTime = gyroDataList.get(0).timestamp;
                    }

                    List<MagnetData> magnetDataList =
                            DB.getDatabase(getApplicationContext()).sensorDao().getAllMagnetData();

                    if (!magnetDataList.isEmpty()) {
                        startTime = accelDataList.get(0).timestamp;
                    }

                    runOnUiThread(
                            () -> {
                                if (startTime > 0) {
                                    setupChart(lineChartAccel, "Beschleunigung", startTime);
                                    setupChart(lineChartGyro, "Gyroskop", startTime);
                                    setupChart(lineChartMag, "Magnetfeld", startTime);
                                }
                                displayDataInCharts(accelDataList, gyroDataList, magnetDataList);
                            });
                });
    }

    private void displayDataInCharts(
            List<AccelData> accelDataList,
            List<GyroData> gyroDataList,
            List<MagnetData> magnetDataList) {
        if (accelDataList.isEmpty()) {
            return;
        }

        ArrayList<Entry> entriesAccelX = new ArrayList<>();
        ArrayList<Entry> entriesAccelY = new ArrayList<>();
        ArrayList<Entry> entriesAccelZ = new ArrayList<>();
        ArrayList<Entry> entriesAccelTotal = new ArrayList<>();

        ArrayList<Entry> entriesGyroX = new ArrayList<>();
        ArrayList<Entry> entriesGyroY = new ArrayList<>();
        ArrayList<Entry> entriesGyroZ = new ArrayList<>();
        ArrayList<Entry> entriesGyroTotal = new ArrayList<>();

        ArrayList<Entry> entriesMagX = new ArrayList<>();
        ArrayList<Entry> entriesMagY = new ArrayList<>();
        ArrayList<Entry> entriesMagZ = new ArrayList<>();
        ArrayList<Entry> entriesMagTotal = new ArrayList<>();

        long firstTimestamp = accelDataList.get(0).timestamp;
        long secondTimestamp = gyroDataList.get(0).timestamp;
        long thirdTimestamp = magnetDataList.get(0).timestamp;

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

        for (GyroData data : gyroDataList) {
            float elapsedTime = data.timestamp - secondTimestamp;
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

        for (MagnetData data : magnetDataList) {
            float elapsedTime = data.timestamp - thirdTimestamp;
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

        lineDataAccelx = new LineDataSet(entriesAccelX, "X-Achse");
        lineDataAccelx.setColor(Color.CYAN);
        lineDataAccely = new LineDataSet(entriesAccelY, "Y-Achse");
        lineDataAccely.setColor(Color.WHITE);
        lineDataAccelz = new LineDataSet(entriesAccelZ, "Z-Achse");
        lineDataAccelz.setColor(Color.GREEN);
        lineDataAccelTotal = new LineDataSet(entriesAccelTotal, "Summe");
        lineDataAccelTotal.setColor(Color.RED);

        lineDataGyrox = new LineDataSet(entriesGyroX, "X-Achse");
        lineDataGyrox.setColor(Color.CYAN);
        lineDataGyroy = new LineDataSet(entriesGyroY, "Y-Achse");
        lineDataGyroy.setColor(Color.WHITE);
        lineDataGyroz = new LineDataSet(entriesGyroZ, "Z-Achse");
        lineDataGyroz.setColor(Color.GREEN);
        lineDataGyroTotal = new LineDataSet(entriesGyroTotal, "Summe");
        lineDataGyroTotal.setColor(Color.RED);

        lineDataMagx = new LineDataSet(entriesMagX, "X-Achse");
        lineDataMagx.setColor(Color.CYAN);
        lineDataMagy = new LineDataSet(entriesMagY, "Y-Achse");
        lineDataMagy.setColor(Color.WHITE);
        lineDataMagz = new LineDataSet(entriesMagZ, "Z-Achse");
        lineDataMagz.setColor(Color.GREEN);
        lineDataMagTotal = new LineDataSet(entriesMagTotal, "Summe");
        lineDataMagTotal.setColor(Color.RED);

        updateAccelChart();
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
