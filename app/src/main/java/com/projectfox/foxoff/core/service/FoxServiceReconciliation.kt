package com.projectfox.foxoff.core.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.projectfox.foxoff.core.application.ActiveHoursSettings
import com.projectfox.foxoff.core.application.BackgroundServiceSettings
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/**
 * Raison affichée dans Réglages quand la réconciliation a dû désactiver la
 * surveillance (permissions/notification indisponibles) — distincte du
 * refus de notification pendant l'onboarding, qui reste géré localement
 * dans SettingsScreen. Mise à jour depuis le cycle de vie de l'application
 * (voir FoxApplication), donc doit être un état partagé, pas un `remember`
 * local à la composition.
 */
object BackgroundServiceDisableReason {
    private val _reason = MutableStateFlow<String?>(null)
    val reason: StateFlow<String?> = _reason.asStateFlow()

    fun set(reason: String?) {
        _reason.value = reason
    }
}

/**
 * Câblage PRODUCTION de ServiceReconciler : lit les vrais prérequis Android
 * (permission Bluetooth, visibilité de la notification), et exécute les
 * vrais démarrage/arrêt de FoxForegroundService. Appelé depuis le cycle de
 * vie de l'APPLICATION (ProcessLifecycleOwner.ON_START, voir
 * FoxApplication), jamais depuis une recomposition Compose — une
 * recomposition peut se déclencher pour des raisons purement visuelles,
 * sans rapport avec un retour réel au premier plan.
 */
object FoxServiceReconciliation {

    // Construit paresseusement au premier appel avec le Context applicatif
    // (stable pour toute la durée du processus) — voir RealActions.
    private var activeReconciler: ServiceReconciler? = null

    fun reconcileNow(context: Context) {
        val appContext = context.applicationContext
        val instance = activeReconciler ?: ServiceReconciler(RealActions(appContext)).also { activeReconciler = it }

        val userWantsMonitoring = BackgroundServiceSettings.isEnabled(appContext)
        val selectedSlots = ActiveHoursSettings.getSelectedSlots(appContext)
        val withinActiveHours = ActiveHoursSettings.isWithinSelectedSlots(LocalTime.now().hour, selectedSlots)
        // Hors créneau sélectionné, traité exactement comme une intention
        // désactivée : la surveillance s'arrête (ou ne démarre pas) sans
        // toucher au réglage principal (BackgroundServiceSettings reste à
        // true, prêt à repartir au prochain créneau, voir le worker
        // périodique FoxActiveHoursWorker qui rappelle reconcileNow()).
        val intentEnabled = userWantsMonitoring && withinActiveHours
        val realStatus = FoxForegroundServiceState.status.value
        val bluetoothGranted = Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val notificationsVisible = FoxNotificationVisibility.isVisible(appContext, FoxForegroundService.CHANNEL_ID)

        val action = instance.reconcile(intentEnabled, realStatus, bluetoothGranted, notificationsVisible)
        FoxLogger.i(
            "FOX-RECONCILE | intention=$userWantsMonitoring horsCréneau=${!withinActiveHours} réel=$realStatus " +
                    "bluetooth=$bluetoothGranted notification=$notificationsVisible -> $action"
        )
    }

    private class RealActions(private val context: Context) : ServiceReconciliationActions {
        override fun start() {
            BackgroundServiceDisableReason.set(null)
            ContextCompat.startForegroundService(context, Intent(context, FoxForegroundService::class.java))
        }

        override fun stop() {
            context.stopService(Intent(context, FoxForegroundService::class.java))
        }

        override fun disableAndStop(reason: String) {
            BackgroundServiceDisableReason.set(reason)
            BackgroundServiceSettings.setEnabled(context, false)
            context.stopService(Intent(context, FoxForegroundService::class.java))
            FoxLogger.w("FOX-RECONCILE | Surveillance désactivée automatiquement : $reason")
        }
    }
}
