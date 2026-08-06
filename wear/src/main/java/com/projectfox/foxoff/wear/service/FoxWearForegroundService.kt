package com.projectfox.foxoff.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.projectfox.foxoff.wear.core.FoxWearApplication
import com.projectfox.foxoff.wear.core.FoxWearLogger
import kotlinx.coroutines.*

/**
 * Ensures persistent monitoring on the watch.
 */
class FoxWearForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "fox_wear_channel"
        private const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        FoxWearLogger.i("FoxWearForegroundService created")
        createNotificationChannel()
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FoxWearLogger.i("FOX-WEAR | FoxWearForegroundService | onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())
        
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FoxOFF:MonitoringLock")
            wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours max
            FoxWearLogger.i("FOX-WEAR | FoxWearForegroundService | WakeLock acquis")
        }

        FoxWearApplication.core.notifyForegroundServiceActive(true)
        
        // Keep-alive heartbeat log loop
        serviceScope.launch {
            while(isActive) {
                delay(30000)
                val diag = FoxWearApplication.core.getEngineDiagnostic()
                FoxWearLogger.i("FOX-WEAR | HEARTBEAT | Service ALIVE | $diag")
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoxOFF Monitoring")
            .setContentText("Collecting physiological data")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val name = "FoxOFF Watch Monitoring"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        FoxWearLogger.i("FOX-WEAR | FoxWearForegroundService | onDestroy (Service DÉTRUIT)")
        FoxWearApplication.core.notifyForegroundServiceActive(false)
        wakeLock?.release()
        super.onDestroy()
    }
}
