package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class DataRepository(private val appRuleDao: AppRuleDao) {
    val allRules: Flow<List<AppRule>> = appRuleDao.getAllRules()

    fun getRuleById(id: Int): Flow<AppRule?> = appRuleDao.getRuleById(id)

    suspend fun getRuleByPackageSync(packageName: String): AppRule? = 
        appRuleDao.getRuleByPackageSync(packageName)

    suspend fun insertOrUpdate(appRule: AppRule) {
        if (appRule.id == 0) {
            appRuleDao.insertRule(appRule)
        } else {
            appRuleDao.updateRule(appRule)
        }
    }

    suspend fun incrementBlockedCount(id: Int, currentCount: Int) {
        // Fetch it
        appRuleDao.getRuleById(id).collect { rule ->
            rule?.let {
                appRuleDao.updateRule(it.copy(totalLaunchesBlocked = it.totalLaunchesBlocked + 1))
            }
        }
    }

    suspend fun incrementBlockedCountSimple(rule: AppRule) {
        appRuleDao.updateRule(rule.copy(totalLaunchesBlocked = rule.totalLaunchesBlocked + 1))
    }

    suspend fun deleteById(id: Int) = appRuleDao.deleteRuleById(id)

    companion object {
        @Volatile
        private var INSTANCE: DataRepository? = null

        fun getInstance(context: Context): DataRepository {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mindful_breaker_db"
                ).build()
                val instance = DataRepository(db.appRuleDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
