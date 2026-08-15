package com.projectfox.foxoff.brain.simulation

import com.projectfox.foxoff.brain.SleepScoringConfig
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Expérience "logique contextuelle" (2026-08-16, suite à l'expérience de
 * continuité BPM — aucune stratégie globale ne satisfaisait FP<=baseline
 * ET manqués<=baseline, tension structurelle H vs E identifiée). Moteur
 * PARALLÈLE, comme `ContinuityExperimentEngine` — ne touche jamais
 * FoxBrain/WeightedSleepAnalyzer/FoxCore réels.
 *
 * Signal de "contexte temporel" retenu : la minute écoulée depuis le
 * début de la nuit simulée (23h00) — c'est le SEUL signal réellement
 * disponible en production sans connaître la vérité terrain (l'heure
 * courante / la durée de surveillance écoulée), contrairement à
 * `groundTruthAsleepMinute` qui n'existe que dans la simulation.
 *
 * 4 mécanismes testés (voir [ContextualConfig]), combinables via leurs
 * paramètres :
 * - A (seuil progressif) : au-delà de `wakeThresholdMinutes` sans avoir
 *   jamais atteint DROWSY (40%), le seuil requis pour ASLEEP augmente
 *   progressivement au-delà de 90%.
 * - B (corroboration mouvement) : au-delà de `wakeThresholdMinutes`, le
 *   bonus BPM périodique n'est accordé que si aucun mouvement significatif
 *   n'a eu lieu dans les `stillnessWindowMinutes` précédentes.
 * - C (confirmation soutenue) : un franchissement du seuil ASLEEP survenu
 *   après `wakeThresholdMinutes` doit se maintenir en continu pendant
 *   `confirmationMinutesAfterWake` minutes avant de déclencher réellement
 *   la pause (sinon, le chrono de confirmation redémarre) — un
 *   franchissement avant ce délai déclenche immédiatement, comme
 *   aujourd'hui.
 * - E (tolérance contextuelle) : tolérance de 2 échantillons (la
 *   meilleure de l'expérience précédente pour H) activée UNIQUEMENT une
 *   fois qu'une vraie progression a déjà eu lieu (score déjà passé par
 *   DROWSY, 40%+) — désactivée pendant la phase initiale non prouvée.
 * "D" (profils D/D+H/D+F+H) n'est pas un mécanisme séparé mais une
 * vérification appliquée aux 4 ci-dessus (ces profils s'endorment vite,
 * donc restent presque toujours sous `wakeThresholdMinutes` et ne
 * devraient quasiment jamais activer les protections contextuelles).
 */
data class ContextualConfig(
    val label: String,
    val wakeThresholdMinutes: Int = 60,
    val progressiveThresholdRatePerMinute: Float = 0f,
    val progressiveThresholdMax: Float = 0f,
    val requireStillnessAfterWake: Boolean = false,
    val stillnessWindowMinutes: Int = 10,
    val confirmationMinutesAfterWake: Int = 0,
    val toleranceGatedByProgress: Boolean = false
)

object ContextualStrategies {
    val BASELINE = ContextualConfig("BASELINE (reset immédiat, seuil fixe 90%, production)")
    val A_SEUIL_PROGRESSIF = ContextualConfig(
        "A-SEUIL_PROGRESSIF",
        progressiveThresholdRatePerMinute = 0.0004f,
        progressiveThresholdMax = 0.08f
    )
    val B_CORROBORATION_MOUVEMENT = ContextualConfig(
        "B-CORROBORATION_MOUVEMENT",
        requireStillnessAfterWake = true,
        stillnessWindowMinutes = 10
    )
    val C_CONFIRMATION_SOUTENUE = ContextualConfig(
        "C-CONFIRMATION_SOUTENUE",
        confirmationMinutesAfterWake = 5
    )
    val E_TOLERANCE_CONTEXTUELLE = ContextualConfig(
        "E-TOLERANCE_CONTEXTUELLE",
        toleranceGatedByProgress = true
    )

    fun all() = listOf(BASELINE, A_SEUIL_PROGRESSIF, B_CORROBORATION_MOUVEMENT, C_CONFIRMATION_SOUTENUE, E_TOLERANCE_CONTEXTUELLE)
}

