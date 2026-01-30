package database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "knownSensors")
public class Sensor {
    @PrimaryKey(autoGenerate = true)
    public int sensorID;

    public String sensorName;

    public String getSensorName() {
        return sensorName;
    }

    public int getSensorID() {
        return sensorID;
    }

    public Sensor setSensorName(String name) {
        sensorName = name;
        return this;
    }
}
