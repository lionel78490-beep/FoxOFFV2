package com.projectfox.foxoff.core.automation

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
import com.projectfox.foxoff.MainActivity
import com.projectfox.foxoff.R
import com.projectfox.foxoff.brain.FoxBrainEvent
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Duration
import java.time.Instant

/**
 * Câblage PRODUCTION de SleepPauseCountdown : notification actionnable
 * (bouton "Annuler") avant d'exécuter réellement la pause TV. Canal séparé
 * de la surveillance en arrière-plan (IMPORTANCE_HIGH — celui-ci doit
 * attirer l'attention, contrairement à la notification permanente,
 * volontairement discrète). Un seul compte à rebours à la fois, voir
 * SleepPauseCountdown.start().
 */
object SleepPauseCoordinator {

    private const val CHANNEL_ID = "fox_sleep_pause_alert"
    private const val NOTIFICATION_ID = 2001
    private const val ACTION_CANCEL = "com.projectfox.foxoff.action.CANCEL_SLEEP_PAUSE"

    private val COUNTDOWN_DURATION: Duration = Duration.ofSeconds(10)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var receiverRegistered = false

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            FoxLogger.i("FOX-SLEEP | Pause annulée par l'utilisateur")
            countdown.cancel()
        }
    }

    private val countdown = SleepPauseCountdown(
        countdownDuration = COUNTDOWN_DURATION,
        scope = scope,
        onCountdownStarted = { showCountdownNotification() },
        onExecute = { executePause() },
        onCancelled = { onCancelledByUser() }
    )

    val isPending: Boolean get() = countdown.isPending

    // Score au moment où le compte à rebours a RÉELLEMENT démarré (pas à
    // chaque appel redondant pendant qu'il est déjà en cours, voir
    // onSleepDetected) — c'est cette valeur, avec l'heure de détection, qui
    // est conservée dans SleepDetectionHistory pour comparaison future avec
    // Samsung Health.
    @Volatile
    private var pendingDetectedAt: Instant = Instant.now()
    @Volatile
    private var pendingProbability: Float = 0f
    @Volatile
    private var pendingConfidence: Float = 0f

    /** À appeler UNE FOIS depuis FoxCore.startOrchestration(). */
    fun start(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel()
        registerCancelReceiver()
        SleepDetectionHistory.loadAll(context)
    }

    /** Déclenche le compte à rebours annulable. Idempotent si déjà en cours. */
    fun onSleepDetected(sleepProbability: Float, confidence: Float) {
        if (!countdown.isPending) {
            pendingDetectedAt = Instant.now()
            pendingProbability = sleepProbability
            pendingConfidence = confidence
        }
        countdown.start()
    }

    /**
     * Annule le compte à rebours en cours — même effet exact que le bouton
     * "Annuler" de la notification téléphone (voir cancelReceiver
     * ci-dessous), mais déclenchable depuis n'importe où (utilisé par
     * PhoneWearListenerService à la réception de `/foxoff/confirm_response`,
     * la réponse "toujours là" envoyée depuis la montre).
     */
    fun cancel() {
        countdown.cancel()
    }

    private fun createNotificationChannel() {
        val context = appContext ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FoxOFF — Alerte pause sommeil",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerte avant une pause TV automatique liée à la détection de sommeil."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun registerCancelReceiver() {
        val context = appContext ?: return
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            cancelReceiver,
            IntentFilter(ACTION_CANCEL),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun showCountdownNotification() {
        val context = appContext ?: return

        val cancelIntent = Intent(ACTION_CANCEL).setPackage(context.packageName)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("FoxOFF — Endormissement détecté")
            .setContentText("Pause de la TV dans ${COUNTDOWN_DURATION.seconds}s...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Annuler", cancelPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            FoxLogger.e("FOX-SLEEP | Impossible d'afficher la notification (permission révoquée ?)", e)
        }

        // Canal supplémentaire, best-effort : demande à la montre (portée
        // au poignet, donc effectivement capable de réveiller l'utilisateur,
        // contrairement à une notification téléphone) d'afficher le même
        // avertissement. Si la montre est injoignable, l'envoi échoue
        // silencieusement et la notification téléphone ci-dessus reste le
        // filet de sécurité complet — rien n'est dégradé.
        FoxCore.sendToWatch(context, "/foxoff/confirm_awake")

        FoxLogger.i("FOX-SLEEP | Endormissement détecté -> pause dans ${COUNTDOWN_DURATION.seconds}s (annulable)")
        NightLog.record(
            context,
            NightLogType.PAUSE_COUNTDOWN_STARTED,
            "Score ${(pendingProbability * 100).toInt()}%, confiance ${(pendingConfidence * 100).toInt()}%"
        )
    }

    private suspend fun executePause() {
        FoxLogger.i("FOX-SLEEP | Délai écoulé sans annulation -> pause TV")
        FoxCore.tvController?.pause()
        FoxCore.brain.onEvent(FoxBrainEvent.SleepDetected)
        FoxCore.brain.onEvent(FoxBrainEvent.TvCommandSent("Pause Automatique", Instant.now()))
        dismissNotification()
        recordHistory(SleepDetectionOutcome.PAUSED)
        appContext?.let {
            NightLog.record(it, NightLogType.PAUSE_EXECUTED, "TV mise en pause")
            // Économie de batterie montre : plus la peine de remonter le
            // mouvement toutes les 30s une fois le sommeil confirmé — voir
            // MovementEngine.setLowPowerMode(). Best-effort comme le reste
            // des communications montre (voir sendToWatch), aucun risque si
            // la montre est injoignable.
            FoxCore.sendToWatch(it, "/foxoff/movement_low_power")
        }
    }

    private fun onCancelledByUser() {
        dismissNotification()
        FoxCore.brain.onEvent(FoxBrainEvent.SleepCancelled)
        recordHistory(SleepDetectionOutcome.CANCELLED)
        appContext?.let { NightLog.record(it, NightLogType.PAUSE_CANCELLED, "Annulé par l'utilisateur") }
    }

    private fun recordHistory(outcome: SleepDetectionOutcome) {
        val context = appContext ?: return
        SleepDetectionHistory.record(
            context,
            SleepDetectionRecord(
                detectedAt = pendingDetectedAt,
                sleepProbability = pendingProbability,
                confidence = pendingConfidence,
                outcome = outcome
            )
        )
    }

    private fun dismissNotification() {
        val context = appContext ?: return
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: SecurityException) {
            // Sans conséquence : la notification restera affichée jusqu'à ce
            // que l'utilisateur l'efface lui-même.
        }
    }
}
