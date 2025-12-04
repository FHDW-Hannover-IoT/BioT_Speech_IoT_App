package com.fhdw.biot.speech.iot;

import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

/**
 * MqttHandler
 * -----------
 * Thin wrapper around Eclipse Paho MQTT v5 {@link MqttAsyncClient}.
 *
 * High-level responsibilities:
 *  - Own exactly one {@link MqttAsyncClient} instance for the whole app.
 *  - Provide easy methods to:
 *      - connect(...)       → open TCP + MQTT session to the broker.
 *      - subscribe(topic)   → register interest in specific topics.
 *      - publish(topic,..)  → send messages to the broker.
 *      - disconnect()       → close connection cleanly.
 *  - Expose connection status via {@link #isConnected()}.
 *  - Forward incoming messages to a higher-level listener
 *    ({@link MqttMessageListener}) which is implemented in MainActivity.
 *
 * Data flow (receive side):
 *  Broker → MqttAsyncClient → MqttCallback.messageArrived(...)
 *         → MqttHandler.listener.onMessageReceived(...)
 *         → MainActivity.handleMovement / handleGyro / handleTime
 *         → TextViews + Room database.
 *
 * Data flow (send side):
 *  SensorDataSimulator / MainActivity → MqttHandler.publish(...)
 *      → MqttAsyncClient.publish(...)
 *      → Broker → (optionally other subscribers, including our own client).
 */
public class MqttHandler {

    /**
     * Underlying Paho asynchronous client.
     * All real MQTT protocol work happens inside this object.
     */
    private final MqttAsyncClient client;

    /**
     * Simple flag to remember if the last connect succeeded.
     * This is set to true in connectComplete() and false in disconnected()
     * and in error paths. We always combine it with client.isConnected().
     *
     * Marked volatile so changes from background threads are visible to others.
     */
    private volatile boolean connected = false;

    /**
     * Optional high-level listener that receives incoming messages as
     * (topic, String payload). MainActivity passes a lambda here.
     */
    private MqttMessageListener listener;

    // ------------------------------------------------------------
    // Listener interfaces
    // ------------------------------------------------------------

    /**
     * Notified about connection lifecycle events.
     * Implemented by MainActivity to get callbacks when:
     *  - the connect attempt has succeeded (onConnected)
     *  - the connect attempt has failed (onFailure)
     */
    public interface ConnectionListener {
        void onConnected();
        void onFailure(Throwable t);
    }

    /**
     * Notified about every incoming MQTT message on any subscribed topic.
     * Implemented by MainActivity where messages are routed to UI + DB.
     */
    public interface MqttMessageListener {
        void onMessageReceived(String topic, String message);
    }

    // ------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------
    public MqttHandler(String brokerUrl, String clientId) throws MqttException {
        System.out.println("MQTTv5: creating client → " + brokerUrl + " id=" + clientId);

        /*
         * Create the underlying Paho client.
         *
         * - brokerUrl: e.g. "tcp://10.0.2.2:1883"
         * - clientId : must be unique per client connected to the broker
         * - MemoryPersistence:
         *      Keeps QoS state and in-flight messages only in RAM
         *      (no disk persistence on Android).
         */
        client = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());

        /*
         * Register a global callback implementation.
         * Paho uses this for:
         *  - connection loss
         *  - protocol-level errors
         *  - incoming messages
         *  - publish delivery notifications
         *  - successful connect / reconnect
         *  - AUTH packets (rarely used)
         */
        client.setCallback(new MqttCallback() {

            /**
             * Called when the connection is closed for *any* reason
             * (network problem, broker shutdown, disconnect(), etc.).
             */
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                connected = false;
                System.out.println(
                        "MQTTv5: DISCONNECTED → " +
                                (disconnectResponse == null ? "null" : disconnectResponse.getReasonString())
                );
            }

            /**
             * Called when an MQTT-level error occurs that is not normal
             * connection loss. Mostly useful for debugging.
             */
            @Override
            public void mqttErrorOccurred(MqttException exception) {
                System.out.println("MQTTv5 ERROR → " + exception.getMessage());
            }

            /**
             * Called whenever a message arrives on any topic this client
             * is subscribed to.
             *
             * Data flow:
             *  Broker → client.subscribe("some/topic", ...)
             *         → MqttAsyncClient receives PUBLISH
             *         → this messageArrived(...)
             *         → (topic, MqttMessage) converted to (topic, String)
             *         → listener.onMessageReceived(...) (if set)
             *         → MainActivity updates UI + DB.
             */
            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                System.out.println("MQTTv5: Message Arrived → " + topic + " = " + payload);

                if (listener != null) {
                    listener.onMessageReceived(topic, payload);
                }
            }

            /**
             * Called when a message that this client has PUBLISHED
             * has been fully delivered according to its QoS.
             *
             * We only log this; app logic does not depend on it.
             */
            @Override
            public void deliveryComplete(IMqttToken token) {
                System.out.println("MQTTv5: Delivery Complete → " + token);
            }

