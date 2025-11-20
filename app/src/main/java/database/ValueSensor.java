package database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sensor")
public class ValueSensor {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "PrimeID")
    public int primeID;
    @ColumnInfo(name = "BewegungX")
    public int value1;

    @ColumnInfo(name = "BewegungY")
    public int value2;

    @ColumnInfo(name = "BewegungZ")
    public int value3;

    @ColumnInfo(name = "GyroX")
    public int value4;

    @ColumnInfo(name = "GyroY")
    public int value5;

    @ColumnInfo(name = "GyroZ")
    public int value6;

    @ColumnInfo(name = "MagnetfeldX")
    public int value7;

    @ColumnInfo(name = "MagnetfeldY")
    public int value8;

    @ColumnInfo(name = "MagnetfeldZ")
    public int value9;
}
