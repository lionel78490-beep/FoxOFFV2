package com.projectfox.foxoff.wear.sensors.engines

import android.content.Context
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import com.projectfox.foxoff.wear.core.FoxWearLogger
import com.projectfox.foxoff.wear.sensors.api.WearSensorEngine
import com.projectfox.foxoff.wear.sensors.model.WearHeartRateSample
import com.projectfox.foxoff.wear.sensors.model.WearSensorBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant

/**
 * Wear engine using official Health Services API.
 */
class HealthServicesEngine(private val context: Context) : WearSensorEngine {

    private val measureClient = HealthServices.getClient(context).measureClient
    
    private val _samples = MutableSharedFlow<WearHeartRateSample>(extraBufferCapacity = 64)
    override val samples: Flow<WearHeartRateSample> = _samples.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var firstCallbackReceived = false

    init {
        FoxWearLogger.i("MeasureClient created successfully")
    }

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            val status = if (availability is DataTypeAvailability) {
                availability.toString()
            } else {
                "Unknown"
            }
            FoxWearLogger.i("HealthServices Availability changed for ${dataType.name}: $status")
            
            if (availability == DataTypeAvailability.AVAILABLE) {
                FoxWearLogger.i("Sensor is now AVAILABLE")
            } else {
                FoxWearLogger.w("Sensor is NOT AVAILABLE: $status")
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            if (!firstCallbackReceived) {
                firstCallbackReceived = true
                FoxWearLogger.i("FOX-WEAR | Capteur | Premier callback HealthServices reçu")
            }

            for (point in data.sampleDataPoints) {
                if (point.dataType == DataType.HEART_RATE_BPM) {
                    val hr = (point.value as? Double)?.toFloat() ?: 0.0f
                    FoxWearLogger.i("FOX-WEAR | Capteur | BPM détecté: $hr (via MeasureClient)")
                    
                    scope.launch {
                        _samples.emit(
                            WearHeartRateSample(
                                bpm = hr,
                                accuracy = 3,
                                timestamp = Instant.now(),
                                backend = WearSensorBackend.HEALTH_SERVICES
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun initialize() {
        FoxWearLogger.i("HealthServicesEngine initialized")
    }

    /**
     * Checks if heart rate monitoring is supported via Health Services.
     */
    suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        try {
            val capabilities = measureClient.getCapabilitiesAsync().await()
            val supportedDataTypes = capabilities.supportedDataTypesMeasure
            
            val hasHr = DataType.HEART_RATE_BPM in supportedDataTypes
            
            FoxWearLogger.i("Capabilities supported check:")
            FoxWearLogger.i("- HEART_RATE_BPM = $hasHr")
            FoxWearLogger.i("- Total supported types = ${supportedDataTypes.size}")
            
            hasHr
        } catch (e: Exception) {
            FoxWearLogger.e("Error checking Health Services capabilities", e)
            false
        }
    }

    override suspend fun start() {
        try {
            FoxWearLogger.i("Registration to heart rate callback started")
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            FoxWearLogger.i("Callback successfully registered")
        } catch (e: Exception) {
            FoxWearLogger.e("Failed to register Health Services callback", e)
            throw e
        }
    }

    override suspend fun stop() {
        try {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback).await()
            FoxWearLogger.i("HealthServicesEngine stopped")
        } catch (e: Exception) {
            FoxWearLogger.e("Error stopping HealthServicesEngine", e)
        }
    }

    override suspend fun shutdown() {
        stop()
        scope.cancel()
    }
}
