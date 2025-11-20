package com.fhdw.biot.speech.iot;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttHandler {

    private MqttClient client;
    private MqttMessageListener listener;
    public void setMessageListener(MqttMessageListener listener) {
        this.listener = listener;
    }
    public void connect(String brokerUrl, String clientId) {
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            client = new MqttClient(brokerUrl, clientId, persistence);

            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("MQTT connection lost");
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    System.out.println("MQTT delivery complete");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    System.out.println("DEBUG: messageArrived() -> " + payload);

                    if (listener != null) {
                        listener.onMessageReceived(topic, payload);
                    }
                }
            });

            client.connect(connectOptions);
            System.out.println("MQTT connected");

        } catch (MqttException e) {
            System.out.println("MQTT not connected");
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish(String topic, String message) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            client.publish(topic, mqttMessage);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String topic) {
        try {
            client.subscribe(topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public interface MqttMessageListener {
        void onMessageReceived(String topic, String message);
    }

}
