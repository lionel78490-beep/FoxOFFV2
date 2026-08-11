package com.projectfox.foxoff.wear.sensors.engines

import android.content.Context
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import com.projectfox.foxoff.wear.core.FoxWearLogger
import com.projectfox.foxoff.wear.sensors.api.WearSensorEngine
import com.projectfox.foxoff.wear.sensors.model.WearHeartRateSample
import com.projectfox.foxoff.wear.sensors.model.WearSensorBackend
import com.projectfox.foxoff.wear.service.PassiveDataService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Engine for long-term, battery-efficient background monitoring.
 * Uses PassiveMonitoringClient to receive batched heart rate data.
 */
class PassiveMonitoringEngine(private val context: Context) : WearSensorEngine {

    private val passiveClient = HealthServices.getClient(context).passiveMonitoringClient

    private val _samples = MutableSharedFlow<WearHeartRateSample>(replay = 1, extraBufferCapacity = 64)
    override val samples: Flow<WearHeartRateSample> = _samples.asSharedFlow()

    // Toutes les erreurs internes étaient auparavant seulement journalisées
    // (Logcat) — exposées ici pour que FoxWearCore puisse les afficher dans
    // l'UI (diagnostic temporaire, voir WearHomeScreen) sans dépendre d'ADB.
    var lastError: String? = null
        private set

    override suspend fun initialize() {
        FoxWearLogger.i("FOX-WEAR | PassiveEngine | Initialisé")
    }

    suspend fun isSupported(): Boolean {
        return try {
            val capabilities = passiveClient.getCapabilitiesAsync().await()
            val supported = DataType.HEART_RATE_BPM in capabilities.supportedDataTypesPassiveMonitoring
            if (!supported) lastError = "HEART_RATE_BPM absent de supportedDataTypesPassiveMonitoring"
            supported
        } catch (e: Exception) {
            lastError = "isSupported(): ${e.javaClass.simpleName} ${e.message}"
            FoxWearLogger.e("FOX-WEAR | PassiveEngine | Erreur vérification support", e)
            false
        }
    }

    override suspend fun start() {
        try {
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .build()

            passiveClient.setPassiveListenerServiceAsync(
                PassiveDataService::class.java,
                config
            ).await()

            lastError = null
            FoxWearLogger.i("FOX-WEAR | PassiveEngine | Surveillance d'arrière-plan activée")
        } catch (e: Exception) {
            lastError = "start(): ${e.javaClass.simpleName} ${e.message}"
            FoxWearLogger.e("FOX-WEAR | PassiveEngine | Erreur activation", e)
        }
    }

    override suspend fun stop() {
        try {
            passiveClient.clearPassiveListenerServiceAsync().await()
            FoxWearLogger.i("FOX-WEAR | PassiveEngine | Surveillance d'arrière-plan désactivée")
        } catch (e: Exception) {
            FoxWearLogger.e("FOX-WEAR | PassiveEngine | Erreur désactivation", e)
        }
    }

    override suspend fun shutdown() {
        stop()
    }

    override fun getDiagnosticInfo(): String {
        return "Mode: PASSIVE | Battery Optimized"
    }
}
