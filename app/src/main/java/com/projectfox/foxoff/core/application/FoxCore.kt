package com.projectfox.foxoff.core.application

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.projectfox.foxoff.automation.RealTvController
import com.projectfox.foxoff.automation.TvController
import com.projectfox.foxoff.brain.*
import com.projectfox.foxoff.core.events.FoxEventBus
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.core.module.FoxModule
import com.projectfox.foxoff.sensors.events.SensorEvent
import android.content.Context
import com.projectfox.foxoff.core.diagnostics.FoxDiagnostic
import com.projectfox.foxoff.tv.FoxTvEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FoxCore {
    val eventBus = FoxEventBus()
    private val modules = mutableListOf<FoxModule>()
    private val coreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val brain = FoxBrain(WeightedSleepAnalyzer())
    
    private var _tvEngine: FoxTvEngine? = null
    val tvEngine: FoxTvEngine? get() = _tvEngine
    
    private var _tvController: TvController? = null
    val tvController: TvController? get() = _tvController

    // Real-time Watch State
    private val _watchInfo = MutableStateFlow<SensorEvent.WatchInfoReceived?>(null)
    val watchInfo: StateFlow<SensorEvent.WatchInfoReceived?> = _watchInfo.asStateFlow()

    fun registerModule(module: FoxModule) {
        modules.add(module)
    }

    suspend fun initialize(context: Context) {
        FoxDiagnostic.dumpCommunication()
        
        // Initialize TV Engine but DON'T start discovery yet
        _tvEngine = FoxTvEngine(context)
        _tvController = RealTvController(_tvEngine!!)
        
        modules.forEach { it.initialize() }
        startOrchestration()
        
        // Removed discoverWatch(context) and _tvEngine?.initialize() from here
        // They will be triggered in the Onboarding screens after permissions.
    }

    private fun startOrchestration() {
        coreScope.launch {
            // All sensor/external events are funneled through FoxBrain
            launch {
                eventBus.subscribeAs<SensorEvent.HeartRateReceived>().collectLatest { event ->
                    FoxLogger.i("FOX-CORE | Orchestrator | BPM reçu -> Brain")
                    brain.onEvent(FoxBrainEvent.HeartRateReceived(event.sample.bpm, "WEAR"))
                }
            }
            
            launch {
                eventBus.subscribeAs<SensorEvent.WatchInfoReceived>().collectLatest { info ->
                    FoxLogger.i("FOX-CORE | Orchestrator | WatchInfo reçu -> Brain")
                    brain.onEvent(FoxBrainEvent.WatchConnected(info.name))
                    brain.onEvent(FoxBrainEvent.BatteryChanged(info.battery, false))
                }
            }

            // Observe TV Engine State
            launch {
                tvEngine?.state?.collectLatest { tvDevice ->
                    if (tvDevice != null) {
                        if (tvDevice.status == com.projectfox.foxoff.tv.TvConnectionStatus.CONNECTED) {
                            brain.onEvent(FoxBrainEvent.TVTurnedOn)
                        } else {
                            brain.onEvent(FoxBrainEvent.TVTurnedOff)
                        }
                        brain.onEvent(FoxBrainEvent.TvAppChanged(tvDevice.currentApp))
                    }
                }
            }

            // Brain Decision Loop
            launch {
                brain.state.collectLatest { brainState ->
                    val score = brainState.lastScore.sleepProbability
                    
                    // 1. Rule: Score > 90% -> Pause TV
                    if (brainState.detectedSleepState == SleepState.ASLEEP && brainState.lastScore.confidence > 0.8f) {
                        if (!brainState.tvIsPaused) {
                            FoxLogger.i("FOX-CORE | Brain decision: User ASLEEP. Attempting to pause TV...")
                            tvController?.pause()
                            brain.onEvent(FoxBrainEvent.SleepDetected)
                            brain.onEvent(FoxBrainEvent.TvCommandSent("Pause Automatique", java.time.Instant.now()))
                        }
                    }
                    
                    // 2. Rule: Score > 70% -> Start High Precision Monitoring
                    if (score > 0.70f && !brainState.isMonitoring) {
                        FoxLogger.i("FOX-CORE | Pré-endormissement (70%+) : Demande de mode Haute Précision")
                        sendToWatch("/foxoff/start_high_precision", byteArrayOf())
                    }
                }
            }
        }
    }

    private fun sendToWatch(path: String, payload: ByteArray) {
        coreScope.launch(Dispatchers.IO) {
            try {
                FoxLogger.i("FOX-CORE | Commande envoyée à la montre [$path]")
            } catch (e: Exception) {
                FoxLogger.e("FOX-CORE | Erreur envoi commande montre", e)
            }
        }
    }

    suspend fun discoverWatch(context: Context) = withContext(Dispatchers.IO) {
        try {
            FoxLogger.i("FOX-WATCH | discoverWatch() started")
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = Tasks.await(nodeClient.connectedNodes)
            
            if (nodes.isNotEmpty()) {
                val primaryNode = nodes.first()
                FoxLogger.i("FOX-WATCH | Node found : ${primaryNode.displayName}")
                val info = SensorEvent.WatchInfoReceived(primaryNode.displayName, 0, true)
                _watchInfo.value = info
                eventBus.publish(info)
                FoxLogger.i("FOX-WATCH | watchInfo updated")
            } else {
                FoxLogger.w("FOX-WATCH | Aucune montre connectée via GMS.")
                brain.onEvent(FoxBrainEvent.WatchDisconnected)
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-WATCH | Erreur lors de la découverte", e)
        }
    }

    suspend fun startModules() {
        modules.forEach { it.start() }
    }

    suspend fun shutdown() {
        modules.forEach { it.shutdown() }
    }
}
