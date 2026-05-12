package com.fhdw.biot.speech.iot.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ereignisType")
public class EreignisType {
    @PrimaryKey(autoGenerate = true)
    public int ereignisID;

    public String ereignisName;
    public String sensorType;
    public boolean axisX;
    public boolean axisY;
    public boolean axisZ;
    public boolean axisSum;
    public float ereignisThreshold;

    @ColumnInfo(name = "thresholdDirection")
    public String thresholdDirection;
}
