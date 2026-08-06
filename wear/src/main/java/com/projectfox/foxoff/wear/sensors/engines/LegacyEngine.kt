package com.projectfox.foxoff.wear.sensors.engines

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
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
 * Wear engine using standard Android SensorManager.
 */
class LegacyEngine(private val context: Context) : WearSensorEngine, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val _samples = MutableSharedFlow<WearHeartRateSample>(extraBufferCapacity = 64)
    override val samples: Flow<WearHeartRateSample> = _samples.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitoring = false

    override suspend fun initialize() {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS)
        FoxWearLogger.i("LegacyEngine initialized. Permission BODY_SENSORS = ${permission == PackageManager.PERMISSION_GRANTED}")
    }

    override suspend fun start() {
        if (heartRateSensor == null) {
            FoxWearLogger.e("Heart Rate sensor not available")
            return
        }

        val registered = sensorManager.registerListener(
            this,
            heartRateSensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )

        if (registered) {
            isMonitoring = true
            FoxWearLogger.i("LegacyEngine started")
        } else {
            FoxWearLogger.e("LegacyEngine failed to start")
        }
    }

    override suspend fun stop() {
        isMonitoring = false
        sensorManager.unregisterListener(this)
        FoxWearLogger.i("LegacyEngine stopped")
    }

    override suspend fun shutdown() {
        stop()
        scope.cancel()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val hr = event.values[0]
            FoxWearLogger.i("FOX-WEAR | Capteur | BPM détecté: $hr (via SensorManager Legacy)")

            scope.launch {
                _samples.emit(
                    WearHeartRateSample(
                        bpm = hr,
                        accuracy = event.accuracy,
                        timestamp = Instant.now(),
                        backend = WearSensorBackend.LEGACY
                    )
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
