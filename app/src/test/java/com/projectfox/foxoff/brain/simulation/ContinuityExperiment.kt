package com.projectfox.foxoff.brain.simulation

import com.projectfox.foxoff.brain.SleepScoringConfig
import com.projectfox.foxoff.brain.SleepState
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Expérience "logique de continuité du signal BPM" (2026-08-16, demande
 * explicite de Lionel après l'expérience contrôlée durée/seuil) — teste 7
 * stratégies de tolérance aux interruptions du flux BPM, EXCLUSIVEMENT
 * dans un moteur de simulation PARALLÈLE, séparé de `FoxBrain`/
 * `WeightedSleepAnalyzer`/`FoxCore` (production, jamais touchés).
 *
 * Pourquoi un moteur séparé plutôt que remplacer `WeightedSleepAnalyzer`
 * dans le simulateur existant : `FoxBrain.onEvent()` recalcule lui-même
 * (indépendamment de l'analyseur) la remise à zéro de
 * `bpmBelowBaselineSince`/`lastBpmDropBonusAt` à chaque `HeartRateReceived`
 * — un analyseur alternatif seul ne suffirait donc pas à changer le
 * comportement réel de continuité, il faudrait modifier `FoxBrain.kt`
 * (production, exclu). Ce moteur réimplémente la même logique de score
 * (bonus BPM, pénalité mouvement, bonus/malus TV, seuils SleepState) que
 * `WeightedSleepAnalyzer`, mais avec la continuité BPM rendue "branchable" —
 * réutilise `NightSimulator.bpmAt`/`movementAt` pour générer EXACTEMENT
 * les mêmes données physiologiques simulées que le reste du framework
 * (comparaison strictement contrôlée).
 *
 * La stratégie 1 (RESET_IMMEDIAT) DOIT reproduire à l'identique les
 * résultats de la config de production sur les mêmes 125 000 nuits — c'est
 * la vérification de fidélité du moteur avant de faire confiance aux
 * variantes (voir `ContinuityExperimentTest`).
 */

/** État de continuité BPM porté par une stratégie — remplace `bpmBelowBaselineSince`/`lastBpmDropBonusAt` de FoxBrainState. */
data class ContinuityState(
    val belowSince: Instant? = null,
    val lastBonusAt: Instant? = null,
    val consecutiveMisses: Int = 0
)

/** Stratégie de tolérance aux interruptions — remplace UNIQUEMENT la logique de remise à zéro, rien d'autre. */
interface ContinuityStrategy {
    val label: String
    fun update(state: ContinuityState, belowThreshold: Boolean, now: Instant): ContinuityState
}

object ContinuityStrategies {

    /** 1. Logique actuelle de production : toute interruption remet tout à zéro immédiatement. */
    object ResetImmediat : ContinuityStrategy {
        override val label = "1-RESET_IMMEDIAT (production)"
        override fun update(state: ContinuityState, belowThreshold: Boolean, now: Instant) =
            if (belowThreshold) state.copy(belowSince = state.belowSince ?: now, consecutiveMisses = 0)
            else ContinuityState()
    }

    /** 2/3/4. Tolère jusqu'à `maxMisses` échantillons consécutifs au-dessus du seuil sans rien perdre. */
    class Tolerance(private val maxMisses: Int, order: Int) : ContinuityStrategy {
        override val label = "$order-TOLERANCE_${maxMisses}_ECHANTILLON${if (maxMisses > 1) "S" else ""}"
        override fun update(state: ContinuityState, belowThreshold: Boolean, now: Instant): ContinuityState {
            if (belowThreshold) return state.copy(belowSince = state.belowSince ?: now, consecutiveMisses = 0)
            val misses = state.consecutiveMisses + 1
            return if (misses <= maxMisses) state.copy(consecutiveMisses = misses) else ContinuityState()
        }
    }

    /** 5. Décroissance partielle : une interruption fait perdre la MOITIÉ du crédit accumulé, jamais la totalité. */
    object PartialDecay : ContinuityStrategy {
        override val label = "5-DECROISSANCE_PARTIELLE_50PCT"
        override fun update(state: ContinuityState, belowThreshold: Boolean, now: Instant): ContinuityState {
            if (belowThreshold) return state.copy(belowSince = state.belowSince ?: now, consecutiveMisses = 0)
            val newBelowSince = state.belowSince?.let { since ->
                val elapsed = Duration.between(since, now)
                now.minus(elapsed.dividedBy(2))
            }
            return state.copy(belowSince = newBelowSince, consecutiveMisses = state.consecutiveMisses + 1)
        }
    }

    /** 6. Décroissance proportionnelle à la durée de l'interruption : 20% de crédit perdu par échantillon manqué consécutif, plafonné à 100%. */
    object ProgressiveDecay : ContinuityStrategy {
        override val label = "6-DECROISSANCE_PROGRESSIVE_20PCT_PAR_ECHANTILLON"
        override fun update(state: ContinuityState, belowThreshold: Boolean, now: Instant): ContinuityState {
            if (belowThreshold) return state.copy(belowSince = state.belowSince ?: now, consecutiveMisses = 0)
            val misses = state.consecutiveMisses + 1
            val lossFraction = (misses * 0.20).coerceAtMost(1.0)
            val newBelowSince = state.belowSince?.let { since ->
                if (lossFraction >= 1.0) null
                else {
                    val elapsed = Duration.between(since, now)
                    now.minus(Duration.ofSeconds((elapsed.seconds * (1 - lossFraction)).toLong()))
                }
            }
            return state.copy(belowSince = newBelowSince, consecutiveMisses = misses)
        }
    }

    /** 7. Combinaison : 1 échantillon toléré SANS pénalité, au-delà décroissance partielle (50%) plutôt que reset complet. */
    object ToleranceThenPartialDecay : ContinuityStrategy {
        override val label = "7-TOLERANCE_1_PUIS_DECROISSANCE_PARTIELLE"
        override fun update(state: ContinuityState, belowThreshold: Boolean, now: Instant): ContinuityState {
            if (belowThreshold) return state.copy(belowSince = state.belowSince ?: now, consecutiveMisses = 0)
            val misses = state.consecutiveMisses + 1
            if (misses == 1) return state.copy(consecutiveMisses = misses) // tolère sans pénalité
            val newBelowSince = state.belowSince?.let { since ->
                val elapsed = Duration.between(since, now)
                now.minus(elapsed.dividedBy(2))
            }
            return state.copy(belowSince = newBelowSince, consecutiveMisses = misses)
        }
    }

    fun all(): List<ContinuityStrategy> = listOf(
        ResetImmediat,
        Tolerance(1, 2),
        Tolerance(2, 3),
        Tolerance(3, 4),
        PartialDecay,
        ProgressiveDecay,
        ToleranceThenPartialDecay
    )
}

/** Résultat d'une nuit rejouée par le moteur expérimental — mêmes champs que NightSimulationResult pour rester comparable. */
data class ExperimentalNightResult(
    val profile: NightProfile,
    val reachedAsleepAt: Instant?,
    val pauseTriggeredAt: Instant?,
    val maxProbability: Float
)

/**
 * Moteur de score expérimental — réimplémente fidèlement les règles de
 * `WeightedSleepAnalyzer` (bonus BPM soutenu, pénalité mouvement, bonus/
 * malus TV, seuils SleepState 90/70/40, décision de pause identique à
 * `FoxCore.shouldSendAutoPause` avec confiance fixe 0.85 comme en
 * production), à l'exception de la continuité BPM qui est déléguée à une
 * [ContinuityStrategy] branchable. N'appelle JAMAIS FoxBrain/FoxCore.
 */
object ContinuityExperimentEngine {

    private const val CONFIDENCE = 0.85f

    fun run(profile: NightProfile, config: SleepScoringConfig, strategy: ContinuityStrategy): ExperimentalNightResult {
        val sim = NightSimulator()
        val rng = Random(profile.hashCode())

        var currentProb = 0f
        var minBpmToday = 0
        val restingBpmBaseline = profile.restingBpmBaseline ?: 70
        var tvConnected = false
        var tvIsPaused = false
        var continuity = ContinuityState()

        var reachedAsleepAt: Instant? = null
        var pauseTriggeredAt: Instant? = null
        var maxProbability = 0f

        if (profile.tvOn) {
            currentProb = (currentProb + config.tvOnBonus).coerceIn(0f, 1f)
            tvConnected = true
        }

        var watchIsOut = false
        var tvTurnedOffDispatched = false

        for (minute in 0 until NightSimulator.NIGHT_MINUTES) {
            val instant = NightSimulator.NIGHT_START.plusSeconds(minute * 60L)
            val inDropout = profile.watchDropoutRange?.contains(minute) == true

            if (inDropout && !watchIsOut) watchIsOut = true
            else if (!inDropout && watchIsOut) watchIsOut = false

            if (!tvTurnedOffDispatched && profile.tvOn && profile.tvOffMinute == minute) {
                tvTurnedOffDispatched = true
                currentProb = (currentProb * config.tvTurnedOffMultiplier).coerceIn(0f, 1f)
                tvConnected = false
            }

            if (!inDropout) {
                val bpm = sim.bpmAt(profile, minute, rng)
                minBpmToday = if (minBpmToday == 0) bpm.toInt() else minOf(minBpmToday, bpm.toInt())
                val baseline = if (minBpmToday > 0) minOf(minBpmToday.toFloat(), restingBpmBaseline.toFloat()) else restingBpmBaseline.toFloat()
                val belowThreshold = bpm < baseline * (1 + config.bpmDropThreshold)

                continuity = strategy.update(continuity, belowThreshold, instant)

                val reference = continuity.lastBonusAt ?: continuity.belowSince
                val due = belowThreshold && reference != null &&
                    !Duration.between(reference, instant).isNegative &&
                    Duration.between(reference, instant) >= config.sustainedBpmDropDuration

                if (belowThreshold && due) {
                    currentProb = (currentProb + config.bpmDropBonus).coerceIn(0f, 1f)
                    continuity = continuity.copy(lastBonusAt = instant)
                }
            }

            sim.movementAt(profile, minute, rng)?.let { magnitude ->
                if (magnitude > config.movementThreshold) {
                    currentProb = (currentProb - config.significantMovementPenalty).coerceIn(0f, 1f)
                }
            }

            val state = when {
                currentProb >= 0.90f -> SleepState.ASLEEP
                currentProb >= 0.70f -> SleepState.PRE_SLEEP
                currentProb >= 0.40f -> SleepState.DROWSY
                else -> SleepState.AWAKE
            }

            maxProbability = maxOf(maxProbability, currentProb)
            if (reachedAsleepAt == null && state == SleepState.ASLEEP) reachedAsleepAt = instant
            if (pauseTriggeredAt == null && state == SleepState.ASLEEP && CONFIDENCE > config.autoPauseConfidenceThreshold && !tvIsPaused) {
                pauseTriggeredAt = instant
                tvIsPaused = true
            }
        }

        return ExperimentalNightResult(profile, reachedAsleepAt, pauseTriggeredAt, maxProbability)
    }
}
