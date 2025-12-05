package com.fhdw.biot.speech.iot.sensor;

public interface SensorPoint {
    long getTimestamp();

    float getX();

    float getY();

    float getZ();
}
