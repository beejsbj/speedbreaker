package com.example.ui

object DashboardRoute {
    const val ROUTE = "dashboard"
}

object AppSelectionRoute {
    const val ROUTE = "app_selection"
}

object ConfigurationRoute {
    const val ROUTE = "configuration/{packageName}/{appName}"
    fun createRoute(packageName: String, appName: String) = "configuration/$packageName/$appName"
}

object SpeedBreakerRoute {
    const val ROUTE = "speed_breaker/{ruleId}"
    fun createRoute(ruleId: Int) = "speed_breaker/$ruleId"
}

