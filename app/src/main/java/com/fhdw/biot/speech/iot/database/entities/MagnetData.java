package com.fhdw.biot.speech.iot.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.fhdw.biot.speech.iot.sensor.SensorPoint;

@Entity(tableName = "magnet_data",
        indices = {@Index(value = {"timestamp", "resolution"}, unique = true)})
public class MagnetData implements SensorPoint {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;
    public float magnetX;
    public float magnetY;
    public float magnetZ;

    @ColumnInfo(defaultValue = "raw")
    public String resolution = "raw";

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public float getX() {
        return magnetX;
    }

    @Override
    public float getY() {
        return magnetY;
    }

    @Override
    public float getZ() {
        return magnetZ;
    }
}
