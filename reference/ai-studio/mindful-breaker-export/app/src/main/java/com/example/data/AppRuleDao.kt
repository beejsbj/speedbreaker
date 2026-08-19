package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    fun getAllRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE id = :id LIMIT 1")
    fun getRuleById(id: Int): Flow<AppRule?>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getRuleByPackageSync(packageName: String): AppRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(appRule: AppRule)

    @Update
    suspend fun updateRule(appRule: AppRule)

    @Query("DELETE FROM app_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)
}
