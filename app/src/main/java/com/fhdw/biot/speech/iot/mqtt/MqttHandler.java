package com.fhdw.biot.speech.iot.mqtt;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MqttHandler — thin wrapper around Eclipse Paho MQTT v5 {@link MqttAsyncClient}.
 *
 * Threading: a single-thread {@link ExecutorService} handles all blocking Paho
 * calls (connect, subscribe, publish, disconnect). No raw Thread is created.
 * All operations are fire-and-forget from the caller's perspective.
 *
 * Connection status is exposed as {@link LiveData<Boolean>} so Activities can
 * observe it reactively instead of polling {@link #isConnected()}.
 */
public class MqttHandler implements IMqttPublisher {

    private static final String TAG = "MqttHandler";

    private final MqttAsyncClient client;
    private volatile boolean connected = false;
    private MqttMessageListener listener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mqtt-thread");
        t.setDaemon(true);
        return t;
    });

    private final MutableLiveData<Boolean> connectionStatus = new MutableLiveData<>(false);

    public LiveData<Boolean> connectionStatus() { return connectionStatus; }

    // ── Listener interfaces ───────────────────────────────────────────────────

    public interface ConnectionListener {
        void onConnected();
        void onFailure(Throwable t);
    }

    public interface MqttMessageListener {
        void onMessageReceived(String topic, String message);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public MqttHandler(String brokerUrl, String clientId) throws MqttException {
        Log.i(TAG, "Creating client → " + brokerUrl + " id=" + clientId);
        client = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
        client.setCallback(new MqttCallback() {

            @Override
            public void disconnected(MqttDisconnectResponse response) {
                connected = false;
                connectionStatus.postValue(false);
                Log.w(TAG, "Disconnected: " + (response == null ? "null" : response.getReasonString()));
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {
                Log.e(TAG, "MQTT error: " + exception.getMessage(), exception);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                Log.d(TAG, "Message arrived → " + topic + " = " + payload);
                if (listener != null) listener.onMessageReceived(topic, payload);
            }

            @Override
            public void deliveryComplete(IMqttToken token) {
                Log.d(TAG, "Delivery complete → " + token);
            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                connected = true;
                connectionStatus.postValue(true);
                Log.i(TAG, "Connect complete → " + serverURI + " (reconnect=" + reconnect + ")");
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                Log.d(TAG, "Auth packet → reasonCode=" + reasonCode);
            }
        });
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    public void connect(ConnectionListener connListener) {
        executor.submit(() -> {
            // Guard against duplicate connect (e.g. when a new Activity starts while
            // this handler is already connected — Paho throws 32100 otherwise).
            if (client.isConnected()) {
                Log.d(TAG, "connect() called but already connected — skipping");
                if (connListener != null) connListener.onConnected();
                return;
            }
            try {
                MqttConnectionOptions options = new MqttConnectionOptions();
                options.setAutomaticReconnect(true);
                options.setCleanStart(true);

                IMqttToken token = client.connect(options);
                token.waitForCompletion();
                connected = client.isConnected();
                Log.i(TAG, "Connected to " + client.getServerURI() + " isConnected=" + connected);

                if (connected && connListener != null) connListener.onConnected();
            } catch (Exception e) {
                connected = false;
                Log.e(TAG, "Connect failed: " + e.getMessage(), e);
                if (connListener != null) connListener.onFailure(e);
            }
        });
    }

    public boolean isConnected() {
        return connected && client.isConnected();
    }

    // ── Subscribe ─────────────────────────────────────────────────────────────

    public void subscribe(String topic) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot subscribe — not connected: " + topic);
            return;
        }
        executor.submit(() -> {
            try {
                client.subscribe(topic, 1);
                Log.i(TAG, "Subscribed → " + topic);
            } catch (Exception e) {
                Log.e(TAG, "Subscribe failed → " + topic + ": " + e.getMessage(), e);
            }
        });
    }

    // ── Publish ───────────────────────────────────────────────────────────────

    public void publish(String topic, String payload) {
        publish(topic, payload, false);
    }

    @Override
    public void publish(String topic, String payload, boolean retained) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot publish — not connected: " + topic);
            return;
        }
        executor.submit(() -> {
            try {
                MqttMessage msg = new MqttMessage(payload.getBytes());
                msg.setQos(1);
                msg.setRetained(retained);
                client.publish(topic, msg);
                Log.d(TAG, "Published → " + topic + " payload=" + payload + " retained=" + retained);
            } catch (Exception e) {
                Log.e(TAG, "Publish failed → " + topic + ": " + e.getMessage(), e);
            }
        });
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    public void disconnect() {
        executor.submit(() -> {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                    connected = false;
                    Log.i(TAG, "Disconnected.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Disconnect error: " + e.getMessage(), e);
            } finally {
                executor.shutdown();
            }
        });
    }

    public void setMessageListener(MqttMessageListener l) {
        this.listener = l;
    }
}
