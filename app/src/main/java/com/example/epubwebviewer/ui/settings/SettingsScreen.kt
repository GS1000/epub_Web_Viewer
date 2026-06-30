package com.example.epubwebviewer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.epubwebviewer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val sleepEnabled by viewModel.sleepEnabled.collectAsState()
    val sleepDelaySeconds by viewModel.sleepDelaySeconds.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sleep settings card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Server Sleep",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable sleep mode")
                        Switch(
                            checked = sleepEnabled,
                            onCheckedChange = { viewModel.setSleepEnabled(it) }
                        )
                    }

                    if (sleepEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Sleep after idle (seconds)")
                        Slider(
                            value = sleepDelaySeconds.toFloat(),
                            onValueChange = { viewModel.setSleepDelaySeconds(it.toInt()) },
                            valueRange = 10f..300f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${sleepDelaySeconds}s",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Sort order card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sort Order",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val sortOptions = listOf(
                        "last_read_desc" to "Last read (newest first)",
                        "last_read_asc" to "Last read (oldest first)",
                        "title_asc" to "Title (A–Z)",
                        "title_desc" to "Title (Z–A)",
                        "import_desc" to "Date added (newest first)",
                        "import_asc" to "Date added (oldest first)"
                    )

                    sortOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(
                                selected = sortOrder == value,
                                onClick = { viewModel.setSortOrder(value) }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // About card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("EPUB Viewer v2.0\nSleep mode saves battery when idle.\nSort order affects the dashboard list.")
                }
            }

            // Reset button – wrapped in a Row for stable centering
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { viewModel.resetToDefaults() }
                ) {
                    Text("Reset all settings to defaults")
                }
            }
        }
    }
}