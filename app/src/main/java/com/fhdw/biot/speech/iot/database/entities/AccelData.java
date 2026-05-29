package com.fhdw.biot.speech.iot.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.fhdw.biot.speech.iot.sensor.SensorPoint;

@Entity(tableName = "accel_data",
        indices = {@Index(value = {"timestamp", "resolution"}, unique = true)})
public class AccelData implements SensorPoint {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;
    public float accelX;
    public float accelY;
    public float accelZ;

    /** Which resolution tier this row came from: "raw", "1min", or "1hour". */
    @ColumnInfo(defaultValue = "raw")
    public String resolution = "raw";

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public float getX() {
        return accelX;
    }

    @Override
    public float getY() {
        return accelY;
    }

    @Override
    public float getZ() {
        return accelZ;
    }
}
