package dev.burooj.speedbreaker.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A deliberately tiny local store: one JSON value per current state kind. */
@Entity(tableName = "speedbreaker_state")
public final class StateRecord {
    @PrimaryKey
    @NonNull
    public final String key;

    @NonNull
    public final String value;

    public StateRecord(@NonNull String key, @NonNull String value) {
        this.key = key;
        this.value = value;
    }
}
