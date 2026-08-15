package com.example.epubwebviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.epubwebviewer.ui.dashboard.DashboardScreen
import com.example.epubwebviewer.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted – notifications can be shown
            } else {
                // Permission denied – app still works without notifications
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ (optional)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        setContent {
            DashboardScreen(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        // Order matters here: the reading server (if any) has already flushed the
        // latest chapter/scroll progress to metadata.json via debounced POSTs while
        // the user was reading, so it's safe to stop the server first...
        viewModel.stopService()
        // ...then reload the book list from disk so the dashboard reflects whatever
        // was just written, instead of showing whatever was loaded before the user
        // opened the book. This is the fix for the dashboard showing stale progress
        // (e.g. "chapter 2" when the browser was actually at chapter 14).
        viewModel.refreshBooks()
    }
}