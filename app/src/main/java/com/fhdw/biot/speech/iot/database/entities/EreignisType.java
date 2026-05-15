package com.fhdw.biot.speech.iot.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Persistent rule definition that drives the {@link com.fhdw.biot.speech.iot.events.RuleEvaluator}. */
@Entity(tableName = "ereignisType")
public class EreignisType {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ereignisID")
    public int ereignisID;

    @ColumnInfo(name = "ereignisName")    public String ereignisName;
    @ColumnInfo(name = "sensorType")      public String sensorType;
    @ColumnInfo(name = "axisX")           public boolean axisX;
    @ColumnInfo(name = "axisY")           public boolean axisY;
    @ColumnInfo(name = "axisZ")           public boolean axisZ;
    @ColumnInfo(name = "axisSum")         public boolean axisSum;
    @ColumnInfo(name = "ereignisThreshold") public float ereignisThreshold;
    @ColumnInfo(name = "thresholdDirection") public String thresholdDirection;
}
