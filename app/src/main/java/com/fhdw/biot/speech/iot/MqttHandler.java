package com.fhdw.biot.speech.iot;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttHandler {

    private final MqttAsyncClient client;
    private volatile boolean connected = false;
    private MqttMessageListener listener;

    public interface ConnectionListener {
        void onConnected();
        void onFailure(Throwable t);
    }

    public MqttHandler(String brokerUrl, String clientId) throws MqttException {
        System.out.println("MQTT: creating client -> " + brokerUrl + " id=" + clientId);
        client = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                connected = false;
                System.out.println("MQTT: connectionLost -> " + (cause == null ? "null" : cause.getMessage()));
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                System.out.println("MQTT: messageArrived topic=" + topic + " payload=" + payload);
                if (listener != null) listener.onMessageReceived(topic, payload);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("MQTT: deliveryComplete token=" + token);
            }
        });
    }

    public void connect(final ConnectionListener connListener) {
        new Thread(() -> {
            try {
                System.out.println("MQTT: connect() called");
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                options.setAutomaticReconnect(true);
                client.connect(options, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        connected = true;
                        System.out.println("MQTT: connected successfully to " + client.getServerURI());
                        if (connListener != null) connListener.onConnected();
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        connected = false;
                        System.out.println("MQTT: connect failed -> " + (exception == null ? "unknown" : exception.getMessage()));
                        if (connListener != null) connListener.onFailure(exception);
                    }
                });
            } catch (MqttException e) {
                connected = false;
                System.out.println("MQTT: connect threw -> " + e.getMessage());
                if (connListener != null) connListener.onFailure(e);
            }
        }).start();
    }

    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }

    public void subscribe(String topic) {
        if (!isConnected()) {
            System.out.println("MQTT: subscribe called but not connected -> " + topic);
            return;
        }
        try {
            client.subscribe(topic, 1, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("MQTT: subscribe success -> " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("MQTT: subscribe FAILED -> " + topic + " : " + (exception == null ? "unknown" : exception.getMessage()));
                }
            });
        } catch (MqttException e) {
            System.out.println("MQTT: subscribe exception -> " + e.getMessage());
        }
    }

    public void publish(String topic, String payload) {
        publish(topic, payload, false);
    }

    /** publish with retained option for testing */
    public void publish(String topic, String payload, boolean retained) {
        if (!isConnected()) {
            System.out.println("MQTT: publish called but not connected -> " + topic);
            return;
        }
        new Thread(() -> {
            try {
                MqttMessage msg = new MqttMessage(payload.getBytes());
                msg.setQos(1);
                msg.setRetained(retained);
                client.publish(topic, msg, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        System.out.println("MQTT: publish success -> " + topic + " payload=" + payload + " retained=" + retained);
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        System.out.println("MQTT: publish FAILED -> " + topic + " : " + (exception == null ? "unknown" : exception.getMessage()));
                    }
                });
            } catch (MqttException e) {
                System.out.println("MQTT: publish exception -> " + e.getMessage());
            }
        }).start();
    }

    public void disconnect() {
        new Thread(() -> {
            try {
                if (client != null && client.isConnected()) {
                    client.disconnect();
                    connected = false;
                    System.out.println("MQTT: disconnected");
                }
            } catch (MqttException e) {
                System.out.println("MQTT: disconnect error -> " + e.getMessage());
            }
        }).start();
    }

    public void setMessageListener(MqttMessageListener l) {
        this.listener = l;
    }

    public interface MqttMessageListener {
        void onMessageReceived(String topic, String message);
    }
}
