package com.fhdw.biot.speech.iot;


import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "gyro_data")
public class GyroData {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public float gyroX;
    public float gyroY;
    public float gyroZ;
    public long timestamp;
}