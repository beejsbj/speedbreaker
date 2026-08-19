package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AppCategory {
    STANDARD,
    REELS
}

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val category: AppCategory = AppCategory.STANDARD,
    val blockDurationSeconds: Int = 10,
    val customQuotes: String? = null,
    val isEnabled: Boolean = true,
    val totalLaunchesBlocked: Int = 0 
)
