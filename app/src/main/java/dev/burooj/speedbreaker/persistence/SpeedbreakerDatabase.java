package dev.burooj.speedbreaker.persistence;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {StateRecord.class}, version = 1, exportSchema = false)
public abstract class SpeedbreakerDatabase extends RoomDatabase {
    public abstract StateDao stateDao();
}
