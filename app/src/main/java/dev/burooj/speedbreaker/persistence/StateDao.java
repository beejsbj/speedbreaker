package dev.burooj.speedbreaker.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface StateDao {
    @Query("SELECT * FROM speedbreaker_state WHERE `key` = :key LIMIT 1")
    StateRecord read(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void write(StateRecord record);
}
