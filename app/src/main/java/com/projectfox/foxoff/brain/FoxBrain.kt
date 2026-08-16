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
                    // config partagée, voir sa documentation pour le détail
                    // du plancher glissant minOf) : suit depuis quand le BPM
                    // est continûment sous le seuil, et si le bonus a déjà
                    // été accordé pour cet épisode — voir SleepScoringConfig
                    // .sustainedBpmDropDuration.
                    val rollingMinBpm = baseState.bpmHistory.minOfOrNull { it.second }
                    val baseline = if (rollingMinBpm != null) {
                        minOf(rollingMinBpm.toFloat(), baseState.restingBpmBaseline.toFloat())
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

                    val (newMinBpmToday, newPendingLowBpm) = nextMinBpmToday(
                        currentMin = baseState.minBpmToday,
                        pendingLow = baseState.pendingLowBpm,
                        bpm = bpm,
                        debounce = config.debounceMinBpmFloor,
                        toleranceBpm = config.minBpmConfirmationToleranceBpm
                    )

                    // Plancher glissant (2026-08-16, voir doc de
                    // FoxBrainState.bpmHistory) : ajoute cette lecture puis
                    // purge celles sorties de la fenêtre
                    // rollingBaselineWindowMinutes — la liste reste donc
                    // bornée naturellement, pas de croissance illimitée sur
                    // une longue nuit.
                    val newBpmHistory = (baseState.bpmHistory + (event.timestamp to bpm))
                        .filter { Duration.between(it.first, event.timestamp).toMinutes() <= config.rollingBaselineWindowMinutes }

                    baseState.copy(
                        // Un BPM reçu EST une preuve applicative de présence
                        // (voir WatchPresenceCoordinator) : il doit à lui seul
                        // ramener l'état à "Connectée", sans attendre un
                        // watch_info séparé. Le nom mémorisé n'est pas touché
                        // ici (cet événement ne le porte pas).
                        watchConnected = true,
                        currentBpm = bpm,
                        lastBpmTime = LocalTime.now(),
                        minBpmToday = newMinBpmToday,
                        pendingLowBpm = newPendingLowBpm,
                        bpmHistory = newBpmHistory,
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

    /**
     * Calcule le prochain `minBpmToday`/`pendingLowBpm` pour une lecture BPM
     * donnée. Comportement par défaut (`debounce = false`, historique,
     * inchangé) : `minBpmToday` se resserre immédiatement dès la moindre
     * lecture plus basse, `pendingLowBpm` reste toujours `null`. Comportement
     * "debounced" (`debounce = true`, voir SleepScoringConfig.debounceMinBpmFloor) :
     * une lecture plus basse que le minimum courant devient seulement un
     * CANDIDAT (`pendingLowBpm`) tant qu'elle n'est pas confirmée par une
     * lecture suivante proche (`toleranceBpm`) — une seule lecture isolée et
     * basse (bruit capteur ou micro-creux ponctuel) ne resserre donc plus le
     * seuil pour tout le reste de la nuit à elle seule.
     */
    private fun nextMinBpmToday(
        currentMin: Int, pendingLow: Int?, bpm: Int, debounce: Boolean, toleranceBpm: Int
    ): Pair<Int, Int?> {
        if (currentMin == 0) return bpm to null // toute première lecture de la session : commit immédiat
        if (!debounce) return minOf(currentMin, bpm) to null
        if (bpm >= currentMin) return currentMin to null // pas un nouveau creux : rien à confirmer
        // bpm < currentMin : nouveau candidat plus bas.
        return if (pendingLow != null && bpm <= pendingLow + toleranceBpm) {
            minOf(bpm, pendingLow) to null // confirmé par une lecture proche : on committe
        } else {
            currentMin to bpm // pas encore confirmé : on attend la prochaine lecture
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
