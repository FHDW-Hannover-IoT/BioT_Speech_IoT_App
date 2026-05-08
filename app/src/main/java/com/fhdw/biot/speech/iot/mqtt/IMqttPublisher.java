package com.fhdw.biot.speech.iot.mqtt;

/**
 * IMqttPublisher
 * ─────────────────────────────────────────────────────────────────────────────
 * Abstraction over anything that can publish an MQTT message.
 *
 * This makes both classes independently testable and keeps the voice/LLM
 * packages decoupled from the mqtt package.
 *
 * Implementations
 * ───────────────
 *  • {@link MqttHandler}          – real Paho-backed publisher (production)
 *  • A mock in unit tests          – no broker needed
 */
public interface IMqttPublisher {

    /**
     * Publish a non-retained message with QoS 1.
     *
     * @param topic   MQTT topic string, e.g. {@code "Control/Mode"}
     * @param payload UTF-8 payload string, e.g. {@code "BURST"}
     */
    void publish(String topic, String payload);

    /**
     * Publish a message with explicit retained flag.
     *
     * @param topic    MQTT topic string
     * @param payload  UTF-8 payload string
     * @param retained {@code true} → broker stores this as the last-known value
     *                 and replays it to new subscribers immediately
     */
    void publish(String topic, String payload, boolean retained);

    /**
     * Returns {@code true} if there is currently an active connection to the broker.
     * Callers should check this before publishing to avoid silent no-ops.
     */
    boolean isConnected();
}