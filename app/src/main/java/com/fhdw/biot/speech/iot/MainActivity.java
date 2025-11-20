package com.fhdw.biot.speech.iot;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final String Broker_URL = "tcp://10.0.2.2:1883";
    private static final String Client_ID = "Nutzer";
    private MqttHandler mqttHandler;
    //private final TextView datenBewegung = findViewById(R.id.Daten_Bewegung);
    //private TextView datenPulse = findViewById(R.id.Daten_Bewegung);;
    //private TextView datenGyro = findViewById(R.id.Daten_Bewegung);;

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
        //mqttHandler.setMessageListener((topic, message) -> {
        //    System.out.println("Received in Activity: " + message);
        //    runOnUiThread(() -> datenBewegung.setText(message));
        //});
        mqttHandler.connect(Broker_URL, Client_ID);
        subscribeToTopic("Sensor");
        //subscribeToTopic("Sensor/Magnetfeld");
        //subscribeToTopic("Sensor/Gyro");
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
}
