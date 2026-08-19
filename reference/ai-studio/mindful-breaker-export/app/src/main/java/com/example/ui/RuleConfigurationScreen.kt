package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.AppCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleConfigurationScreen(
    viewModel: BreakerViewModel,
    packageName: String,
    appName: String,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    var category by remember { mutableStateOf(AppCategory.STANDARD) }
    var duration by remember { mutableStateOf(10f) }
    var customQuotes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure $appName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            Text("Breathing Duration: ${duration.toInt()} seconds", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = duration,
                onValueChange = { duration = it },
                valueRange = 5f..60f,
                steps = 11
            )

            Text("App Type", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterChip(
                    selected = category == AppCategory.STANDARD,
                    onClick = { category = AppCategory.STANDARD },
                    label = { Text("Standard") }
                )
                FilterChip(
                    selected = category == AppCategory.REELS,
                    onClick = { category = AppCategory.REELS },
                    label = { Text("Reels / Infinite Scroll") }
                )
            }

            Text("Custom Quote / Reminder (Optional)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = customQuotes,
                onValueChange = { customQuotes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Is this really how you want to spend your time?") },
                minLines = 3
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.saveRule(
                        packageName = packageName,
                        appName = appName,
                        category = category,
                        duration = duration.toInt(),
                        quotes = customQuotes.takeIf { it.isNotBlank() }
                    )
                    onSaveSuccess()
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("SAVE RULE")
            }
        }
    }
}
