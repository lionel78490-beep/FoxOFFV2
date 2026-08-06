package com.projectfox.foxoff.brain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime

/**
 * The core intelligence of FoxOFF.
 * Orchestrates multi-source event analysis and maintains global state.
 */
class FoxBrain(private val analyzer: FoxBrainAnalyzer) {

    private val _state = MutableStateFlow(FoxBrainState())
    val state: StateFlow<FoxBrainState> = _state.asStateFlow()

    /**
     * Primary entry point for incoming data.
     */
    fun onEvent(event: FoxBrainEvent) {
        val currentScore = analyzer.analyze(event, _state.value)
        
        _state.update { currentState ->
            val baseState = currentState.copy(
                lastScore = currentScore,
                lastEventTime = event.timestamp,
                detectedSleepState = determineSleepState(currentScore)
            )

            // Dynamic mapping of event data to global state
            when (event) {
                is FoxBrainEvent.HeartRateReceived -> {
                    val bpm = event.bpm.toInt()
                    baseState.copy(
                        currentBpm = bpm,
                        lastBpmTime = LocalTime.now(),
                        minBpmToday = if (baseState.minBpmToday == 0) bpm else minOf(baseState.minBpmToday, bpm),
                        maxBpmToday = maxOf(baseState.maxBpmToday, bpm)
                    )
                }
                is FoxBrainEvent.WatchConnected -> baseState.copy(
                    watchConnected = true,
                    watchName = event.name
                )
                is FoxBrainEvent.WatchDisconnected -> baseState.copy(
                    watchConnected = false
                )
                is FoxBrainEvent.BatteryChanged -> baseState.copy(
                    watchBattery = event.level,
                    watchIsCharging = event.isCharging
                )
                is FoxBrainEvent.TVTurnedOn -> baseState.copy(
                    tvConnected = true
                )
                is FoxBrainEvent.TVTurnedOff -> baseState.copy(
                    tvConnected = false
                )
                is FoxBrainEvent.TvAppChanged -> baseState.copy(
                    tvCurrentApp = event.appName ?: "Aucune"
                )
                is FoxBrainEvent.TvCommandSent -> baseState.copy(
                    tvLastCommand = event.command,
                    tvLastCommandTime = LocalTime.now()
                )
                is FoxBrainEvent.MovementDetected -> baseState.copy(
                    movementMagnitude = event.magnitude
                )
                is FoxBrainEvent.MonitoringStarted -> baseState.copy(
                    isMonitoring = true
                )
                is FoxBrainEvent.MonitoringStopped -> baseState.copy(
                    isMonitoring = false
                )
                else -> baseState
            }
        }
    }

    private fun determineSleepState(score: FoxBrainScore): SleepState {
        val prob = score.sleepProbability
        return when {
            prob >= 0.90f -> SleepState.ASLEEP
            prob >= 0.70f -> SleepState.PRE_SLEEP
            prob >= 0.40f -> SleepState.DROWSY
            else -> SleepState.AWAKE
        }
    }
}