data class ContextualNightResult(
    val profile: NightProfile,
    val reachedAsleepAt: Instant?,
    val pauseTriggeredAt: Instant?,
    val maxProbability: Float
)

/** Une étape tracée — pour les études de cas détaillées demandées. */
data class ContextualMinuteTrace(
    val minute: Int,
    val bpm: Float?,
    val movement: Float?,
    val sleepProbability: Float,
    val effectiveThreshold: Float,
    val reason: String
)

data class ContextualNightTrace(val profile: NightProfile, val minutes: List<ContextualMinuteTrace>, val result: ContextualNightResult)

object ContextualDetectionEngine {

    private const val CONFIDENCE = 0.85f

    fun run(profile: NightProfile, config: SleepScoringConfig, strategy: ContextualConfig): ContextualNightResult {
        val trace = runInternal(profile, config, strategy, captureTrace = false)
        return trace.result
    }

    fun runWithTrace(profile: NightProfile, config: SleepScoringConfig, strategy: ContextualConfig): ContextualNightTrace =
        runInternal(profile, config, strategy, captureTrace = true)

    private fun runInternal(profile: NightProfile, config: SleepScoringConfig, strategy: ContextualConfig, captureTrace: Boolean): ContextualNightTrace {
        val sim = NightSimulator()
        val rng = Random(profile.hashCode())

        var currentProb = 0f
        var minBpmToday = 0
        val restingBpmBaseline = profile.restingBpmBaseline ?: 70

        var belowSince: Instant? = null
        var lastBonusAt: Instant? = null
        var consecutiveMisses = 0
        var everReachedDrowsy = false
        var lastSignificantMovementMinute: Int? = null

        var aboveThresholdSince: Instant? = null // pour la stratégie C

        var tvIsPaused = false
        var reachedAsleepAt: Instant? = null
        var pauseTriggeredAt: Instant? = null
        var maxProbability = 0f
        val traceList = if (captureTrace) mutableListOf<ContextualMinuteTrace>() else null

        if (profile.tvOn) currentProb = (currentProb + config.tvOnBonus).coerceIn(0f, 1f)

        var tvTurnedOffDispatched = false

        for (minute in 0 until NightSimulator.NIGHT_MINUTES) {
            val instant = NightSimulator.NIGHT_START.plusSeconds(minute * 60L)
            val inDropout = profile.watchDropoutRange?.contains(minute) == true
            var reason = ""
            var bpmThisMinute: Float? = null

            if (!tvTurnedOffDispatched && profile.tvOn && profile.tvOffMinute == minute) {
                tvTurnedOffDispatched = true
                currentProb = (currentProb * config.tvTurnedOffMultiplier).coerceIn(0f, 1f)
                reason = "TV éteinte"
            }

            val inLongWake = minute > strategy.wakeThresholdMinutes

            if (!inDropout) {
                bpmThisMinute = sim.bpmAt(profile, minute, rng)
                minBpmToday = if (minBpmToday == 0) bpmThisMinute.toInt() else minOf(minBpmToday, bpmThisMinute.toInt())
                val baseline = if (minBpmToday > 0) minOf(minBpmToday.toFloat(), restingBpmBaseline.toFloat()) else restingBpmBaseline.toFloat()
                val belowThreshold = bpmThisMinute < baseline * (1 + config.bpmDropThreshold)

                // Continuité : reset immédiat, SAUF stratégie E si déjà en progression avérée (tolérance 2 échantillons).
                val toleranceActive = strategy.toleranceGatedByProgress && everReachedDrowsy
                if (belowThreshold) {
                    belowSince = belowSince ?: instant
                    consecutiveMisses = 0
                } else if (toleranceActive && consecutiveMisses < 2) {
                    consecutiveMisses++
                } else {
                    belowSince = null
                    lastBonusAt = null
                    consecutiveMisses = 0
                }

                val reference = lastBonusAt ?: belowSince
                var due = belowThreshold && reference != null &&
                    !Duration.between(reference, instant).isNegative &&
                    Duration.between(reference, instant) >= config.sustainedBpmDropDuration

                // Stratégie B : au-delà du seuil d'éveil prolongé, exige l'absence de mouvement significatif récent.
                if (due && strategy.requireStillnessAfterWake && inLongWake) {
                    val sinceMovement = lastSignificantMovementMinute?.let { minute - it } ?: Int.MAX_VALUE
                    if (sinceMovement < strategy.stillnessWindowMinutes) {
                        due = false
                        reason = "Bonus refusé (mouvement récent, contexte éveil prolongé)"
                    }
                }

                if (belowThreshold && due) {
                    currentProb = (currentProb + config.bpmDropBonus).coerceIn(0f, 1f)
                    lastBonusAt = instant
                    reason = "Baisse soutenue de la fréquence cardiaque détectée"
                }
                if (currentProb >= 0.40f) everReachedDrowsy = true
            }

            sim.movementAt(profile, minute, rng)?.let { magnitude ->
                if (magnitude > config.movementThreshold) {
                    currentProb = (currentProb - config.significantMovementPenalty).coerceIn(0f, 1f)
                    lastSignificantMovementMinute = minute
                    reason = "Mouvement important détecté"
                }
            }

            // Stratégie A : seuil ASLEEP progressivement relevé si jamais de vraie progression après le seuil d'éveil.
            val extra = if (!everReachedDrowsy && inLongWake) {
                ((minute - strategy.wakeThresholdMinutes) * strategy.progressiveThresholdRatePerMinute).coerceAtMost(strategy.progressiveThresholdMax)
            } else 0f
            val effectiveThreshold = 0.90f + extra

            maxProbability = maxOf(maxProbability, currentProb)
            val crossedNow = currentProb >= effectiveThreshold

            if (crossedNow) {
                if (reachedAsleepAt == null) reachedAsleepAt = instant

                if (strategy.confirmationMinutesAfterWake > 0 && minute > strategy.wakeThresholdMinutes) {
                    // Stratégie C : exige un maintien continu avant de déclencher réellement.
                    aboveThresholdSince = aboveThresholdSince ?: instant
                    val heldFor = Duration.between(aboveThresholdSince, instant)
                    if (pauseTriggeredAt == null && !tvIsPaused && heldFor >= Duration.ofMinutes(strategy.confirmationMinutesAfterWake.toLong())) {
                        pauseTriggeredAt = instant
                        tvIsPaused = true
                        reason = "Pause déclenchée (confirmation soutenue atteinte)"
                    } else if (pauseTriggeredAt == null) {
                        reason = "Au-dessus du seuil, en attente de confirmation (${Duration.between(aboveThresholdSince, instant).toMinutes()}/${strategy.confirmationMinutesAfterWake}min)"
                    }
                } else if (pauseTriggeredAt == null && !tvIsPaused) {
                    pauseTriggeredAt = instant
                    tvIsPaused = true
                    reason = "Pause déclenchée"
                }
            } else {
                aboveThresholdSince = null // redescendu sous le seuil : le chrono de confirmation redémarre
            }

            traceList?.add(ContextualMinuteTrace(minute, bpmThisMinute, null, currentProb, effectiveThreshold, reason.ifEmpty { traceList.lastOrNull()?.reason ?: "" }))
        }

        val result = ContextualNightResult(profile, reachedAsleepAt, pauseTriggeredAt, maxProbability)
        return ContextualNightTrace(profile, traceList ?: emptyList(), result)
    }

    /** N'imprime que les minutes où la raison change — reprend le format de NightDiagnostics.printKeyTransitions. */
    fun printKeyTransitions(trace: ContextualNightTrace) {
        var lastReason: String? = null
        trace.minutes.forEach { m ->
            if (m.reason.isNotEmpty() && m.reason != lastReason) {
                println(
                    "    [min ${m.minute}] score=${"%.0f".format(m.sleepProbability * 100)}% " +
                        "seuil_effectif=${"%.0f".format(m.effectiveThreshold * 100)}% " +
                        "bpm=${m.bpm?.let { "%.0f".format(it) } ?: "—"} raison=\"${m.reason}\""
                )
                lastReason = m.reason
            }
        }
    }
}
