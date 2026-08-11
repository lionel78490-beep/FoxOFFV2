package com.projectfox.foxoff.wear.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.projectfox.foxoff.wear.core.FoxWearApplication
import com.projectfox.foxoff.wear.core.FoxWearLogger

/**
 * Miroir côté montre de SleepPauseCoordinator (app/.../core/automation/) :
 * une simple notification actionnable, PAS une alerte plein-écran —
 * décision volontaire (échange avec l'utilisateur, 2026-08-09) pour ne pas
 * réveiller quelqu'un qui dort vraiment, tout en donnant une chance de
 * réagir à quelqu'un encore éveillé. Vibration/affichage gérés par le
 * système selon l'importance du canal (IMPORTANCE_DEFAULT, pas HIGH) —
 * aucun appel direct au Vibrator.
 */
object ConfirmAwakeNotifier {

    private const val CHANNEL_ID = "fox_confirm_awake"
    private const val NOTIFICATION_ID = 3001
    private const val ACTION_STILL_AWAKE = "com.projectfox.foxoff.wear.action.STILL_AWAKE"

    // Miroir informatif du délai réel côté téléphone
    // (SleepPauseCoordinator.COUNTDOWN_DURATION) — la montre n'est pas
    // décisionnaire, elle affiche juste ce texte et se referme d'elle-même
    // au bout de ce délai (setTimeoutAfter), le téléphone reste seul maître
    // du vrai timing de la pause.
    private const val TIMEOUT_MS = 10_000L

    @Volatile
    private var receiverRegistered = false

    private val stillAwakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            FoxWearLogger.i("FOX-WEAR | Confirmation 'toujours là' -> envoi au téléphone")
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            FoxWearApplication.core.notifyStillAwake()
        }
    }

    fun show(context: Context) {
        ensureChannel(context)
        ensureReceiverRegistered(context)

        val actionIntent = Intent(ACTION_STILL_AWAKE).setPackage(context.packageName)
        val actionPendingIntent = PendingIntent.getBroadcast(
            context, 0, actionIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Toujours là ?")
            .setContentText("Pause de la TV dans 10s si pas de réponse")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setTimeoutAfter(TIMEOUT_MS)
            .setAutoCancel(true)
            .addAction(0, "Je suis là", actionPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            FoxWearLogger.i("FOX-WEAR | Confirmation 'toujours là' affichée")
        } catch (e: SecurityException) {
            FoxWearLogger.e("FOX-WEAR | Impossible d'afficher la confirmation (permission révoquée ?)", e)
        }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FoxOFF — Confirmation avant pause",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Demande de confirmation avant la pause automatique de la TV."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun ensureReceiverRegistered(context: Context) {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            stillAwakeReceiver,
            IntentFilter(ACTION_STILL_AWAKE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }
}
