package com.projectfox.foxoff.brain.simulation

import com.projectfox.foxoff.brain.FoxBrain
import com.projectfox.foxoff.brain.FoxBrainEvent
import com.projectfox.foxoff.brain.SleepScoringConfig
import com.projectfox.foxoff.brain.SleepState
import com.projectfox.foxoff.brain.WeightedSleepAnalyzer
import com.projectfox.foxoff.core.application.FoxCore
import java.time.Instant
import kotlin.random.Random

/** Un pas de temps (une minute simulée) — toute l'observabilité nécessaire au diagnostic. */
data class MinuteTrace(
    val minute: Int,
    val bpm: Float?,
    val movement: Float?,
    val sleepProbability: Float,
    val confidence: Float,
    val state: SleepState,
    val reason: String
)

data class NightTrace(
    val profile: NightProfile,
    val minutes: List<MinuteTrace>,
    val reachedAsleepAt: Instant?,
    val pauseTriggeredAt: Instant?,
    val maxProbability: Float,
    val endState: SleepState
)

/**
 * Rejoue une nuit EXACTEMENT comme `NightSimulator.run()` (mêmes appels,
 * même ordre d'événements, réutilise directement `bpmAt`/`movementAt` de
 * NightSimulator plutôt que de les dupliquer — garantit que le diagnostic
 * correspond bit à bit aux chiffres déjà rapportés), mais capture en plus
 * une trace minute par minute (score, confiance, état, BPM, mouvement,
 * raison) pour l'analyse diagnostique du 2026-08-16. N'existait pas avant
 * — NightSimulator lui-même n'est pas modifié dans son comportement
 * (seule sa visibilité a changé, voir son fichier).
 *
 * Outil d'OBSERVATION uniquement : ne modifie ni n'évalue de configuration
 * différente de celle fournie, ne touche à aucun fichier de production.
 */
object NightDiagnostics {

    fun runWithTrace(profile: NightProfile, config: SleepScoringConfig): NightTrace {
        val brain = FoxBrain(WeightedSleepAnalyzer(config), config)
        profile.restingBpmBaseline?.let { brain.setRestingBpmBaseline(it) }
        val rng = Random(profile.hashCode())
        val sim = NightSimulator()

        var reachedAsleepAt: Instant? = null
        var pauseTriggeredAt: Instant? = null
        var maxProbability = 0f
        val minutes = mutableListOf<MinuteTrace>()

        fun dispatch(event: FoxBrainEvent, instant: Instant) {
            event.timestamp = instant
            brain.onEvent(event)
        }

        if (profile.tvOn) {
            dispatch(FoxBrainEvent.TVTurnedOn, NightSimulator.NIGHT_START)
        }

        var watchIsOut = false
        var tvTurnedOffDispatched = false

        for (minute in 0 until NightSimulator.NIGHT_MINUTES) {
            val instant = NightSimulator.NIGHT_START.plusSeconds(minute * 60L)
            val inDropout = profile.watchDropoutRange?.contains(minute) == true

            if (inDropout && !watchIsOut) {
                watchIsOut = true
                dispatch(FoxBrainEvent.WatchDisconnected, instant)
            } else if (!inDropout && watchIsOut) {
                watchIsOut = false
                dispatch(FoxBrainEvent.WatchConnected("Montre simulée"), instant)
            }

            if (!tvTurnedOffDispatched && profile.tvOn && profile.tvOffMinute == minute) {
                tvTurnedOffDispatched = true
                dispatch(FoxBrainEvent.TVTurnedOff, instant)
            }

            var bpmThisMinute: Float? = null
            if (!inDropout) {
                bpmThisMinute = sim.bpmAt(profile, minute, rng)
                dispatch(FoxBrainEvent.HeartRateReceived(bpmThisMinute, "SIM"), instant)
            }

            val movement = sim.movementAt(profile, minute, rng)
            movement?.let { dispatch(FoxBrainEvent.MovementDetected(it), instant) }

            val state = brain.state.value
            maxProbability = maxOf(maxProbability, state.lastScore.sleepProbability)
            if (reachedAsleepAt == null && state.detectedSleepState == SleepState.ASLEEP) {
                reachedAsleepAt = instant
            }
            if (pauseTriggeredAt == null && FoxCore.shouldSendAutoPause(state, config)) {
                pauseTriggeredAt = instant
                dispatch(FoxBrainEvent.SleepDetected, instant)
            }

            minutes += MinuteTrace(
                minute = minute,
                bpm = bpmThisMinute,
                movement = movement,
                sleepProbability = state.lastScore.sleepProbability,
                confidence = state.lastScore.confidence,
                state = state.detectedSleepState,
                reason = state.lastScore.reason
            )
        }

        return NightTrace(
            profile = profile,
            minutes = minutes,
            reachedAsleepAt = reachedAsleepAt,
            pauseTriggeredAt = pauseTriggeredAt,
            maxProbability = maxProbability,
            endState = brain.state.value.detectedSleepState
        )
    }

    /** N'imprime que les minutes où la raison OU la catégorie d'état change — une nuit complète tient sur quelques lignes. */
    fun printKeyTransitions(trace: NightTrace) {
        var lastReason: String? = null
        var lastState: SleepState? = null
        trace.minutes.forEach { m ->
            if (m.reason != lastReason || m.state != lastState) {
                println(
                    "    [min ${m.minute}] score=${"%.0f".format(m.sleepProbability * 100)}% état=${m.state} " +
                        "bpm=${m.bpm?.let { "%.0f".format(it) } ?: "—"} mvt=${m.movement?.let { "%.2f".format(it) } ?: "—"} " +
                        "raison=\"${m.reason}\""
                )
                lastReason = m.reason
                lastState = m.state
            }
        }
    }
}
