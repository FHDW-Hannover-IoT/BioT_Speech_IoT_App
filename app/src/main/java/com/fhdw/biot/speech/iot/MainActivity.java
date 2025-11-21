package com.fhdw.biot.speech.iot;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

import database.InitDb;
import database.ValueSensor;

public class MainActivity extends AppCompatActivity {

    private static final String Broker_URL = "tcp://10.0.2.2:1883";
    private static final String Client_ID = "Nutzer";
    private MqttHandler mqttHandler;
    private TextView datenBewegung;
    private TextView datenZeit;
    private TextView datenGyro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        mqttHandler = new MqttHandler();
        datenBewegung = findViewById(R.id.Daten_Bewegung);
        datenZeit = findViewById(R.id.Daten_Zeit);
        datenGyro = findViewById(R.id.Daten_Gyro);

        mqttHandler.setMessageListener((topic, message) -> {
            System.out.println("MQTT DEBUG: topic=" + topic + " | msg=" + message);

            runOnUiThread(() -> {
                System.out.println("UI DEBUG: ENTER runOnUiThread");

                switch (topic) {
                    case "Sensor/Bewegung":
                        datenBewegung.setText(message);
                        String[] parts = message.split(",");

                        try {
                            ValueSensor sensor = new ValueSensor();
                            sensor.value1 = Float.parseFloat(parts[0]);
                            sensor.value2 = Float.parseFloat(parts[1]);
                            sensor.value3 = Float.parseFloat(parts[2]);


                            new Thread(() -> {
                                try {
                                    InitDb.appDatabase.valueDao().insert(sensor);
                                } catch (Exception e) {
                                    System.out.println("THREAD ERROR: " + e.getMessage());
                                }
                            }).start();

                        } catch (Exception e) {
                            System.out.println("PARSE ERROR: " + e.getMessage());
                        }
                        break;
                    case "Sensor/Gyro":
                        datenGyro.setText(message);
                        String[] partsG = message.split(",");

                        try {
                            ValueSensor sensor = new ValueSensor();
                            sensor.value4 = Float.parseFloat(partsG[0]);
                            sensor.value5 = Float.parseFloat(partsG[1]);
                            sensor.value6 = Float.parseFloat(partsG[2]);


                            new Thread(() -> {
                                try {
                                    InitDb.appDatabase.valueDao().insert(sensor);
                                } catch (Exception e) {
                                    System.out.println("THREAD ERROR: " + e.getMessage());
                                }
                            }).start();

                        } catch (Exception e) {
                            System.out.println("PARSE ERROR: " + e.getMessage());
                        }
                        break;
                    case "Sensor/Zeit":
                        datenZeit.setText(message);

                        try {
                            ValueSensor sensor = new ValueSensor();
                            sensor.value7 = message;



                            new Thread(() -> {
                                try {
                                    InitDb.appDatabase.valueDao().insert(sensor);
                                } catch (Exception e) {
                                    System.out.println("THREAD ERROR: " + e.getMessage());
                                }
                            }).start();

                        } catch (Exception e) {
                            System.out.println("PARSE ERROR: " + e.getMessage());
                        }
                        break;
                }
            });
        });



        mqttHandler.connect(Broker_URL, Client_ID);
        subscribeToTopic("Sensor/Bewegung");
        subscribeToTopic("Sensor/Zeit");
        subscribeToTopic("Sensor/Gyro");
        publishMessage("Sensor/Bewegung", "-0.788295,4.259267,0.982682");
        publishMessage("Sensor/Zeit", "1970-01-01T00:01:10.215742Z");
        publishMessage("Sensor/Gyro", "1.265565, 0.301551, 0.052052");
        loadDatabaseValues();
    }

    @Override
    protected void onDestroy() {
        mqttHandler.disconnect();
        super.onDestroy();
    }

    private void publishMessage(String topic, String message) {
        //Anzeigen das etwas zum topic gepublisht wird
        Toast.makeText(this, "Publish message: " + message, Toast.LENGTH_SHORT).show();
        mqttHandler.publish(topic, message);
    }

    private void subscribeToTopic(String topic) {
        Toast.makeText(this, "Subscribing to topic: " + topic, Toast.LENGTH_SHORT).show();
        mqttHandler.subscribe(topic);
    }

    private void loadDatabaseValues() {
        new Thread(() -> {
            List<ValueSensor> list = InitDb.appDatabase.valueDao().getAllvalue();
            System.out.println("DB SIZE: " + list.size());
            for (ValueSensor s : list) {
                System.out.println("DB ENTRY: ID=" + s.primeID +
                        " | X=" + s.value1 +
                        " | Y=" + s.value2 +
                        " | Z=" + s.value3 +
                        " | Y=" + s.value4 +
                        " | Y=" + s.value5 +
                        " | Y=" + s.value6 +
                        " | Y=" + s.value7);
            }
        }).start();
    }

}
