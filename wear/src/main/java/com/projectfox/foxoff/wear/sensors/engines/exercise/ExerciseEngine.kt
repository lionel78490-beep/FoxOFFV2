package com.projectfox.foxoff.wear.sensors.engines.exercise

import android.content.Context
import androidx.concurrent.futures.await
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesException
import androidx.health.services.client.data.*
import com.projectfox.foxoff.wear.core.FoxWearLogger
import com.projectfox.foxoff.wear.sensors.api.WearSensorEngine
import com.projectfox.foxoff.wear.sensors.model.WearHeartRateSample
import com.projectfox.foxoff.wear.sensors.model.WearSensorBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Wear engine using Health Services ExerciseClient.
 * Stabilized version for Wear OS 6 (Galaxy Watch 8).
 */
class ExerciseEngine(private val context: Context) : WearSensorEngine {

    private val exerciseClient = HealthServices.getClient(context).exerciseClient
    private val startMutex = Mutex()
    
    init {
        FoxWearLogger.i("FOX-WEAR | ExerciseEngine | Session ExerciseClient créée")
    }

    private val _samples = MutableSharedFlow<WearHeartRateSample>(extraBufferCapacity = 64)
    override val samples: Flow<WearHeartRateSample> = _samples.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var watchdogJob: Job? = null
    
    private var _engineState = ExerciseState.IDLE
    val engineState: ExerciseState get() = _engineState

    private var stats = ExerciseStats()
    private val startTime = Instant.now()
    
    private var lastAvailability: Availability = DataTypeAvailability.UNKNOWN

    private var lastBatchTime: Instant? = null

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val now = Instant.now()
            val stateInfo = update.exerciseStateInfo
            val currentState = stateInfo.state
            
            val heartRateData = update.latestMetrics.sampleDataPoints.filter { it.dataType == DataType.HEART_RATE_BPM }
            
            // --- LOGS D'AUDIT BATCHING ---
            if (heartRateData.isNotEmpty()) {
                val lastSample = heartRateData.last()
                val batchDelay = lastBatchTime?.let { java.time.Duration.between(it, now).seconds } ?: 0
                
                FoxWearLogger.i("FOX-WEAR | BATCH REÇU | " +
                    "Samples: ${heartRateData.size} | " +
                    "Délai depuis dernier lot: ${batchDelay}s | " +
                    "Dernier BPM: ${lastSample.value}")
                
                lastBatchTime = now
            } else {
                FoxWearLogger.d("FOX-WEAR | UPDATE REÇU | Aucun BPM dans ce lot (État: $currentState)")
            }
            // -----------------------------

            if (currentState == androidx.health.services.client.data.ExerciseState.ACTIVE) {
                if (_engineState != ExerciseState.RUNNING) {
                    FoxWearLogger.i("FOX-WEAR | ExerciseUpdateCallback | State changed to ACTIVE")
                    _engineState = ExerciseState.RUNNING
                }
            }
            
            if (heartRateData.isNotEmpty()) {
                stats = stats.copy(
                    batchCount = stats.batchCount + 1,
                    sampleCount = stats.sampleCount + heartRateData.size,
                    uptimeMillis = Instant.now().toEpochMilli() - startTime.toEpochMilli(),
                    lastSampleTime = Instant.now()
                )

                heartRateData.forEach { point ->
                    val hr = (point.value as? Double)?.toFloat() ?: 0.0f
                    _samples.tryEmit(
                        WearHeartRateSample(
                            bpm = hr,
                            accuracy = 3,
                            timestamp = Instant.now(),
                            backend = WearSensorBackend.EXERCISE
                        )
                    )
                }
            }

