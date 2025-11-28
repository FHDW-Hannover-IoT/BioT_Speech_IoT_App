package com.fhdw.biot.speech.iot;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ereignis_data")
public class EreignisData {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;
    public String sensorType;
    public float value;
    public char axis;
}
