package com.fhdw.biot.speech.iot;

public class EditableSensorEvent {
    public long id;
    public String sensorType;
    public String eventType;
    public float thresholdValue;

    public EditableSensorEvent(long id) {
        this.id = id;
        this.sensorType = "Accel";
        this.eventType = "";
        this.thresholdValue = 0;
    }
}
