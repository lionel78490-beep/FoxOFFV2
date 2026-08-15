package com.projectfox.foxoff.brain.simulation

import com.projectfox.foxoff.brain.FoxBrain
import com.projectfox.foxoff.brain.FoxBrainEvent
import com.projectfox.foxoff.brain.SleepScoringConfig
import com.projectfox.foxoff.brain.SleepState
import com.projectfox.foxoff.brain.WeightedSleepAnalyzer
import com.projectfox.foxoff.core.application.FoxCore
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import kotlin.math.sin
import kotlin.random.Random

/** Résultat d'une nuit simulée — voir NightSimulationTest pour l'usage. */
data class NightSimulationResult(
    val profile: NightProfile,
    val reachedAsleepAt: Instant?,
    val pauseTriggeredAt: Instant?,
    val maxProbability: Float,
    val endState: SleepState,
    val passed: Boolean,
    val notes: String
) {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

    fun summaryLine(): String {
        val icon = if (passed) "OK" else "!!"
        val asleep = reachedAsleepAt?.let { timeFmt.format(it) } ?: "jamais"
        val pause = pauseTriggeredAt?.let { timeFmt.format(it) } ?: "jamais"
        return "[$icon] ${profile.name.padEnd(45)} " +
            "endormi=$asleep pause=$pause maxScore=${"%.0f".format(maxProbability * 100)}% " +
            "état_final=$endState $notes"
    }
}

/**
 * Rejoue un [NightProfile] à travers le VRAI moteur de production
 * (`FoxBrain` + `WeightedSleepAnalyzer`, mêmes classes qu'en production —
 * pas de doublure) et la vraie décision de pause
 * (`FoxCore.shouldSendAutoPause`), un pas de temps toutes les minutes
 * simulées de 23h00 à 07h00 (480 pas). N'émet que des FoxBrainEvent
 * réellement dispatchés en production (HeartRateReceived, MovementDetected,
 * TVTurnedOn, WatchConnected/Disconnected, SleepDetected) — voir
 * FoxCore.startOrchestration() — pas les événements du sealed class jamais
 * câblés (TimeChanged, StillnessDetected...), pour rester fidèle au
 * comportement réel de l'app plutôt que de tester un chemin hypothétique.
 */
class NightSimulator {

    private companion object {
        const val NIGHT_MINUTES = 480
        const val ONSET_DURATION = 10f
        const val NIGHT_WAKING_DURATION = 15
        const val MORNING_WAKE_DURATION = 60f
        const val SIGNIFICANT_MOVEMENT = 4.5f
        const val ISOLATED_MOVEMENT_SPIKE = 4.5f
        const val RESTLESS_MOVEMENT_MAX = 1.2f
        const val FALSE_DIP_DURATION = 20
        val NIGHT_START: Instant = Instant.parse("2026-01-01T23:00:00Z")
    }

    fun run(profile: NightProfile, config: SleepScoringConfig = SleepScoringConfig()): NightSimulationResult {
        val brain = FoxBrain(WeightedSleepAnalyzer(config), config)
        profile.restingBpmBaseline?.let { brain.setRestingBpmBaseline(it) }
        val rng = Random(profile.hashCode())

        var reachedAsleepAt: Instant? = null
        var pauseTriggeredAt: Instant? = null
        var maxProbability = 0f

        if (profile.tvOn) {
            dispatch(brain, FoxBrainEvent.TVTurnedOn, NIGHT_START)
        }

        var watchIsOut = false
        var tvTurnedOffDispatched = false

        for (minute in 0 until NIGHT_MINUTES) {
            val instant = NIGHT_START.plusSeconds(minute * 60L)
            val inDropout = profile.watchDropoutRange?.contains(minute) == true

            if (inDropout && !watchIsOut) {
                watchIsOut = true
                dispatch(brain, FoxBrainEvent.WatchDisconnected, instant)
            } else if (!inDropout && watchIsOut) {
                watchIsOut = false
                dispatch(brain, FoxBrainEvent.WatchConnected("Montre simulée"), instant)
            }

            if (!tvTurnedOffDispatched && profile.tvOn && profile.tvOffMinute == minute) {
                tvTurnedOffDispatched = true
                dispatch(brain, FoxBrainEvent.TVTurnedOff, instant)
            }

            if (!inDropout) {
                val bpm = bpmAt(profile, minute, rng)
                dispatch(brain, FoxBrainEvent.HeartRateReceived(bpm, "SIM"), instant)
            }

            movementAt(profile, minute, rng)?.let { magnitude ->
                dispatch(brain, FoxBrainEvent.MovementDetected(magnitude), instant)
            }

            val state = brain.state.value
            maxProbability = maxOf(maxProbability, state.lastScore.sleepProbability)

            if (reachedAsleepAt == null && state.detectedSleepState == SleepState.ASLEEP) {
                reachedAsleepAt = instant
            }

            // Décision réelle de FoxCore, pas une réimplémentation — voir
            // FoxCoreAutoPauseGateTest pour la même approche. `tvIsPaused`
            // ne repasse jamais à false (voir FoxForegroundService) : une
            // fois déclenché, SleepDetected verrouille l'état pour le reste
            // de la nuit simulée, exactement comme en production
            // (executePause() dans SleepPauseCoordinator).
            if (pauseTriggeredAt == null && FoxCore.shouldSendAutoPause(state, config)) {
                pauseTriggeredAt = instant
                dispatch(brain, FoxBrainEvent.SleepDetected, instant)
            }
        }

        val finalState = brain.state.value
        val passed = evaluate(profile, reachedAsleepAt, pauseTriggeredAt, finalState.detectedSleepState)
        val notes = buildNotes(profile, reachedAsleepAt, pauseTriggeredAt, finalState.detectedSleepState)

        return NightSimulationResult(
            profile = profile,
            reachedAsleepAt = reachedAsleepAt,
            pauseTriggeredAt = pauseTriggeredAt,
            maxProbability = maxProbability,
            endState = finalState.detectedSleepState,
            passed = passed,
            notes = notes
        )
    }

