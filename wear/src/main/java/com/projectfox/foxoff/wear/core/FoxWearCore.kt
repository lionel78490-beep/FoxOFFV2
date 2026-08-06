package com.projectfox.foxoff.wear.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.projectfox.foxoff.wear.communication.WearCommunicationManager
import com.projectfox.foxoff.wear.communication.model.ConnectionState
import com.projectfox.foxoff.wear.sensors.api.WearSensorEngine
import com.projectfox.foxoff.wear.sensors.engines.HealthServicesEngine
import com.projectfox.foxoff.wear.sensors.engines.LegacyEngine
import com.projectfox.foxoff.wear.sensors.engines.exercise.ExerciseEngine
import com.projectfox.foxoff.wear.sensors.model.WearHeartRateSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Orchestrator for the FoxOFF Wear application.
 */
class FoxWearCore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: WearSensorEngine? = null
    private val communicationManager = WearCommunicationManager(context)
    
    private val _status = MutableSharedFlow<String>(replay = 1)
    val status: Flow<String> = _status.asSharedFlow()

    private val _isMonitoring = MutableSharedFlow<Boolean>(replay = 1)
    val isMonitoring: Flow<Boolean> = _isMonitoring.asSharedFlow()

    private val _isForegroundServiceActive = MutableStateFlow(false)
    val isForegroundServiceActive: StateFlow<Boolean> = _isForegroundServiceActive.asStateFlow()

    val connectionState: Flow<ConnectionState> = communicationManager.connectionState

    private val _samples = MutableSharedFlow<WearHeartRateSample>(replay = 1, extraBufferCapacity = 64)
    val heartRateSamples: Flow<WearHeartRateSample> = _samples.asSharedFlow()

    init {
        _status.tryEmit("READY")
        _isMonitoring.tryEmit(false)
        
        // Start periodic info sending
        scope.launch {
            while (true) {
                sendDeviceInfo()
                delay(60000) // Every 1 minute
            }
        }
    }

    private suspend fun sendDeviceInfo() {
        FoxWearLogger.d("FOX-WEAR | Collecte et envoi des infos de la montre au téléphone...")
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val batteryPct: Int = batteryStatus?.let { intent ->
            val level: Int = intent.getIntOfExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntOfExtra(BatteryManager.EXTRA_SCALE, -1)
            (level * 100 / scale.toFloat()).toInt()
        } ?: 0

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        communicationManager.sendWatchInfo(deviceName, batteryPct)
    }

    private fun Intent.getIntOfExtra(name: String, defaultValue: Int): Int {
        return getIntExtra(name, defaultValue)
    }

    suspend fun initialize() {
        FoxWearLogger.i("FoxWearCore initializing")
        
        // Engine selection logic (AUTO)
        val exerciseEngine = ExerciseEngine(context)
        val isExerciseSupported = exerciseEngine.isSupported()
        
        val hsEngine = HealthServicesEngine(context)
        val isHsSupported = hsEngine.isSupported()
        
        FoxWearLogger.i("Exercise Client supported = $isExerciseSupported")
        FoxWearLogger.i("Health Services available = $isHsSupported")
        
        engine = when {
            isExerciseSupported -> {
                FoxWearLogger.i("Selected backend = EXERCISE")
                exerciseEngine
            }
            isHsSupported -> {
                FoxWearLogger.i("Selected backend = HEALTH_SERVICES")
                hsEngine
            }
            else -> {
                FoxWearLogger.i("Selected backend = LEGACY (Fallback)")
                LegacyEngine(context)
            }
        }
        
        try {
            engine?.initialize()
            FoxWearLogger.i("Backend initialized successfully")
            _status.emit("INITIALIZED")
            
            // Send initial device info to phone even if no BPM yet
            scope.launch {
                sendDeviceInfo()
            }
        } catch (e: Exception) {
            FoxWearLogger.e("Error initializing selected backend, falling back to LEGACY", e)
            engine = LegacyEngine(context)
            engine?.initialize()
            _status.emit("INITIALIZED")
        }
        
        // Bridge samples to communication manager and internal flow
        scope.launch {
            FoxWearLogger.i("FOX-WEAR | Démarrage de la boucle de pontage des BPM")
            engine?.samples?.collect { sample ->
                FoxWearLogger.d("FOX-WEAR | Pontage BPM: ${sample.bpm} vers UI et Communication")
                _samples.emit(sample)
                communicationManager.sendSample(sample)
                // Also send device info occasionally
            }
        }
    }

    suspend fun startMonitoring() {
        FoxWearLogger.i("Starting monitoring")
        engine?.start()
        _isMonitoring.emit(true)
        _status.emit("RUNNING")
    }

    suspend fun stopMonitoring() {
        FoxWearLogger.i("Stopping monitoring")
        engine?.stop()
        _isMonitoring.emit(false)
        _status.emit("READY")
    }

    /**
     * Manually bridge a sample from passive background services.
     */
    suspend fun sendPassiveSample(sample: WearHeartRateSample) {
        _samples.emit(sample)
        communicationManager.sendSample(sample)
        sendDeviceInfo()
    }

    fun notifyForegroundServiceActive(isActive: Boolean) {
        _isForegroundServiceActive.value = isActive
        if (isActive) {
            FoxWearLogger.i("Foreground confirmed")
        }
    }

    suspend fun shutdown() {
        engine?.shutdown()
        _status.emit("OFF")
    }

    fun getEngineDiagnostic(): String = engine?.getDiagnosticInfo() ?: "Engine NULL"
}
