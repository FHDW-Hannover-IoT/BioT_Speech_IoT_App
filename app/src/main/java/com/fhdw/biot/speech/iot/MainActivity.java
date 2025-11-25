package com.fhdw.biot.speech.iot;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorDao sensorDao;

    public SensorManager sensorManager;
    public Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;

    private TextView accelXValue, accelYValue, accelZValue;
    private TextView gyroXValue, gyroYValue, gyroZValue;
    private TextView magXValue, magYValue, magZValue;

    private float accelEventThreshold = 15;
    private float gyroEventThreshold = 15;
    private float magEventThreshold = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        Button buttonGyro = findViewById(R.id.btnGyro);
        buttonGyro.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainActivity.this, GyroActivity.class);
                    startActivity(intent);
                });

        Button buttonAccel = findViewById(R.id.btnAccel);
        buttonAccel.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainActivity.this, AccelActivity.class);
                    startActivity(intent);
                });

        Button buttonMagnet = findViewById(R.id.btnMagnet);
        buttonMagnet.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainActivity.this, MagnetActivity.class);
                    startActivity(intent);
                });

        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainActivity.this, EreignisActivity.class);
                    intent.putExtra("SENSOR_FILTER", "ALL");
                    startActivity(intent);
                });

        Button graphButton = findViewById(R.id.graphenansicht);
        graphButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainActivity.this, MainGraphActivity.class);
                    startActivity(intent);
                });

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        accelXValue = findViewById(R.id.accelXValue);
        accelYValue = findViewById(R.id.accelYValue);
        accelZValue = findViewById(R.id.accelZValue);

        gyroXValue = findViewById(R.id.gyroXValue);
        gyroYValue = findViewById(R.id.gyroYValue);
        gyroZValue = findViewById(R.id.gyroZValue);

        magXValue = findViewById(R.id.magXValue);
        magYValue = findViewById(R.id.magYValue);
        magZValue = findViewById(R.id.magZValue);

        DB db = DB.getDatabase(this);
        sensorDao = db.sensorDao();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accelXValue.setText(getString(R.string.beschleunigung_x, event.values[0]));
                accelYValue.setText(getString(R.string.beschleunigung_y, event.values[1]));
                accelZValue.setText(getString(R.string.beschleunigung_z, event.values[2]));

                AccelData accelData = new AccelData();
                accelData.accelX = event.values[0];
                accelData.accelY = event.values[1];
                accelData.accelZ = event.values[2];
                accelData.timestamp = System.currentTimeMillis();

                for (int i = 0; i < 2; i++) {
                    if (Math.abs(event.values[i]) > accelEventThreshold) {
                        SensorEreigniss mag_event =
                                new SensorEreigniss(
                                        accelData.timestamp,
                                        "mag",
                                        event.values[i],
                                        "magEvent_" + accelData.timestamp,
                                        this);
                    }
                }

                DB.databaseWriteExecutor.execute(() -> sensorDao.insert(accelData));

                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroXValue.setText(getString(R.string.gyro_x, event.values[0]));
                gyroYValue.setText(getString(R.string.gyro_y, event.values[1]));
                gyroZValue.setText(getString(R.string.gyro_z, event.values[2]));
                GyroData gyroData = new GyroData();
                gyroData.gyroX = event.values[0];
                gyroData.gyroY = event.values[1];
                gyroData.gyroZ = event.values[2];
                gyroData.timestamp = System.currentTimeMillis();

                for (int i = 0; i < 3; i++) {
                    if (Math.abs(event.values[i]) > gyroEventThreshold) {
                        SensorEreigniss mag_event =
                                new SensorEreigniss(
                                        gyroData.timestamp,
                                        "mag",
                                        event.values[i],
                                        "magEvent_" + gyroData.timestamp,
                                        this);
                    }
                }

                DB.databaseWriteExecutor.execute(() -> sensorDao.insert(gyroData));
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magXValue.setText(getString(R.string.magnet_x, event.values[0]));
                magYValue.setText(getString(R.string.magnet_y, event.values[1]));
                magZValue.setText(getString(R.string.magnet_z, event.values[2]));
                MagnetData magnetData = new MagnetData();
                magnetData.magnetX = event.values[0];
                magnetData.magnetY = event.values[1];
                magnetData.magnetZ = event.values[2];
                magnetData.timestamp = System.currentTimeMillis();

                for (int i = 0; i < 2; i++) {
                    if (Math.abs(event.values[i]) > magEventThreshold) {
                        SensorEreigniss mag_event =
                                new SensorEreigniss(
                                        magnetData.timestamp,
                                        "mag",
                                        event.values[i],
                                        "magEvent_" + magnetData.timestamp,
                                        this);
                    }
                }

                DB.databaseWriteExecutor.execute(() -> sensorDao.insert(magnetData));
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }
}
