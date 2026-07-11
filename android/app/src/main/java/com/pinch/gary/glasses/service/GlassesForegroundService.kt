package com.pinch.gary.glasses.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pinch.gary.MainActivity
import com.pinch.gary.core.util.Logger
import com.pinch.gary.glasses.GlassesManager
import com.pinch.gary.glasses.model.GlassesConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GlassesForegroundService"
private const val NOTIFICATION_CHANNEL_ID = "gary_glasses_connection"
private const val NOTIFICATION_ID = 1001

/**
 * Keeps the BLE connection (and future mic/voice session) alive while the
 * app is backgrounded. foregroundServiceType="connectedDevice|microphone"
 * is declared in the manifest. This service owns no business logic itself —
 * it only starts [GlassesManager] scanning and mirrors its state into the
 * required persistent notification.
 */
@AndroidEntryPoint
class GlassesForegroundService : Service() {

    @Inject
    lateinit var glassesManager: GlassesManager

    private var serviceJob: Job? = null
    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob())
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(GlassesConnectionState.Disconnected))

        glassesManager.connectionState
            .onEach { state -> updateNotification(state) }
            .launchIn(serviceScope)

        serviceScope.launch {
            glassesManager.startScanning()
        }
        Logger.d(TAG, "GlassesForegroundService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        glassesManager.disconnect()
        serviceScope.cancel()
        super.onDestroy()
        Logger.d(TAG, "GlassesForegroundService destroyed")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Glasses connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the connection status of your Gary glasses"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun updateNotification(state: GlassesConnectionState) {
        val notification = buildNotification(state)
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: GlassesConnectionState): Notification {
        val contentText = when (state) {
            is GlassesConnectionState.Disconnected -> "Not connected"
            is GlassesConnectionState.Scanning -> "Looking for your glasses…"
            is GlassesConnectionState.BleConnected -> "Connected"
            is GlassesConnectionState.Streaming -> "Connected · streaming"
            is GlassesConnectionState.Reconnecting -> "Reconnecting…"
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gary")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
