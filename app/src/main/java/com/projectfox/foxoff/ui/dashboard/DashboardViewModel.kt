package com.projectfox.foxoff.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectfox.foxoff.brain.SleepState
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.diagnostics.FoxDiagnostic
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        FoxDiagnostic.dumpCommunication()
        startRealUpdates()
    }

    private fun startRealUpdates() {
        viewModelScope.launch {
            // SINGLE SOURCE OF TRUTH: FoxBrain.state
            FoxCore.brain.state.collectLatest { brainState ->
                _uiState.update { 
                    it.copy(
                        // Global
                        mainStatus = if (!brainState.watchConnected) "Recherche montre..." 
                                     else getMainStatusLabel(brainState.detectedSleepState),
                        mainStatusColor = if (!brainState.watchConnected) Color.Yellow 
                                          else getStatusColor(brainState.detectedSleepState),

                        // Watch
                        watchConnected = brainState.watchConnected,
                        watchName = brainState.watchName,
                        watchBattery = if (brainState.watchBattery > 0) "${brainState.watchBattery}%" else "Connecté",
                        
                        // TV
                        tvConnected = brainState.tvConnected,
                        tvName = brainState.tvName,
                        tvCurrentApp = brainState.tvCurrentApp,
                        tvLastCommand = brainState.tvLastCommand,
                        tvLastCommandTime = brainState.tvLastCommandTime,
                        
                        // HR
                        currentBpm = brainState.currentBpm,
                        lastBpmTime = brainState.lastBpmTime,
                        minBpmToday = brainState.minBpmToday,
                        maxBpmToday = brainState.maxBpmToday,
                        
                        // Analysis
                        sleepState = brainState.detectedSleepState,
                        confidence = (brainState.lastScore.confidence * 100).toInt(),
                        analysisExplanation = brainState.lastScore.reason
                    )
                }
            }
        }
    }

    fun onAssociateClick(context: android.content.Context) {
        viewModelScope.launch {
            FoxCore.discoverWatch(context)
        }
    }

    private fun getMainStatusLabel(state: SleepState): String {
        return when (state) {
            SleepState.AWAKE -> "Utilisateur éveillé"
            SleepState.DROWSY -> "Fatigue légère"
            SleepState.PRE_SLEEP -> "Pré-endormissement"
            SleepState.ASLEEP -> "Sommeil probable"
        }
    }

    private fun getStatusColor(state: SleepState): Color {
        return when (state) {
            SleepState.AWAKE -> Color.Green
            SleepState.DROWSY -> Color.Cyan
            SleepState.PRE_SLEEP -> Color.Yellow
            SleepState.ASLEEP -> Color(0xFFD500F9) // FoxElectricViolet
        }
    }
}
