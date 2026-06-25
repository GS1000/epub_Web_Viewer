package com.example.epubwebviewer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sleep settings
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

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.resetToDefaults() }) {
                        Text("Reset to defaults")
                    }
                }
            }

            // Other settings can be added here later
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
                    Text("EPUB Viewer v1.0\nSleep mode saves battery when idle.")
                }
            }
        }
    }
}