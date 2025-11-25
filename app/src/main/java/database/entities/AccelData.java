package database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "accel_data")
public class AccelData implements SensorPoint{

    @PrimaryKey(autoGenerate = true)
    private int id;

    private long timestamp;
    private float accelX;
    private float accelY;
    private float accelZ;

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
