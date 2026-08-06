package com.projectfox.foxoff.wear.service

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.projectfox.foxoff.wear.core.FoxWearApplication
import com.projectfox.foxoff.wear.core.FoxWearLogger
import com.projectfox.foxoff.wear.sensors.model.WearHeartRateSample
import com.projectfox.foxoff.wear.sensors.model.WearSensorBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Service called by Health Services when new passive data is available.
 * This runs even if the main FoxOFF app is closed.
 */
class PassiveDataService : PassiveListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hrPoints = dataPoints.sampleDataPoints.filter { it.dataType == DataType.HEART_RATE_BPM }
        
        if (hrPoints.isNotEmpty()) {
            val latest = hrPoints.last()
            val bpm = (latest.value as? Double)?.toFloat() ?: 0f
            
            FoxWearLogger.i("FOX-WEAR | PassiveService | BPM Reçu en arrière-plan : $bpm")

            serviceScope.launch {
                val sample = WearHeartRateSample(
                    bpm = bpm,
                    accuracy = 3,
                    timestamp = Instant.now(),
                    backend = WearSensorBackend.HEALTH_SERVICES
                )
                
                // Route to the same communication pipeline as the live engine
                FoxWearApplication.core.sendPassiveSample(sample)
            }
        }
    }
}
