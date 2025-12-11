package database.entities;

import androidx.room.PrimaryKey;

public class EreignisType {
    @PrimaryKey(autoGenerate = true)
    public int ereignisID;

    public String ereignisName;
    public Sensor sensorType;
    public int ereignisThreshold;
}
