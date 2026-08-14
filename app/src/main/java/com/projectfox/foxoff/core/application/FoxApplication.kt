package com.projectfox.foxoff.core.application

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.core.service.FoxActiveHoursWorker
import com.projectfox.foxoff.core.service.FoxForegroundService
import com.projectfox.foxoff.core.service.FoxForegroundServiceState
import com.projectfox.foxoff.core.service.FoxForegroundServiceStatus
import com.projectfox.foxoff.core.service.FoxServiceReconciliation
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Graphe Hilt disponible dès maintenant (voir DECISIONS.md ADR-003), mais
 * FoxCore reste pour l'instant un object auto-suffisant, non injecté —
 * cette annotation pose seulement la fondation, sans changer le
 * comportement de démarrage ci-dessous.
 */
@HiltAndroidApp
class FoxApplication : Application() {
    // IO, pas Main : FoxCore.initialize() (et tout ce qu'il déclenche —
    // WatchSettings, RestingBpmSettings, NightLog, SleepDetectionHistory)
    // lit des SharedPreferences chiffrées (voir FoxEncryptedPrefs), dont le
    // tout premier accès génère la clé maître Android Keystore — une vraie
    // opération cryptographique, pas instantanée. La faire sur Main
    // bloquait le thread UI au démarrage (régression réelle constatée le
    // 2026-08-12 : jusqu'à 3-4 min de blocage après réinstallation).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FoxLogger.i("FoxApplication started")

        // État réel du service de premier plan : repart toujours de STOPPED
        // à un nouveau démarrage de processus. Si le service tourne
        // réellement dans CE processus, son propre onCreate() le remettra à
        // RUNNING juste après (voir FoxForegroundService) — jamais de valeur
        // héritée d'un processus précédent, qui n'existe plus de toute façon.
        FoxForegroundServiceState.update(FoxForegroundServiceStatus.STOPPED)

        // Amorce le cache FoxEncryptedPrefs pour tous les fichiers connus,
        // le plus tôt possible et en arrière-plan — la clé maître Android
        // Keystore n'est générée qu'UNE SEULE fois (partagée entre tous les
        // fichiers), donc ce warm-up réduit d'autant la latence du tout
        // premier écran (Réglages, détection montre...) qui touchera l'un
        // de ces fichiers de façon synchrone plus tard.
        appScope.launch {
            listOf(
                "fox_active_hours_settings",
                "fox_background_service_settings",
                "fox_resting_bpm_settings",
                "fox_watch_settings",
                "fox_night_log",
                "fox_sleep_detection_history",
                "fox_tv_settings",
                "foxoff_tv_identity_rsa_v1",
                "foxoff_tv_pairs"
            ).forEach { name ->
                com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(this@FoxApplication, name)
            }
        }

        // Synchronise les miroirs réactifs dès le lancement, pour que tout
        // composable qui les observe (SettingsScreen) ait immédiatement la
        // bonne valeur, sans dépendre d'un premier appel incident ailleurs.
        // Sur IO, pas sur le thread appelant (onCreate() tourne sur Main) —
        // même raison que le dispatcher d'appScope ci-dessus.
        appScope.launch {
            BackgroundServiceSettings.isEnabled(this@FoxApplication)
        }

        // Seul déclencheur capable de démarrer/arrêter la surveillance au
        // bon moment d'après un créneau horaire (ActiveHoursSettings) sans
        // que l'utilisateur ait besoin de rouvrir l'app pile à l'heure —
        // voir FoxActiveHoursWorker. enqueueUniquePeriodicWork(..., KEEP)
        // est idempotent : sans effet si déjà planifié depuis un précédent
        // lancement du processus.
        FoxActiveHoursWorker.schedule(this)

        appScope.launch {
            FoxCore.initialize(this@FoxApplication)
        }

        // Réconciliation intention/état réel à chaque retour de l'app au
        // premier plan (ProcessLifecycleOwner.ON_START — niveau application,
        // pas Activity : ne se redéclenche pas à chaque recréation d'Activity
        // pour un changement de configuration). Corrige le cas où
        // BackgroundServiceSettings.isEnabled() vaut true mais le service
        // n'a jamais pu (re)démarrer (relance de processus, nouvelle
        // installation) — jamais appelé depuis une recomposition Compose.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                FoxServiceReconciliation.reconcileNow(this@FoxApplication)
                // Relance le heartbeat de reconnexion montre/TV s'il s'était
                // arrêté pour économiser la batterie après une pause auto
                // (voir FoxForegroundService) — l'utilisateur qui rouvre
                // l'app est réveillé, la reconnexion redevient utile.
                FoxForegroundService.restartHeartbeatIfStopped()
            }
        })
    }
}
