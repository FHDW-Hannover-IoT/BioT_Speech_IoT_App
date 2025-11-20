package database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ValueSensorDAO {

        @Insert
        void insert(ValueSensor sensor);

        @Update
        void update(ValueSensor sensor);

        @Delete
        void delete(ValueSensor sensor);

        @Query("SELECT * FROM sensor WHERE PrimeID = :primeID")
        ValueSensor getvalueByID(String primeID);

        @Query("SELECT * FROM sensor")
        List<ValueSensor> getAllvalue();

}
