package com.fhdw.biot.speech.iot;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "accel_data")
public class AccelData {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;
    public float accelX;
    public float accelY;
    public float accelZ;

}
