package com.projectfox.foxoff.brain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.LocalTime

/**
 * The core intelligence of FoxOFF.
 * Orchestrates multi-source event analysis and maintains global state.
 *
 * `config` DOIT être la même instance que celle passée à l'analyseur (voir
 * FoxCore) : le suivi de `bpmBelowBaselineSince`/`lastBpmDropBonusAt`
 * ci-dessous doit utiliser exactement le même seuil que WeightedSleepAnalyzer
 * pour rester cohérent — deux copies de config qui divergent casseraient la
 * garde anti-faux-positif.
 */
class FoxBrain(
    private val analyzer: FoxBrainAnalyzer,
    private val config: SleepScoringConfig = SleepScoringConfig()
) {

    private val _state = MutableStateFlow(FoxBrainState())
    val state: StateFlow<FoxBrainState> = _state.asStateFlow()

    /**
     * Remplace la référence BPM de repos générique par défaut (voir
     * FoxBrainState.restingBpmBaseline) par une valeur calibrée depuis les
     * vraies données de l'utilisateur (voir HealthConnectBaselineProvider,
     * appelé depuis FoxCore). Sans effet sur minBpmToday une fois établi —
     * cette valeur ne sert qu'au "cold start" (voir WeightedSleepAnalyzer).
     */
    fun setRestingBpmBaseline(bpm: Int) {
        _state.update { it.copy(restingBpmBaseline = bpm) }
    }

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

                    // Même calcul de seuil que WeightedSleepAnalyzer (même
                    // config partagée) : suit depuis quand le BPM est
                    // continûment sous le seuil, et si le bonus a déjà été
                    // accordé pour cet épisode — voir SleepScoringConfig
                    // .sustainedBpmDropDuration.
                    val baseline = if (baseState.minBpmToday > 0) {
                        baseState.minBpmToday.toFloat()
                    } else {
                        baseState.restingBpmBaseline.toFloat()
                    }
                    val belowThreshold = event.bpm < baseline * (1 + config.bpmDropThreshold)
                    val since = baseState.bpmBelowBaselineSince
                    // Même dérivation que WeightedSleepAnalyzer (voir sa
                    // documentation) : octroi PÉRIODIQUE tant que le BPM
                    // reste bas, pas une seule fois pour tout l'épisode.
                    val reference = baseState.lastBpmDropBonusAt ?: since
                    val due = belowThreshold && reference != null &&
                            !Duration.between(reference, event.timestamp).isNegative &&
                            Duration.between(reference, event.timestamp) >= config.sustainedBpmDropDuration

                    baseState.copy(
                        // Un BPM reçu EST une preuve applicative de présence
                        // (voir WatchPresenceCoordinator) : il doit à lui seul
                        // ramener l'état à "Connectée", sans attendre un
                        // watch_info séparé. Le nom mémorisé n'est pas touché
                        // ici (cet événement ne le porte pas).
                        watchConnected = true,
                        currentBpm = bpm,
                        lastBpmTime = LocalTime.now(),
                        minBpmToday = if (baseState.minBpmToday == 0) bpm else minOf(baseState.minBpmToday, bpm),
                        maxBpmToday = maxOf(baseState.maxBpmToday, bpm),
                        bpmBelowBaselineSince = when {
                            !belowThreshold -> null
                            since == null -> event.timestamp
                            else -> since
                        },
                        lastBpmDropBonusAt = when {
                            !belowThreshold -> null
                            due -> event.timestamp
                            else -> baseState.lastBpmDropBonusAt
                        }
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
                is FoxBrainEvent.SleepDetected -> baseState.copy(
                    tvIsPaused = true
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