            /**
             * Called after a successful initial connect OR automatic reconnect.
             * This is another signal that the TCP/MQTT session is now usable.
             */
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                connected = true;
                System.out.println("MQTTv5: Connect Complete → " + serverURI +
                        " (reconnect=" + reconnect + ")");
            }

            /**
             * Extra abstract method for MQTT v5: AUTH packets.
             * We don't use authentication extensions, so we only log and ignore.
             */
            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                // You probably don't use AUTH packets; just log and ignore.
                System.out.println(
                        "MQTTv5: authPacketArrived → reasonCode=" +
                                reasonCode + " props=" + properties
                );
            }
        });
    }

    // ------------------------------------------------------------
    // CONNECT (background thread, sync call)
    // ------------------------------------------------------------

    /**
     * Connects to the MQTT broker on a background thread.
     *
     * Threading:
     *  - A new thread ("mqtt-connect-thread") is started.
     *  - Inside that thread we call client.connect(...) and block
     *    until it finishes using token.waitForCompletion().
     *
     * Callbacks:
     *  - On success  → ConnectionListener.onConnected()
     *  - On failure  → ConnectionListener.onFailure(Throwable)
     */
    public void connect(ConnectionListener connListener) {
        new Thread(() -> {
            try {
                // Configure session behaviour.
                MqttConnectionOptions options = new MqttConnectionOptions();
                options.setAutomaticReconnect(true); // Paho will try to reconnect if link drops.
                options.setCleanStart(true);        // Start a new session (no old subscriptions).

                // Start connect and wait until it has completed:
                IMqttToken token = client.connect(options);
                token.waitForCompletion();      // <-- BLOCKS this background thread
                //     until connected or failed.

                // If we got here without exception, the client is connected:
                connected = client.isConnected();

                System.out.println("MQTTv5: CONNECTED to " + client.getServerURI()
                        + " isConnected=" + client.isConnected());

                if (connected && connListener != null) {
                    connListener.onConnected();
                }

            } catch (Exception e) {
                connected = false;
                System.out.println("MQTTv5: CONNECT FAILED → " + e.getMessage());
                if (connListener != null) {
                    connListener.onFailure(e);
                }
            }
        }, "mqtt-connect-thread").start();
    }

    /**
     * Returns true only if:
     *  - our internal flag says "connected", AND
     *  - the Paho client also reports isConnected().
     *
     * This is what MainActivity uses before subscribe/publish.
     */
    public boolean isConnected() {
        return connected && client.isConnected();
    }

    // ------------------------------------------------------------
    // SUBSCRIBE
    // ------------------------------------------------------------

    /**
     * Subscribes to the given topic with QoS 1 ("at least once").
     *
     * Threading:
     *  - If currently not connected, this method just logs and returns.
     *  - Otherwise a new thread ("mqtt-subscribe-thread") is started and
     *    client.subscribe(topic, 1) is called there.
     *
     * Data impact:
     *  - After a successful subscribe, any PUBLISH sent to that topic by
     *    any client will trigger messageArrived(...) → listener.onMessageReceived(...).
     */
    public void subscribe(String topic) {
        if (!isConnected()) {
            System.out.println("MQTTv5: Cannot subscribe — not connected.");
            return;
        }

        new Thread(() -> {
            try {
                client.subscribe(topic, 1); // QoS 1
                System.out.println("MQTTv5: SUBSCRIBED → " + topic);
            } catch (Exception e) {
                System.out.println("MQTTv5: SUBSCRIBE FAILED → " + topic +
                        " : " + e.getMessage());
            }
        }, "mqtt-subscribe-thread").start();
    }

    // ------------------------------------------------------------
    // PUBLISH
    // ------------------------------------------------------------

    /**
     * Convenience overload: publish a non-retained message.
     */
    public void publish(String topic, String payload) {
        publish(topic, payload, false);
    }

    /**
     * Publishes a message with QoS 1 on the given topic.
     *
     * @param topic    MQTT topic to publish on (e.g. "Sensor/Bewegung").
     * @param payload  payload as UTF-8 String.
     * @param retained if true, the broker stores this as the last known message
     *                 for that topic and immediately sends it to new subscribers.
     *
     * Threading:
     *  - If not connected, we log and return.
     *  - Otherwise we spin up "mqtt-publish-thread" where we do a blocking
     *    client.publish(...). The UI thread never blocks on network I/O.
     *
     * Data impact:
     *  - The message is sent to the broker.
     *  - The broker forwards it to all clients subscribed to this topic
     *    (including our own client, if subscribed).
     *  - That in turn triggers messageArrived(...) and ends up in MainActivity.
     */
    public void publish(String topic, String payload, boolean retained) {
        if (!isConnected()) {
            System.out.println("MQTTv5: Cannot publish — not connected.");
            return;
        }

        new Thread(() -> {
            try {
                MqttMessage msg = new MqttMessage(payload.getBytes());
                msg.setQos(1);            // "at least once"
                msg.setRetained(retained);

                // Blocking publish, but on our own thread, so UI is safe.
                client.publish(topic, msg);
                System.out.println("MQTTv5: PUBLISH SUCCESS → " + topic +
                        " payload=" + payload + " retained=" + retained);

            } catch (Exception e) {
                System.out.println("MQTTv5: PUBLISH FAILED → " + topic +
                        " ERROR = " + e.getMessage());
            }
        }, "mqtt-publish-thread").start();
    }

    // ------------------------------------------------------------
    // DISCONNECT
    // ------------------------------------------------------------

    /**
     * Gracefully disconnects from the broker.
     *
     * Threading:
     *  - Runs in "mqtt-disconnect-thread" to avoid blocking the UI.
     *
     * Behaviour:
     *  - If the client is currently connected, calls client.disconnect().
     *  - Sets the internal connected flag to false.
     *  - Any further publish/subscribe calls will log "not connected".
     */
    public void disconnect() {
        new Thread(() -> {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                    connected = false;
                    System.out.println("MQTTv5: DISCONNECTED");
                }
            } catch (Exception e) {
                System.out.println("MQTTv5: Disconnect error → " + e.getMessage());
            }
        }, "mqtt-disconnect-thread").start();
    }

    /**
     * Registers or replaces the high-level message listener.
     *
     * Typically called once from MainActivity.onCreate() to pass a lambda
     * that routes incoming messages to the corresponding handler method
     * (movement / gyro / time).
     */
    public void setMessageListener(MqttMessageListener l) {
        this.listener = l;
    }
}
