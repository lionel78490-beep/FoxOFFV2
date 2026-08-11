package com.projectfox.foxoff.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectfox.foxoff.MainActivity
import com.projectfox.foxoff.R
import com.projectfox.foxoff.core.application.BackgroundServiceSettings
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.application.WatchSettings
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.core.presence.WatchConnectionState
import com.projectfox.foxoff.core.presence.WatchConnectionStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service de premier plan (tâche #13) : démarrage, promotion en premier
 * plan, notification reflétant la connectivité montre en direct, arrêt,
 * état réel exposé via FoxForegroundServiceState, et un heartbeat de
 * secours. AUCUN BOOT_COMPLETED à ce stade — sous-étape suivante.
 *
 * Type déclaré : connectedDevice (voir AndroidManifest.xml) — le rôle de
 * ce service est de maintenir la liaison avec la montre, un appareil
 * externe connecté, pas de suivre un capteur santé lui-même.
 *
 * Le heartbeat n'est qu'un FILET DE SÉCURITÉ : la réception passive des
 * messages Data Layer (PhoneWearListenerService) reste le mécanisme
 * principal. Le heartbeat ne fait rien d'autre que rappeler
 * FoxCore.discoverWatch() (déjà protégée contre les recherches
 * concurrentes) — aucun WakeLock, aucune classe TV, aucune commande,
 * aucune pause automatique.
 */
class FoxForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "fox_background_surveillance"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var instance: FoxForegroundService? = null

        /**
         * Relance le heartbeat de reconnexion s'il s'était arrêté (voir
         * startHeartbeat() — arrêt volontaire une fois la TV en pause, pour
         * économiser la batterie le reste de la nuit). Appelé quand
         * l'utilisateur rouvre l'application (ProcessLifecycleOwner.onStart,
         * voir FoxApplication) : à ce moment-là il est réveillé, donc la
         * reconnexion montre/TV redevient utile — sans attendre que
         * l'utilisateur tue puis relance le processus. Sans effet si le
         * service ne tourne pas du tout, ou si le heartbeat tourne déjà.
         */
        fun restartHeartbeatIfStopped() {
            instance?.restartHeartbeatIfStopped()
        }

        // Compromis batterie/fiabilité choisi manuellement (ce service
        // n'utilise pas WorkManager, cette valeur ne lui est pas liée).
        // Constante isolée pour rester facile à ajuster.
        private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L

        // Vérification de visibilité (permission/canal) — nettement plus
        // rapide que le heartbeat ci-dessus (qui sert la reconnexion montre,
        // pas la visibilité) : la promesse de transparence FoxOFF ("pas de
        // notification visible = pas de surveillance") doit être honorée en
        // l'ordre de la minute, pas de 15 minutes.
        private const val VISIBILITY_CHECK_INTERVAL_MS = 60 * 1000L

        private const val ACTION_NOTIFICATION_DISMISSED =
            "com.projectfox.foxoff.action.SURVEILLANCE_NOTIFICATION_DISMISSED"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var deleteReceiverRegistered = false

    @Volatile
    private var heartbeatStopped = false

    // Certains appareils/versions Android permettent de balayer une
    // notification "ongoing" (setOngoing(true) n'empêche pas toujours le
    // swipe, notamment sur certaines surcouches ou depuis Android 14). Ce
    // BroadcastReceiver capte ce balayage explicite pour arrêter la
    // surveillance IMMÉDIATEMENT, plutôt que d'attendre le prochain contrôle
    // périodique — cohérent avec la décision de transparence FoxOFF : si
    // l'utilisateur retire la notification, il ne veut plus de la
    // surveillance.
    private val notificationDismissedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            FoxLogger.w("FOX-BG | Notification balayée par l'utilisateur -> arrêt immédiat de la surveillance")
            failAndStop("Notification balayée : la surveillance a été désactivée.")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Couvre un redémarrage START_STICKY après une mort du processus :
        // onCreate() est réinvoqué directement par le système AVANT même
        // onStartCommand(), donc la vérification doit aussi être faite ici,
        // pas seulement là-bas (voir onStartCommand). Ne continue que si
        // l'intention persistée est toujours activée.
        if (!BackgroundServiceSettings.isEnabled(applicationContext)) {
            FoxLogger.i("FOX-BG | onCreate() avec intention désactivée -> arrêt immédiat, pas de promotion")
            FoxForegroundServiceState.update(FoxForegroundServiceStatus.STOPPED)
            stopSelf()
            return
        }

        FoxForegroundServiceState.update(FoxForegroundServiceStatus.STARTING)
        createNotificationChannel()

        if (!prerequisitesMet()) {
            FoxLogger.e("FOX-BG | Prérequis manquants pour la promotion au premier plan (permission ou notification invisible)")
            failAndStop("Permission Bluetooth ou notification indisponible : la surveillance n'a pas pu démarrer.")
            return
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(
                    watchStatusText(
                        FoxCore.brain.state.value.watchConnected,
                        FoxCore.brain.state.value.watchName,
                        WatchConnectionStateHolder.state.value
                    )
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            FoxForegroundServiceState.update(FoxForegroundServiceStatus.RUNNING)
            FoxLogger.i("FOX-BG | Service de premier plan démarré")

            registerNotificationDismissedReceiver()
            observeWatchStatus()
            performInitialPresenceCheck()
            startHeartbeat()
            startVisibilityWatchdog()
        } catch (e: SecurityException) {
            FoxLogger.e("FOX-BG | SecurityException lors de la promotion au premier plan", e)
            failAndStop("Permission refusée par le système : la surveillance a été désactivée.")
        } catch (e: IllegalStateException) {
            // Couvre ForegroundServiceStartNotAllowedException (API 31+) sans
            // référencer directement cette classe, absente des SDK antérieurs.
            FoxLogger.e("FOX-BG | Démarrage en premier plan refusé par le système", e)
            failAndStop("Démarrage en arrière-plan refusé par le système : la surveillance a été désactivée.")
        } catch (e: Exception) {
            FoxLogger.e("FOX-BG | Échec inattendu de la promotion au premier plan", e)
            failAndStop("Échec inattendu au démarrage : la surveillance a été désactivée.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Intent nul = redémarrage START_STICKY par le système après une mort
        // du processus (pas un appel normal via startForegroundService, qui
        // fournit toujours un Intent). onCreate() vient de s'exécuter juste
        // avant avec la même vérification — celle-ci est une seconde garde,
        // au cas où le process aurait déjà eu une instance vivante dont
        // seul onStartCommand() est rappelé (Intent nul, onCreate() non
        // réinvoqué) : ne continue que si l'intention persistée est
        // toujours activée.
        if (intent == null && !BackgroundServiceSettings.isEnabled(applicationContext)) {
            FoxLogger.i("FOX-BG | Redémarrage STICKY (Intent nul) avec intention désactivée -> arrêt")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        FoxForegroundServiceState.update(FoxForegroundServiceStatus.STOPPED)
        unregisterNotificationDismissedReceiver()
        scope.cancel()
        super.onDestroy()
    }

    private fun registerNotificationDismissedReceiver() {
        ContextCompat.registerReceiver(
            this,
            notificationDismissedReceiver,
            IntentFilter(ACTION_NOTIFICATION_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        deleteReceiverRegistered = true
    }

    private fun unregisterNotificationDismissedReceiver() {
        if (!deleteReceiverRegistered) return
        try {
            unregisterReceiver(notificationDismissedReceiver)
        } catch (e: IllegalArgumentException) {
            // Déjà désenregistré (ex: failAndStop() appelé deux fois) — sans conséquence.
        }
        deleteReceiverRegistered = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Ne rien faire : un vrai service de premier plan doit survivre au
        // retrait de FoxOFF des applications récentes (déjà validé sur
        // matériel réel). Ne surtout pas appeler stopSelf() ici.
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Réception PASSIVE (chemin principal) : suit FoxCore.brain.state ET
     * WatchConnectionStateHolder (même source de vérité que le Dashboard,
     * voir DashboardViewModel), et met à jour la notification en
     * conséquence pour refléter les 4 états (Connectée/Vérification/Hors
     * ligne/Reconnexion). N'appelle jamais discoverWatch() ici — c'est le
     * rôle exclusif du heartbeat ci-dessous.
     */
    private fun observeWatchStatus() {
        scope.launch {
            combine(FoxCore.brain.state, WatchConnectionStateHolder.state) { brainState, connState ->
                brainState to connState
            }.collectLatest { (state, connState) ->
                FoxLogger.i(
                    "FOX-BG | Réception passive | État montre changé -> " +
                            "connectée=${state.watchConnected} (${state.watchName}) | $connState"
                )
                updateNotification(watchStatusText(state.watchConnected, state.watchName, connState))
            }
        }
    }

    /**
     * Vérification immédiate au démarrage du service : ne pas attendre le
     * premier heartbeat (jusqu'à 15 min) pour confirmer si la montre est
     * réellement joignable. Si aucun nœud n'est joignable,
     * FoxCore.discoverWatch() bascule déjà l'état à "Hors ligne" (voir sa
     * branche nodes.isEmpty()) au lieu de conserver un ancien état connecté.
     */
    private fun performInitialPresenceCheck() {
        scope.launch {
            FoxLogger.i("FOX-BG | Démarrage | Vérification immédiate de présence de la montre")
            FoxCore.discoverWatch(applicationContext)
        }
    }

    /**
     * Filet de sécurité : rappelle périodiquement FoxCore.discoverWatch()
     * pour se remettre d'une éventuelle réception passive silencieusement
     * interrompue. Ne prend aucun WakeLock (delay() en mode "best effort" :
     * peut être retardé par Doze, c'est volontaire, jamais contourné) et
     * profite du verrou anti-concurrence déjà présent dans discoverWatch().
     * La visibilité de la notification est vérifiée séparément et bien plus
     * souvent — voir startVisibilityWatchdog().
     *
     * Revalide aussi la TV utilisée (refreshActiveDevice()) : le statut
     * `TvConnectionStatus` n'est mis à jour QUE lors d'une tentative
     * explicite (démarrage de l'app, ou bouton "Réessayer" côté UI) —
     * `TvRemoteClient` ouvre/ferme une connexion à chaque usage, il n'y a
     * aucune connexion persistante dont la coupure pourrait être détectée
     * passivement. Sans cette revalidation périodique, le statut restait
     * bloqué à CONNECTED indéfiniment même après extinction physique de la
     * TV — bug réel constaté (2026-08-08) : aucun événement TV_OFF dans
     * NightLog malgré une TV éteinte pendant la nuit. `refreshActiveDevice()`
     * ne fait qu'un test de connexion en lecture seule (voir
     * TvRemoteClient.testConnection()), jamais de commande.
     *
     * S'arrête (jusqu'à ce que l'utilisateur rouvre l'app, voir
     * restartHeartbeatIfStopped() ci-dessus) dès que `tvIsPaused` est vrai
     * — le sommeil a été confirmé et la TV déjà mise en pause, donc plus
     * aucune reconnexion montre/TV n'a d'utilité tant qu'il dort : économie
     * de batterie demandée explicitement par l'utilisateur. `tvIsPaused` ne
     * repasse jamais à `false` de lui-même (voir FoxBrain.kt) — seule la
     * réouverture de l'app relance la boucle.
     */
    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                FoxLogger.i(
                    "FOX-BG | Heartbeat | Reconnexion de secours " +
                            "(toutes les ${HEARTBEAT_INTERVAL_MS / 60_000} min, montre + statut TV, aucune commande)"
                )
                FoxCore.discoverWatch(applicationContext)
                FoxCore.tvEngine?.refreshActiveDevice()

                if (FoxCore.brain.state.value.tvIsPaused) {
                    FoxLogger.i(
                        "FOX-BG | Heartbeat | TV déjà en pause (sommeil confirmé) -> arrêt de la boucle jusqu'à réouverture de l'app"
                    )
                    heartbeatStopped = true
                    break
                }
            }
        }
    }

    /**
     * Relance immédiatement une vérification (comme
     * performInitialPresenceCheck() au tout premier démarrage) puis reprend
     * la boucle périodique — pas la peine d'attendre jusqu'à 15 min de plus
     * puisque l'utilisateur vient justement de rouvrir l'app.
     */
    private fun restartHeartbeatIfStopped() {
        if (!heartbeatStopped) return
        heartbeatStopped = false
        scope.launch {
            FoxCore.discoverWatch(applicationContext)
            FoxCore.tvEngine?.refreshActiveDevice()
        }
        startHeartbeat()
    }

    /**
     * Vérifie la visibilité de la notification (permission POST_NOTIFICATIONS,
     * NotificationManagerCompat.areNotificationsEnabled(), importance du
     * canal) à un rythme rapide — décision de transparence FoxOFF : si la
     * notification n'est plus visible pour l'utilisateur (désactivée dans
     * les réglages système, canal coupé...), la surveillance doit s'arrêter
     * en l'ordre de la minute, pas silencieusement continuer jusqu'au
     * prochain heartbeat de 15 min. Complémentaire du
     * notificationDismissedReceiver (balayage explicite, instantané) : ce
     * watchdog couvre les cas où la notification est coupée sans balayage
     * direct (ex: désactivation depuis les réglages système).
     */
    private fun startVisibilityWatchdog() {
        scope.launch {
            while (isActive) {
                delay(VISIBILITY_CHECK_INTERVAL_MS)
                if (!FoxNotificationVisibility.isVisible(this@FoxForegroundService, CHANNEL_ID)) {
                    FoxLogger.e("FOX-BG | Visibilité perdue (permission/canal) -> arrêt de la surveillance")
                    failAndStop("Notification désactivée dans les réglages système : la surveillance a été désactivée.")
                    return@launch
                }
            }
        }
    }

    /**
     * Vérifie les prérequis du type connectedDevice (notamment
     * BLUETOOTH_CONNECT sur les versions concernées) ET la visibilité
     * réelle de la notification — les deux doivent être vrais avant toute
     * tentative de promotion.
     */
    private fun prerequisitesMet(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return FoxNotificationVisibility.isVisible(this, CHANNEL_ID)
    }

    private fun failAndStop(reason: String) {
        FoxForegroundServiceState.update(FoxForegroundServiceStatus.ERROR)
        // L'intention persistée ne doit jamais rester "activée" si le
        // service n'a en réalité jamais pu tourner : sans ça, Réglages
        // afficherait un interrupteur activé qui ne correspond à rien.
        // BackgroundServiceSettings.setEnabled() met aussi à jour son
        // miroir réactif (enabledState) — Réglages, qui l'observe via
        // collectAsState(), se corrige immédiatement même si l'écran est
        // déjà ouvert (ne dépend plus d'un remember lu une seule fois).
        BackgroundServiceSettings.setEnabled(applicationContext, false)
        BackgroundServiceDisableReason.set(reason)
        stopSelf()
    }

    /**
     * Reflète les 4 états de WatchConnectionState — même libellés que la
     * carte Accueil (voir DashboardViewModel/DeviceCard), notification et
     * Dashboard observant la même source de vérité.
     */
    private fun watchStatusText(watchConnected: Boolean, watchName: String, connectionState: WatchConnectionState): String {
        return when (connectionState) {
            WatchConnectionState.VERIFYING -> "Vérification de la montre…"
            WatchConnectionState.RECONNECTING -> "Reconnexion à la montre…"
            WatchConnectionState.CONNECTED -> "Montre connectée : $watchName"
            WatchConnectionState.OFFLINE -> {
                if (watchConnected) {
                    // Cas transitoire (état pas encore rafraîchi) : reste honnête.
                    "Montre connectée : $watchName"
                } else {
                    // Nom mémorisé conservé même hors ligne (voir WatchSettings) :
                    // "Galaxy Watch8 — Hors ligne" plutôt qu'un générique "Aucune
                    // montre" qui ferait perdre l'information déjà connue.
                    val lastKnownName = WatchSettings.getLastKnownWatchName(applicationContext)
                    if (lastKnownName != null) "$lastKnownName — Hors ligne" else "En attente de la montre..."
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FoxOFF — Surveillance active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification permanente pendant que FoxOFF surveille votre montre en arrière-plan."
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun updateNotification(statusText: String) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (e: SecurityException) {
            // Permission notifications révoquée en cours de route : ne fait
            // pas planter le service (le prochain heartbeat détectera
            // l'invisibilité et arrêtera proprement, voir startHeartbeat()).
            FoxLogger.e("FOX-BG | Impossible de mettre à jour la notification (permission révoquée ?)", e)
        }
    }

    private fun buildNotification(statusText: String): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Voir notificationDismissedReceiver : setOngoing(true) n'empêche pas
        // toujours le balayage (certaines surcouches, Android 14+) — ce
        // deleteIntent permet de détecter ce cas et d'arrêter la
        // surveillance immédiatement plutôt que de continuer silencieusement.
        val deleteIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_NOTIFICATION_DISMISSED).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoxOFF — Surveillance active")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .build()
    }
}