    private fun dispatch(brain: FoxBrain, event: FoxBrainEvent, instant: Instant) {
        event.timestamp = instant
        brain.onEvent(event)
    }

    private fun evaluate(
        profile: NightProfile,
        reachedAsleepAt: Instant?,
        pauseTriggeredAt: Instant?,
        endState: SleepState
    ): Boolean {
        if (profile.expectNeverAsleep) {
            return reachedAsleepAt == null && pauseTriggeredAt == null
        }
        var ok = true
        if (profile.expectAsleep) ok = ok && reachedAsleepAt != null
        if (profile.expectAutoPause) ok = ok && pauseTriggeredAt != null
        if (profile.expectEndsBelowAsleep) ok = ok && endState != SleepState.ASLEEP
        return ok
    }

    private fun buildNotes(
        profile: NightProfile,
        reachedAsleepAt: Instant?,
        pauseTriggeredAt: Instant?,
        endState: SleepState
    ): String {
        val problems = mutableListOf<String>()
        if (profile.expectNeverAsleep && (reachedAsleepAt != null || pauseTriggeredAt != null)) {
            problems += "faux positif : endormissement/pause détecté alors qu'attendu jamais"
        }
        if (profile.expectAsleep && reachedAsleepAt == null) {
            problems += "jamais atteint ASLEEP alors qu'attendu"
        }
        if (profile.expectAutoPause && pauseTriggeredAt == null) {
            problems += "pause TV jamais déclenchée alors qu'attendue"
        }
        if (profile.expectEndsBelowAsleep && endState == SleepState.ASLEEP) {
            problems += "score encore ASLEEP en fin de nuit alors qu'un réveil matinal était simulé"
        }
        return if (problems.isEmpty()) "" else "-> " + problems.joinToString("; ")
    }

    // --- Génération du BPM/mouvement synthétiques ---

    private fun bpmAt(profile: NightProfile, minute: Int, rng: Random): Float {
        val noise = rng.nextInt(-profile.bpmNoise, profile.bpmNoise + 1).toFloat()

        if (profile.neverSleeps) {
            return profile.awakeBpm + noise
        }

        val waking = profile.nightWakingMinutes.firstOrNull { minute in it until (it + NIGHT_WAKING_DURATION * 3) }
        if (waking != null) {
            val progress = ((minute - waking).toFloat() / (NIGHT_WAKING_DURATION * 3)).coerceIn(0f, 1f)
            // Remonte vite (réveil brusque), redescend progressivement.
            val shape = if (progress < 0.2f) progress / 0.2f else 1f - (progress - 0.2f) / 0.8f
            return profile.asleepBpm + (profile.awakeBpm - profile.asleepBpm) * shape.coerceIn(0f, 1f) + noise
        }

        val morningWake = profile.morningWakeMinute
        if (morningWake != null && minute >= morningWake) {
            val progress = ((minute - morningWake).toFloat() / MORNING_WAKE_DURATION).coerceIn(0f, 1f)
            return profile.asleepBpm + (profile.awakeBpm - profile.asleepBpm) * progress + noise
        }

        val falseDip = profile.falseDipMinute
        if (falseDip != null && minute >= falseDip && minute < falseDip + FALSE_DIP_DURATION && minute < profile.sleepOnsetMinute) {
            // Descend vers asleepBpm puis remonte (triangle) — un instant qui
            // ressemble à du sommeil, sans vrai endormissement.
            val progress = (minute - falseDip).toFloat() / FALSE_DIP_DURATION
            val shape = (1f - kotlin.math.abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)
            return profile.awakeBpm - (profile.awakeBpm - profile.asleepBpm) * shape + noise
        }

        return if (minute < profile.sleepOnsetMinute) {
            profile.awakeBpm + noise
        } else {
            val elapsed = (minute - profile.sleepOnsetMinute).toFloat()
            var progress = (elapsed / ONSET_DURATION).coerceIn(0f, 1f)
            var target = profile.awakeBpm - (profile.awakeBpm - profile.asleepBpm) * progress
            if (profile.hesitant && elapsed < 60f) {
                // Oscillations supplémentaires pendant l'endormissement
                // hésitant, amorties au fil du temps.
                target += sin(elapsed * 0.3).toFloat() * 6f * (1f - elapsed / 60f)
            }
            target + noise
        }
    }

    private fun movementAt(profile: NightProfile, minute: Int, rng: Random): Float? {
        if (profile.nightWakingMinutes.contains(minute)) return SIGNIFICANT_MOVEMENT
        if (profile.isolatedMovementMinutes.contains(minute)) return ISOLATED_MOVEMENT_SPIKE
        if (profile.restlessButLowBpm && minute % 4 == 0) {
            return rng.nextFloat() * RESTLESS_MOVEMENT_MAX
        }
        return null
    }
}
