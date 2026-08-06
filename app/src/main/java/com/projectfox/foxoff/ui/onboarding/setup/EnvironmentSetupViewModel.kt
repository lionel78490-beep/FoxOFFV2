package com.projectfox.foxoff.ui.onboarding.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EnvironmentSetupViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        EnvironmentSetupState(
            watchStep = SetupStepInfo("watch", "⌚", "Montre", "Recherche...", SetupStepStatus.IN_PROGRESS),
            tvStep = SetupStepInfo("tv", "📺", "Télévision", "Recherche...", SetupStepStatus.IN_PROGRESS),
            sensorsStep = SetupStepInfo("sensors", "❤️", "Capteurs", "Initialisation...", SetupStepStatus.IN_PROGRESS),
            brainStep = SetupStepInfo("brain", "🧠", "Fox Brain", "Chargement...", SetupStepStatus.IN_PROGRESS)
        )
    )
    val state: StateFlow<EnvironmentSetupState> = _state.asStateFlow()

    init {
        startRealMonitoring()
    }

    private fun startRealMonitoring() {
        viewModelScope.launch {
            // Monitor Watch
            launch {
                com.projectfox.foxoff.core.application.FoxCore.watchInfo.collect { info ->
                    if (info != null && info.isConnected) {
                        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-SETUP | Event: Watch Connected (${info.name})")
                        updateStep("watch", SetupStepStatus.COMPLETED, info.name, 1f)
                    }
                }
            }

            // Monitor TV
            launch {
                com.projectfox.foxoff.core.application.FoxCore.tvEngine?.state?.collect { tv ->
                    if (tv != null && tv.status == com.projectfox.foxoff.tv.TvConnectionStatus.CONNECTED) {
                        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-SETUP | Event: TV Connected (${tv.name})")
                        updateStep("tv", SetupStepStatus.COMPLETED, tv.name, 1f)
                    }
                }
            }

            // Monitor Brain
            launch {
                com.projectfox.foxoff.core.application.FoxCore.brain.state.collect { brain ->
                    if (brain.currentBpm != null) {
                        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-SETUP | Event: BPM Received (${brain.currentBpm})")
                        updateStep("sensors", SetupStepStatus.COMPLETED, "Capteurs opérationnels", 1f)
                    }
                    if (brain.isMonitoring) {
                        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-SETUP | Event: Brain Monitoring Active")
                        updateStep("brain", SetupStepStatus.COMPLETED, "Intelligence initialisée", 1f)
                    }
                }
            }
            
            // Check for overall completion
            _state.collect { s ->
                val watchOk = s.watchStep.status == SetupStepStatus.COMPLETED
                val sensorsOk = s.sensorsStep.status == SetupStepStatus.COMPLETED
                val minimalConditionsMet = watchOk && sensorsOk
                
                if (minimalConditionsMet && !s.isAllCompleted) {
                    com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-SETUP | Conditions minimales atteintes -> ouverture Dashboard")
                    _state.update { it.copy(isAllCompleted = true, overallProgress = 1f) }
                }
            }
        }
    }

    private fun updateStep(id: String, status: SetupStepStatus, message: String, progress: Float) {
        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-SETUP | updateStep($id, $status, $message, $progress)")
        _state.update { currentState ->
            when (id) {
                "watch" -> currentState.copy(watchStep = currentState.watchStep.copy(status = status, currentMessage = message, progress = progress))
                "tv" -> currentState.copy(tvStep = currentState.tvStep.copy(status = status, currentMessage = message, progress = progress))
                "sensors" -> currentState.copy(sensorsStep = currentState.sensorsStep.copy(status = status, currentMessage = message, progress = progress))
                "brain" -> currentState.copy(brainStep = currentState.brainStep.copy(status = status, currentMessage = message, progress = progress))
                else -> currentState
            }
        }
        updateOverallProgress()
    }

    private fun updateOverallProgress() {
        _state.update { s ->
            val completedCount = listOf(s.watchStep, s.tvStep, s.sensorsStep, s.brainStep).count { it.status == SetupStepStatus.COMPLETED }
            s.copy(overallProgress = completedCount / 4f)
        }
    }
}
