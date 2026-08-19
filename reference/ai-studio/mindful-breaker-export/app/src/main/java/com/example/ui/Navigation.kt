package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: BreakerViewModel
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute.ROUTE
    ) {
        composable(DashboardRoute.ROUTE) {
            DashboardScreen(
                viewModel = viewModel,
                onAddRuleClick = {
                    navController.navigate(AppSelectionRoute.ROUTE)
                },
                onSimulateBlock = { rule ->
                    navController.navigate(SpeedBreakerRoute.createRoute(rule.id))
                }
            )
        }

        composable(AppSelectionRoute.ROUTE) {
            AppSelectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAppSelected = { app ->
                    navController.navigate(
                        ConfigurationRoute.createRoute(
                            packageName = app.packageName,
                            appName = app.appName
                        )
                    )
                }
            )
        }

        composable(ConfigurationRoute.ROUTE) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            val appName = backStackEntry.arguments?.getString("appName") ?: ""

            RuleConfigurationScreen(
                viewModel = viewModel,
                packageName = packageName,
                appName = appName,
                onBack = { navController.popBackStack() },
                onSaveSuccess = {
                    navController.popBackStack(DashboardRoute.ROUTE, inclusive = false)
                }
            )
        }

        composable(SpeedBreakerRoute.ROUTE) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toIntOrNull() ?: 0
            
            // To be fast without making a whole new component viewModel, we can observe the rules list
            val rules by viewModel.rules.collectAsStateWithLifecycle()
            val rule = rules.find { it.id == ruleId }

            if (rule != null) {
                // Record block when screen starts
                LaunchedEffect(rule.id) {
                    viewModel.recordBlock(rule)
                }
                
                SpeedBreakerScreen(
                    appName = rule.appName,
                    durationSeconds = rule.blockDurationSeconds,
                    category = rule.category,
                    openCount = rule.totalLaunchesBlocked,
                    customQuote = rule.customQuotes,
                    onComplete = {
                        // In a real app we would launch the app here. 
                        // For the prototype, we go back.
                        navController.popBackStack()
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
