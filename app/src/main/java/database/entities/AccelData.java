package database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fhdw.biot.speech.iot.sensor.SensorPoint;

@Entity(tableName = "accel_data")
public class AccelData implements SensorPoint {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;
    public float accelX;
    public float accelY;
    public float accelZ;

    @Override
    public long getTimestamp() { return timestamp; }

    @Override
    public float getX() { return accelX; }

    @Override
    public float getY() { return accelY; }

    @Override
    public float getZ() { return accelZ; }
}
