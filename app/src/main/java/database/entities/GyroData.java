package database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fhdw.biot.speech.iot.sensor.SensorPoint;

@Entity(tableName = "gyro_data")
public class GyroData implements SensorPoint {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;
    public float gyroX;
    public float gyroY;
    public float gyroZ;

    @Override
    public long getTimestamp() { return timestamp; }

    @Override
    public float getX() { return gyroX; }

    @Override
    public float getY() { return gyroY; }

    @Override
    public float getZ() { return gyroZ; }
}
