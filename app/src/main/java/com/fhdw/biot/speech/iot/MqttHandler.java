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

/**
 * MqttHandler
 * -----------
 * A small wrapper around Eclipse Paho's {@link MqttAsyncClient}.
 *
 * Responsibilities:
 * - Create and hold a single MQTT client instance.
 * - Asynchronously connect to an MQTT broker.
 * - Expose connection status via {@link #isConnected()}.
 * - Allow subscribers to:
 *      - Subscribe to topics.
 *      - Publish messages (optionally retained).
 *      - Receive incoming messages via {@link MqttMessageListener}.
 * - Cleanly disconnect from the broker.
 *
 * Threading model:
 * - {@link #connect(ConnectionListener)} and {@link #disconnect()} spawn their own
 *   background threads so that networking does not block the UI.
 * - Incoming messages arrive on Paho's internal thread and are forwarded to
 *   {@link MqttMessageListener#onMessageReceived(String, String)}.
 */
public class MqttHandler {

    /**
     * Underlying Eclipse Paho asynchronous MQTT client.
     */
    private final MqttAsyncClient client;

    /**
     * Simple flag to remember if a connection attempt has succeeded.
     * <p>
     * Marked {@code volatile} so changes are visible across threads.
     * Note: we also check {@link MqttAsyncClient#isConnected()} in {@link #isConnected()}.
     */
    private volatile boolean connected = false;

    /**
     * Optional callback that will receive incoming MQTT messages from any subscribed topic.
     */
    private MqttMessageListener listener;

    /**
     * Listener for connection-level events.
     * <p>
     * Implemented by the code that calls {@link #connect(ConnectionListener)} (e.g. MainActivity).
     */
    public interface ConnectionListener {

        /**
         * Called once the client has successfully connected to the broker.
         */
        void onConnected();

        /**
         * Called when the connection attempt fails or throws an exception.
         *
         * @param t the cause of the failure (may be {@code null} in rare cases)
         */
        void onFailure(Throwable t);
    }

    /**
     * Listener interface for incoming MQTT messages.
     * <p>
     * Implemented by clients (e.g., MainActivity) that want to react to published sensor data.
     */
    public interface MqttMessageListener {

        /**
         * Called whenever a subscribed topic receives a new message.
         *
         * @param topic   the MQTT topic on which the message arrived, e.g. "Sensor/Bewegung"
         * @param message the payload as a decoded UTF-8 String
         */
        void onMessageReceived(String topic, String message);
    }

    /**
     * Constructs a new {@code MqttHandler} and configures the underlying Paho client.
     *
     * @param brokerUrl MQTT broker URL, e.g. "tcp://192.168.178.31:1883"
     * @param clientId  client identifier used by the broker to differentiate clients.
     *                  Must be unique per broker connection.
     *
     * @throws MqttException if the client cannot be created (invalid URL, etc.).
     */
    public MqttHandler(String brokerUrl, String clientId) throws MqttException {
        System.out.println("MQTT: creating client -> " + brokerUrl + " id=" + clientId);

        // MemoryPersistence: MQTT session data (like in-flight messages) is stored in RAM.
        client = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());

        // Global callback for connection loss, incoming messages, and delivery confirmations.
        client.setCallback(new MqttCallback() {
            /**
             * Called when the connection to the broker is lost for any reason
             * (network error, broker shutdown, etc.).
             */
            @Override
            public void connectionLost(Throwable cause) {
                connected = false;
                System.out.println("MQTT: connectionLost -> " +
                        (cause == null ? "null" : cause.getMessage()));
            }

            /**
             * Called when a message arrives on a subscribed topic.
             *
             * @param topic   topic name
             * @param message raw {@link MqttMessage} containing payload, QoS, retained flag, etc.
             */
            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                System.out.println("MQTT: messageArrived topic=" + topic + " payload=" + payload);

                // Forward to high-level listener if one is registered.
                if (listener != null) listener.onMessageReceived(topic, payload);
            }

            /**
             * Called when a message that this client has published is completely delivered.
             *
             * @param token delivery token associated with the published message.
             */
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("MQTT: deliveryComplete token=" + token);
            }
        });
    }

    /**
     * Asynchronously connects to the MQTT broker using sensible defaults.
     *
     * <p>Configuration:</p>
     * <ul>
     *     <li>{@code cleanSession = true} – no persistent session on broker.</li>
     *     <li>{@code automaticReconnect = true} – client will try to reconnect automatically
     *         if connection is lost.</li>
     * </ul>
     *
     * @param connListener callback that will be notified on success or failure.
     *                     May be {@code null} if the caller does not care.
     *
     * Return value: {@code void}. This method returns immediately; the connection
     * result is delivered through {@link ConnectionListener}.
     */
    public void connect(final ConnectionListener connListener) {
        new Thread(() -> {
            try {
                System.out.println("MQTT: connect() called");

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                options.setAutomaticReconnect(true);

                // Async connect with an action listener.
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

    /**
     * Checks whether the client is currently connected to the broker.
     *
     * @return {@code true} if both:
     *         <ul>
     *             <li>our internal flag {@link #connected} is {@code true}, and</li>
     *             <li>{@link MqttAsyncClient#isConnected()} reports the client is connected.</li>
     *         </ul>
     *         Otherwise {@code false}.
     */
    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }

    /**
     * Subscribes to a topic with QoS 1 (at least once).
     *
     * @param topic the topic filter to subscribe to, e.g. "Sensor/Bewegung".
     *
     * Return value: {@code void}.
     * Side effects:
     * - If not connected, prints a message and returns without subscribing.
     * - If connected, sends SUBSCRIBE request and logs success or failure.
     */
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

    /**
     * Convenience method to publish a non-retained message.
     *
     * @param topic   topic to publish on
     * @param payload string payload to send
     */
    public void publish(String topic, String payload) {
        publish(topic, payload, false);
    }

    /**
     * Publishes a message on the given topic, optionally as a retained message.
     *
     * @param topic    topic to publish on, e.g. "Sensor/Zeit"
     * @param payload  payload to send, as a String (internally encoded as bytes)
     * @param retained if {@code true}, brokers stores this as the "last known" value
     *                 on that topic and delivers it to future subscribers immediately.
     *
     * Return value: {@code void}.
     * Side effects:
     * - If not connected, logs and returns without sending.
     * - Otherwise, starts a background thread to perform the publish.
     */
    public void publish(String topic, String payload, boolean retained) {
        if (!isConnected()) {
            System.out.println("MQTT: publish called but not connected -> " + topic);
            return;
        }
        new Thread(() -> {
            try {
                MqttMessage msg = new MqttMessage(payload.getBytes());
                msg.setQos(1);                // At least once delivery.
                msg.setRetained(retained);    // Retained or transient.

                client.publish(topic, msg, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        System.out.println("MQTT: publish success -> " + topic +
                                " payload=" + payload + " retained=" + retained);
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

    /**
     * Gracefully disconnects from the broker in a background thread.
     * Return value: {@code void}.
     * Side effects:
     * - If the client is currently connected, sends DISCONNECT and updates
     *   {@link #connected} to {@code false}.
     * - Logs success or any exceptions.
     */
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

    /**
     * Registers or replaces the high-level message listener.
     * When non-null, the listener will receive all incoming messages on any
     * topic that this client has subscribed to.
     * @param l implementation of {@link MqttMessageListener}, or {@code null} to remove the listener.
     * Return value: {@code void}.
     */
    public void setMessageListener(MqttMessageListener l) {
        this.listener = l;
    }
}
