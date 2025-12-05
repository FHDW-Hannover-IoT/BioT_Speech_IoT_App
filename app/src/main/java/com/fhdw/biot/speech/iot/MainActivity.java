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
import database.DB;
import database.dao.SensorDao;
import database.dao.ValueSensorDAO;
import database.entities.MagnetData;
import database.entities.GyroData;
import database.entities.AccelData;
import database.entities.ValueSensor;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // ---- ROOM: DAOs ---------------------------------------------------------
    private SensorDao sensorDao;          // per-sensor tables (Accel / Gyro / Magnet / Ereignis)
    private ValueSensorDAO valueSensorDao; // combined ValueSensor table (like in old app)

    // ---- Android sensors ----------------------------------------------------
    public SensorManager sensorManager;
    public Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;

    // ---- UI elements --------------------------------------------------------
    private TextView accelXValue, accelYValue, accelZValue;
    private TextView gyroXValue, gyroYValue, gyroZValue;
    private TextView magXValue, magYValue, magZValue;

    // ---- event thresholds / timing -----------------------------------------
    private float accelEventThreshold = 5;
    private float gyroEventThreshold = 5;
    private float magEventThreshold = 10;

    private long lastAccelEventTime = System.currentTimeMillis();
    private long lastGyroEventTime = System.currentTimeMillis();
    private long lastMagEventTime = System.currentTimeMillis();

    private int timeBetweenEvents = 5000;   // ms
    private final char[] axis = {'x', 'y', 'z'};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Ensure content is not under system bars
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom);
                    return insets;
                });

        // ----- Navigation buttons -------------------------------------------
        Button buttonGyro = findViewById(R.id.btnGyro);
        buttonGyro.setOnClickListener(
                view -> startActivity(new Intent(MainActivity.this, GyroActivity.class)));

        Button buttonAccel = findViewById(R.id.btnAccel);
        buttonAccel.setOnClickListener(
                view -> startActivity(new Intent(MainActivity.this, AccelActivity.class)));

        Button buttonMagnet = findViewById(R.id.btnMagnet);
        buttonMagnet.setOnClickListener(
                view -> startActivity(new Intent(MainActivity.this, MagnetActivity.class)));

        ImageButton ereignisButton = findViewById(R.id.notification_button);
        ereignisButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(MainActivity.this, EreignisActivity.class);
                    intent.putExtra("SENSOR_FILTER", "ALL");
                    startActivity(intent);
                });

        Button graphButton = findViewById(R.id.graphenansicht);
        graphButton.setOnClickListener(
                view -> startActivity(new Intent(MainActivity.this, MainGraphActivity.class)));

        // ----- Sensor setup --------------------------------------------------
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // UI bindings for live values
        accelXValue = findViewById(R.id.accelXValue);
        accelYValue = findViewById(R.id.accelYValue);
        accelZValue = findViewById(R.id.accelZValue);

        gyroXValue = findViewById(R.id.gyroXValue);
        gyroYValue = findViewById(R.id.gyroYValue);
        gyroZValue = findViewById(R.id.gyroZValue);

        magXValue = findViewById(R.id.magXValue);
        magYValue = findViewById(R.id.magYValue);
        magZValue = findViewById(R.id.magZValue);

        // ----- ROOM: obtain DB / DAOs ---------------------------------------
        DB db = DB.getDatabase(this);
        sensorDao = db.sensorDao();
        valueSensorDao = db.valueSensorDao();   // <- new DAO for ValueSensor table
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
        // Optionally unregister if you want to stop recording in background
        // sensorManager.unregisterListener(this);
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {

            // -----------------------------------------------------------------
            // ACCELEROMETER
            // -----------------------------------------------------------------
            case Sensor.TYPE_ACCELEROMETER: {
                accelXValue.setText(getString(R.string.beschleunigung_x, event.values[0]));
                accelYValue.setText(getString(R.string.beschleunigung_y, event.values[1]));
                accelZValue.setText(getString(R.string.beschleunigung_z, event.values[2]));

                long now = System.currentTimeMillis();

                // Entity for accel_data table
                AccelData accelData = new AccelData();
                accelData.timestamp = now;
                accelData.accelX = event.values[0];
                accelData.accelY = event.values[1];
                accelData.accelZ = event.values[2];

                // Write to accel_data + events + ValueSensor in background
                DB.databaseWriteExecutor.execute(() -> {
                    // store raw accel data
                    sensorDao.insert(accelData);

                    // threshold-based event rows
                    for (int i = 0; i < 3; i++) {
                        if (Math.abs(event.values[i]) > accelEventThreshold &&
                                now > lastAccelEventTime + timeBetweenEvents) {

                            lastAccelEventTime = now;
                            SensorEreignis accel_event =
                                    new SensorEreignis(
                                            accelData.timestamp,
                                            "ACCEL",
                                            event.values[i],
                                            "accelEvent_" + accelData.timestamp,
                                            MainActivity.this,
                                            axis[i]);

                            sensorDao.insert(accel_event.getEreignisData());
                        }
                    }

                    // also store combined row in ValueSensor (old app style)
                    ValueSensor vs = new ValueSensor();
                    vs.value1 = event.values[0];
                    vs.value2 = event.values[1];
                    vs.value3 = event.values[2];
                    valueSensorDao.insert(vs);
                });
                break;
            }

            // -----------------------------------------------------------------
            // GYROSCOPE
            // -----------------------------------------------------------------
            case Sensor.TYPE_GYROSCOPE: {
                gyroXValue.setText(getString(R.string.gyro_x, event.values[0]));
                gyroYValue.setText(getString(R.string.gyro_y, event.values[1]));
                gyroZValue.setText(getString(R.string.gyro_z, event.values[2]));

                long now = System.currentTimeMillis();

                GyroData gyroData = new GyroData();
                gyroData.timestamp = now;
                gyroData.gyroX = event.values[0];
                gyroData.gyroY = event.values[1];
                gyroData.gyroZ = event.values[2];

                DB.databaseWriteExecutor.execute(() -> {
                    sensorDao.insert(gyroData);

                    for (int i = 0; i < 3; i++) {
                        if (Math.abs(event.values[i]) > gyroEventThreshold &&
                                now > lastGyroEventTime + timeBetweenEvents) {

                            lastGyroEventTime = now;
                            SensorEreignis gyro_event =
                                    new SensorEreignis(
                                            gyroData.timestamp,
                                            "GYRO",
                                            event.values[i],
                                            "gyroEvent_" + gyroData.timestamp,
                                            MainActivity.this,
                                            axis[i]);

                            sensorDao.insert(gyro_event.getEreignisData());
                        }
                    }

                    // also store combined row in ValueSensor
                    ValueSensor vs = new ValueSensor();
                    vs.value4 = event.values[0];
                    vs.value5 = event.values[1];
                    vs.value6 = event.values[2];
                    valueSensorDao.insert(vs);
                });
                break;
            }

            // -----------------------------------------------------------------
            // MAGNETIC FIELD
            // -----------------------------------------------------------------
            case Sensor.TYPE_MAGNETIC_FIELD: {
                magXValue.setText(getString(R.string.magnet_x, event.values[0]));
                magYValue.setText(getString(R.string.magnet_y, event.values[1]));
                magZValue.setText(getString(R.string.magnet_z, event.values[2]));

                long now = System.currentTimeMillis();

                MagnetData magnetData = new MagnetData();
                magnetData.timestamp = now;
                magnetData.magnetX = event.values[0];
                magnetData.magnetY = event.values[1];
                magnetData.magnetZ = event.values[2];

                DB.databaseWriteExecutor.execute(() -> {
                    sensorDao.insert(magnetData);

                    for (int i = 0; i < 3; i++) {
                        if (Math.abs(event.values[i]) > magEventThreshold &&
                                now > lastMagEventTime + timeBetweenEvents) {

                            lastMagEventTime = now;
                            SensorEreignis mag_event =
                                    new SensorEreignis(
                                            magnetData.timestamp,
                                            "MAGNET",
                                            event.values[i],
                                            "magEvent_" + magnetData.timestamp,
                                            MainActivity.this,
                                            axis[i]);

                            sensorDao.insert(mag_event.getEreignisData());
                        }
                    }

                    // Optional: also store timestamp in ValueSensor.Zeit
                    ValueSensor vs = new ValueSensor();
                    // store millis as string; you can format to ISO if you prefer
                    vs.value7 = String.valueOf(now);
                    valueSensorDao.insert(vs);
                });
                break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // React to accuracy changes if you need to
    }
}