            if (stateInfo.state.isEnded) {
                val endReason = stateInfo.endReason
                FoxWearLogger.w("FOX-WEAR | ExerciseUpdateCallback | onExerciseUpdateReceived | Session ENDED | Reason: $endReason")
                
                if (endReason != 1) { // 1 = USER_TERMINATED
                    handleUnexpectedStop(endReason)
                }
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {
            FoxWearLogger.i("FOX-WEAR | ExerciseUpdateCallback | onLapSummaryReceived")
        }
        
        override fun onRegistered() {
            FoxWearLogger.i("FOX-WEAR | ExerciseUpdateCallback | onRegistered")
        }
        
        override fun onRegistrationFailed(throwable: Throwable) {
            FoxWearLogger.e("FOX-WEAR | ExerciseUpdateCallback | onRegistrationFailed", throwable)
            _engineState = ExerciseState.ERROR
        }
        
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            lastAvailability = availability
            FoxWearLogger.i("FOX-WEAR | ExerciseUpdateCallback | onAvailabilityChanged | Type: ${dataType.name} | Availability: $availability")
        }
    }

    override suspend fun initialize() {
        _engineState = ExerciseState.PREPARING
        FoxWearLogger.i("ExerciseEngine initializing")
    }

    suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        try {
            val capabilities = exerciseClient.getCapabilitiesAsync().await()
            val supportedExerciseTypes = capabilities.supportedExerciseTypes
            val isWorkoutSupported = ExerciseType.WORKOUT in supportedExerciseTypes
            
            val hasHr = if (isWorkoutSupported) {
                val exerciseCapabilities = capabilities.getExerciseTypeCapabilities(ExerciseType.WORKOUT)
                DataType.HEART_RATE_BPM in exerciseCapabilities.supportedDataTypes
            } else {
                false
            }
            
             FoxWearLogger.i("ExerciseClient available = $isWorkoutSupported (HR support = $hasHr)")
            isWorkoutSupported && hasHr
        } catch (e: Exception) {
            FoxWearLogger.e("Error checking Exercise capabilities", e)
            false
        }
    }

    override suspend fun start() = startMutex.withLock {
        FoxWearLogger.i("FOX-WEAR | Tentative de démarrage de l'ExerciseClient")
        if (_engineState == ExerciseState.RUNNING) {
            FoxWearLogger.i("FOX-WEAR | Session déjà active (RUNNING) - Ignoré")
            return@withLock
        }

        try {
            val exerciseInfo = exerciseClient.getCurrentExerciseInfoAsync().await()
            val trackedStatus = exerciseInfo.exerciseTrackedStatus
            
            FoxWearLogger.i("FOX-WEAR | Statut système actuel: $trackedStatus (1=OWNED, 2=OTHER, 0=NONE)")

            // 1 = OWNED_EXERCISE_IN_PROGRESS
            if (trackedStatus == 1) {
                FoxWearLogger.i("FOX-WEAR | Réutilisation de la session existante")
                exerciseClient.setUpdateCallback(callback)
                _engineState = ExerciseState.RUNNING
                startWatchdog()
                return@withLock
            } else if (trackedStatus == 2) {
                FoxWearLogger.w("Exercise tracked by another app. Overriding...")
            }

            FoxWearLogger.i("Nouvelle session créée - Starting fresh setup")
            
            try {
                FoxWearLogger.i("Preparing exercise...")
                _engineState = ExerciseState.PREPARING
                val warmUpConfig = WarmUpConfig(ExerciseType.WORKOUT, setOf(DataType.HEART_RATE_BPM))
                exerciseClient.setUpdateCallback(callback)
                exerciseClient.prepareExerciseAsync(warmUpConfig).await()
                FoxWearLogger.i("Exercise prepared")
            } catch (e: HealthServicesException) {
                if (e.message?.contains("already in progress") == true) {
                    FoxWearLogger.w("Health Services reported session already in progress during prepare. Recovering...")
                    exerciseClient.setUpdateCallback(callback)
                    _engineState = ExerciseState.RUNNING
                    startWatchdog()
                    return@withLock
                }
                throw e
            }

            val config = ExerciseConfig.builder(ExerciseType.WORKOUT)
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .setIsAutoPauseAndResumeEnabled(false)
                .build()

            try {
                FoxWearLogger.i("Starting exercise...")
                exerciseClient.startExerciseAsync(config).await()
                FoxWearLogger.i("FOX-WEAR | ExerciseEngine | startExerciseAsync SUCCESS")
            } catch (e: HealthServicesException) {
                if (e.message?.contains("already in progress") == true) {
                    FoxWearLogger.w("Health Services reported session already in progress during start. Recovering...")
                    exerciseClient.setUpdateCallback(callback)
                    _engineState = ExerciseState.RUNNING
                    startWatchdog()
                    return@withLock
                }
                throw e
            }
            
            _engineState = ExerciseState.RUNNING
            FoxWearLogger.i("Exercise started and confirmed RUNNING")
            
            startWatchdog()
        } catch (e: Exception) {
            FoxWearLogger.e("Failed to start exercise - No crash triggered", e)
            _engineState = ExerciseState.ERROR
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(30000) // 30s check
                
                try {
                    val exerciseInfo = exerciseClient.getCurrentExerciseInfoAsync().await()
                    val trackedStatus = exerciseInfo.exerciseTrackedStatus
                    
                    FoxWearLogger.i("System Exercise Tracked Status: $trackedStatus")

                    val now = Instant.now()
                    val lastSample = stats.lastSampleTime
                    
                    if (_engineState == ExerciseState.RUNNING) {
                        val secondsSinceLastData = if (lastSample != null) {
                            now.epochSecond - lastSample.epochSecond
                        } else {
                            now.epochSecond - startTime.epochSecond
                        }
                        
                        // Periodic stats log
                        FoxWearLogger.i("Exercise Statistics - Uptime: ${stats.uptimeMillis / 1000}s, Samples: ${stats.sampleCount}, Batches: ${stats.batchCount}")
                        
                        // 1 = OWNED_EXERCISE_IN_PROGRESS
                        if (trackedStatus == 1) {
                            if (secondsSinceLastData > 45) {
                                FoxWearLogger.i("No data for $secondsSinceLastData seconds, but session is OWNED_IN_PROGRESS (Batching mode)")
                            }
                        } else {
                            FoxWearLogger.w("Watchdog detected session lost in system (status=$trackedStatus). Triggering recovery...")
                            handleUnexpectedStop(0) // Unknown reason
                        }
                    }
                } catch (e: Exception) {
                    FoxWearLogger.e("Watchdog: Error querying system state", e)
                }
            }
        }
    }

    private fun handleUnexpectedStop(reason: Int) {
        if (reason == 4) { // 4 = AUTO_END_PERMISSION_LOST
            FoxWearLogger.e("Recovery skipped: Permissions lost (Code 4)")
            return
        }
        
        FoxWearLogger.w("Recovery started for reason code: $reason")
        _engineState = ExerciseState.AUTO_RECOVERING
        scope.launch {
            delay(5000)
            try {
                start()
                FoxWearLogger.i("Recovery successful")
            } catch (e: Exception) {
                FoxWearLogger.e("Recovery failed", e)
            }
        }
    }

    override suspend fun stop() {
        watchdogJob?.cancel()
        if (_engineState == ExerciseState.STOPPED || _engineState == ExerciseState.IDLE) {
            FoxWearLogger.i("Arrêt propre de la session - Already stopped or idle")
            return
        }
        
        try {
            exerciseClient.endExerciseAsync().await()
            FoxWearLogger.i("Arrêt propre de la session - Exercise ended successfully")
            FoxWearLogger.i("Final Statistics - Uptime: ${stats.uptimeMillis / 1000}s, Samples: ${stats.sampleCount}")
        } catch (e: Exception) {
            FoxWearLogger.e("Error stopping exercise - Non-critical", e)
        } finally {
            _engineState = ExerciseState.STOPPED
        }
    }

    override suspend fun shutdown() {
        stop()
        scope.cancel()
        _engineState = ExerciseState.IDLE
    }

    override fun getDiagnosticInfo(): String {
        val lastTime = stats.lastSampleTime
        val secondsSinceLast = if (lastTime != null) {
            (Instant.now().toEpochMilli() - lastTime.toEpochMilli()) / 1000
        } else -1
        return "State: $_engineState | Last BPM: ${secondsSinceLast}s ago | Samples: ${stats.sampleCount}"
    }
}
