package database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "magnet_data")
public class MagnetData {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public float magnetX;
    public float magnetY;
    public float magnetZ;
    public long timestamp;
}
