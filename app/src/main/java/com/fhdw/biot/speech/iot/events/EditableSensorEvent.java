package com.fhdw.biot.speech.iot.events;

/**
 * EditableSensorEvent -------------------- Represents one editable rule configuration row.
 *
 * <p>Fields: id → internal identifier for adapter tracking sensorType → Accel / Gyro / Magnet
 * (default: Accel) eventType → "Overshoot", "Fall", "Shake", etc. thresholdValue → numeric
 * threshold that triggers the event
 *
 * <p>These objects are NOT the final events stored in the database. Instead, they are templates
 * used to *generate* sensor thresholds.
 */
public class EditableSensorEvent {

    public long id;
    public String sensorType;
    public String eventType;
    public float thresholdValue;

    public EditableSensorEvent(long id) {
        this.id = id;
        this.sensorType = "Accel"; // default
        this.eventType = "";
        this.thresholdValue = 0;
    }
}
