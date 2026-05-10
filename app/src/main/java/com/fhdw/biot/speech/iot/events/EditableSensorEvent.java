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

    public boolean axisX;
    public boolean axisY;
    public boolean axisZ;
    public boolean axisSum;

    public String thresholdDirection;

    public EditableSensorEvent(long id) {
        this.id = id;
        this.sensorType = "Accel"; // default
        this.eventType = "";
        this.thresholdValue = 0;
        this.axisX = true;
        this.axisY = true;
        this.axisZ = true;
        this.axisSum = false;

        this.thresholdDirection = ">=";
    }

    public boolean isAxisX() {
        return axisX;
    }

    public void setAxisX(boolean axisX) {
        this.axisX = axisX;
    }

    public boolean isAxisY() {
        return axisY;
    }

    public void setAxisY(boolean axisY) {
        this.axisY = axisY;
    }

    public boolean isAxisZ() {
        return axisZ;
    }

    public void setAxisZ(boolean axisZ) {
        this.axisZ = axisZ;
    }

    public boolean isAxisSum() {
        return axisSum;
    }

    public void setAxisSum(boolean axisSum) {
        this.axisSum = axisSum;
    }
}
