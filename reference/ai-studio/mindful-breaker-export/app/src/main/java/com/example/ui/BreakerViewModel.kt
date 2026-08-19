package com.example.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppCategory
import com.example.data.AppRule
import com.example.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppInfo(
    val packageName: String,
    val appName: String
)

class BreakerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DataRepository = DataRepository.getInstance(application)

    val rules: StateFlow<List<AppRule>> = repository.allRules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    fun loadInstalledApps() {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        val apps = resolveInfoList.map { resolveInfo ->
            AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                appName = resolveInfo.loadLabel(pm).toString()
            )
        }.distinctBy { it.packageName }.sortedBy { it.appName }
        
        _installedApps.value = apps
    }

    fun saveRule(
        id: Int = 0,
        packageName: String,
        appName: String,
        category: AppCategory,
        duration: Int,
        quotes: String?
    ) {
        viewModelScope.launch {
            repository.insertOrUpdate(
                AppRule(
                    id = id,
                    packageName = packageName,
                    appName = appName,
                    category = category,
                    blockDurationSeconds = duration,
                    customQuotes = quotes
                )
            )
        }
    }

    fun toggleRule(rule: AppRule) {
        viewModelScope.launch {
            repository.insertOrUpdate(rule.copy(isEnabled = !rule.isEnabled))
        }
    }

    fun deleteRule(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun recordBlock(rule: AppRule) {
        viewModelScope.launch {
            repository.incrementBlockedCountSimple(rule)
        }
    }
}
