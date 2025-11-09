package com.fhdw.biot.speech.iot;

public class SensorEvent {
    private long timestamp;
    private String sensorType;
    private float value;
    private String id;

    public SensorEvent(long timestamp, String sensorType, float value, String id) {
        this.timestamp = timestamp;
        this.sensorType = sensorType;
        this.value = value;
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSensorType() {
        return sensorType;
    }

    public float getValue() {
        return value;
    }

    public String getId() {
        return id;
    }
}
