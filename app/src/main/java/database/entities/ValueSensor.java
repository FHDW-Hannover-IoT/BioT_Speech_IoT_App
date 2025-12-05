package database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "sensor")
public class ValueSensor {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "PrimeID")
    public int primeID;

    @ColumnInfo(name = "BewegungX")
    public float value1;

    @ColumnInfo(name = "BewegungY")
    public float value2;

    @ColumnInfo(name = "BewegungZ")
    public float value3;

    @ColumnInfo(name = "GyroX")
    public float value4;

    @ColumnInfo(name = "GyroY")
    public float value5;

    @ColumnInfo(name = "GyroZ")
    public float value6;

    @ColumnInfo(name = "Zeit")
    public String value7;
}
