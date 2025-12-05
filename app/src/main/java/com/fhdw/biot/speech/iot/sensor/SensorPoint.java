package com.fhdw.biot.speech.iot.sensor;

/**
 * SensorPoint ----------- Small abstraction for a single 3D sensor sample with a timestamp. It is
 * used by generic algorithms (like the Douglas-Peucker simplification) so they don't care if the
 * data came from accelerometer, gyro, magnetometer, or something else. As long as a class can
 * provide: - a timestamp, - X/Y/Z components, it can be treated as a SensorPoint.
 */
public interface SensorPoint {

    /**
     * @return the timestamp of this sample in milliseconds (usually System.currentTimeMillis() or a
     *     similar time base).
     */
    long getTimestamp();

    /**
     * @return the X component of the sensor vector (e.g. accelX, gyroX, magnetX, depending on the
     *     sensor).
     */
    float getX();

    /**
     * @return the Y component of the sensor vector (e.g. accelY, gyroY, magnetY, ...).
     */
    float getY();

    /**
     * @return the Z component of the sensor vector (e.g. accelZ, gyroZ, magnetZ, ...).
     */
    float getZ();
}
