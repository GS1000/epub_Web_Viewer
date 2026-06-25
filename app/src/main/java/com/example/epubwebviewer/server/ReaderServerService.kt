package com.example.epubwebviewer.server

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.epubwebviewer.R
import com.example.epubwebviewer.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

class ReaderServerService : Service() {

    companion object {
        private const val TAG = "ReaderServerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "reader_server_channel"

        const val EXTRA_BOOK_ID = "book_id"

        private val _portFlow = MutableStateFlow<Int?>(null)
        val portFlow: StateFlow<Int?> = _portFlow

        private val _currentBookId = MutableStateFlow<String?>(null)
        val currentBookId: StateFlow<String?> = _currentBookId
    }

    private var server: LocalHttpServer? = null
    private var sleepCheckJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        settingsRepo = SettingsRepository(this)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        if (bookId.isNullOrEmpty()) {
            Log.e(TAG, "No book ID provided, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        _currentBookId.value = bookId

        // Start as foreground service
        val notification = createNotification(bookId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Foreground service started with notification")

        // Start the server if not already running
        if (server == null) {
            val bookDir = File(filesDir, "books/$bookId")
            if (!bookDir.exists()) {
                Log.e(TAG, "Book directory does not exist: ${bookDir.absolutePath}")
                stopSelf()
                return START_NOT_STICKY
            }

            try {
                // Get sleep delay from settings (in seconds, convert to ms)
                val sleepDelaySec = settingsRepo.getSleepDelaySeconds()
                val sleepDelayMs = if (settingsRepo.isSleepEnabled()) {
                    sleepDelaySec * 1000L
                } else {
                    Long.MAX_VALUE // effectively disable sleep
                }

                server = LocalHttpServer(this, bookDir, sleepDelayMs).apply {
                    start()
                    val port = listeningPort
                    Log.d(TAG, "Server started on port $port")
                    _portFlow.value = port
                }

                // Start sleep monitoring
                startSleepMonitoring()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun startSleepMonitoring() {
        sleepCheckJob?.cancel()
        sleepCheckJob = serviceScope.launch {
            while (isActive) {
                delay(5000) // check every 5 seconds
                server?.checkSleep()
            }
        }
        Log.d(TAG, "Sleep monitoring started")
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepCheckJob?.cancel()
        serviceScope.cancel()
        server?.stop()
        server = null
        _portFlow.value = null
        _currentBookId.value = null

        // Remove notification
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)

        Log.d(TAG, "Server stopped and service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Notification helpers ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EPUB Reader Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the EPUB reader server running while you read."
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(bookId: String): Notification {
        // Get book title
        val bookTitle = try {
            val metaFile = File(filesDir, "books/$bookId/metadata.json")
            if (metaFile.exists()) {
                val json = JSONObject(metaFile.readText())
                json.optString("title", bookId)
            } else {
                bookId
            }
        } catch (e: Exception) {
            bookId
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconResId = try {
            R.drawable.ic_book_notification
        } catch (e: Exception) {
            android.R.drawable.ic_menu_info_details
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📖 EPUB Reader Active")
            .setContentText("Reading: $bookTitle")
            .setSmallIcon(iconResId)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}