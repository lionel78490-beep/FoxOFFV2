package com.projectfox.foxoff.brain.simulation

import com.projectfox.foxoff.brain.SleepScoringConfig
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** Résultat d'évaluation d'une nuit contre sa vérité terrain (`NightProfile.groundTruthAsleepMinute`). */
data class NightEvaluation(
    val profile: NightProfile,
    val delayMinutes: Int?,
    val isFalsePositive: Boolean,
    val isMissedDetection: Boolean,
    val loss: Double
)

data class ConfigStats(
    val config: SleepScoringConfig,
    val nightCount: Int,
    val falsePositiveCount: Int,
    val missedDetectionCount: Int,
    /** Moyenne/médiane/P95/pire — calculés UNIQUEMENT sur les nuits correctement détectées (ni faux positif, ni manquée). */
    val averageDelayMinutes: Double,
    val medianDelayMinutes: Double,
    val p95DelayMinutes: Int,
    val worstDelayMinutes: Int,
    val meanLoss: Double
) {
    val falsePositiveRate: Double get() = falsePositiveCount.toDouble() / nightCount
    val missedDetectionRate: Double get() = missedDetectionCount.toDouble() / nightCount
    /**
     * Taux d'"erreur de mise en pause" au sens large (2026-08-15, deuxième
     * run demandé) : faux positif OU détection manquée — les deux façons
     * de se tromper sur QUAND (ou SI) la pause doit partir. C'est cette
     * grandeur, pas seulement `falsePositiveRate`, qu'`OptimizeSleepScoringConfigTest`
     * contraint à ne jamais dépasser celle de la production.
     */
    val pauseErrorRate: Double get() = (falsePositiveCount + missedDetectionCount).toDouble() / nightCount
}

/**
 * Recherche + évaluation de `SleepScoringConfig` sur une liste de
 * `NightProfile` déjà générée — voir plan 2026-08-15.
 *
 * ÉTANCHE PAR CONSTRUCTION (garde-fou explicite du plan) : ce fichier reçoit
 * toujours une `List<NightProfile>` en paramètre, ne dépend jamais de
 * `ProceduralNightGenerator` et ne génère ni n'ajuste aucun profil — il ne
 * fait qu'évaluer des configurations contre une vérité terrain figée en
 * amont, jamais l'inverse.
 */
object ConfigOptimizer {

    // Doit rester synchronisé avec NightSimulator.NIGHT_START (companion
    // privé, non exposé) — même raison que ProceduralNightGenerator pour
    // ONSET_DURATION_MINUTES : dupliquer une seule constante plutôt
    // qu'élargir une visibilité pour un seul appelant de test.
    private val NIGHT_START: Instant = Instant.parse("2026-01-01T23:00:00Z")

    private const val FALSE_POSITIVE_BASE_PENALTY = 1000.0
    private const val MISSED_DETECTION_PENALTY = 500.0

    fun evaluate(
        profile: NightProfile,
        config: SleepScoringConfig,
        simulator: NightSimulator = NightSimulator()
    ): NightEvaluation {
        val result = simulator.run(profile, config)
        val truth = profile.groundTruthAsleepMinute
        val pauseMinute = result.pauseTriggeredAt?.let { Duration.between(NIGHT_START, it).toMinutes().toInt() }

        return when {
            // Nuit d'insomnie (pas de vérité terrain) : toute pause est un faux positif.
            truth == null && pauseMinute != null ->
                NightEvaluation(profile, null, isFalsePositive = true, isMissedDetection = false, loss = FALSE_POSITIVE_BASE_PENALTY)
            truth == null ->
                NightEvaluation(profile, null, isFalsePositive = false, isMissedDetection = false, loss = 0.0)
            // Pause avant la vérité terrain = pause prématurée (faux positif).
            pauseMinute != null && pauseMinute < truth -> {
                val loss = FALSE_POSITIVE_BASE_PENALTY + (truth - pauseMinute)
                NightEvaluation(profile, null, isFalsePositive = true, isMissedDetection = false, loss = loss)
            }
            pauseMinute == null ->
                NightEvaluation(profile, null, isFalsePositive = false, isMissedDetection = true, loss = MISSED_DETECTION_PENALTY)
            else -> {
                val delay = pauseMinute - truth
                NightEvaluation(profile, delay, isFalsePositive = false, isMissedDetection = false, loss = delay.toDouble())
            }
        }
    }

    fun evaluateConfig(profiles: List<NightProfile>, config: SleepScoringConfig): ConfigStats {
        val simulator = NightSimulator()
        val evals = profiles.map { evaluate(it, config, simulator) }
        val delays = evals.mapNotNull { it.delayMinutes }.sorted()
        return ConfigStats(
            config = config,
            nightCount = profiles.size,
            falsePositiveCount = evals.count { it.isFalsePositive },
            missedDetectionCount = evals.count { it.isMissedDetection },
            averageDelayMinutes = if (delays.isNotEmpty()) delays.average() else Double.NaN,
            medianDelayMinutes = percentile(delays, 0.50),
            p95DelayMinutes = percentile(delays, 0.95).let { if (it.isNaN()) -1 else it.toInt() },
            worstDelayMinutes = delays.maxOrNull() ?: -1,
            meanLoss = evals.map { it.loss }.average()
        )
    }

    /** Percentile par interpolation la plus proche (liste déjà triée) — Double.NaN si vide. */
    private fun percentile(sortedValues: List<Int>, fraction: Double): Double {
        if (sortedValues.isEmpty()) return Double.NaN
        val index = (fraction * (sortedValues.size - 1)).toInt().coerceIn(0, sortedValues.size - 1)
        return sortedValues[index].toDouble()
    }

    /** Tire `attempts` configurations aléatoires dans des plages réalistes centrées sur la production actuelle. */
    fun randomConfigs(attempts: Int, seed: Long): List<SleepScoringConfig> {
        val rng = Random(seed)
        return (0 until attempts).map {
            SleepScoringConfig(
                bpmDropBonus = 0.08f + rng.nextFloat() * 0.22f,
                significantMovementPenalty = 0.03f + rng.nextFloat() * 0.17f,
                bpmDropThreshold = 0.06f + rng.nextFloat() * 0.12f,
                movementThreshold = 1.0f + rng.nextFloat() * 3.0f,
                sustainedBpmDropDuration = Duration.ofMinutes((1 + rng.nextInt(0, 6)).toLong()),
                tvTurnedOffMultiplier = 0.3f + rng.nextFloat() * 0.7f
            )
        }
    }

    /**
     * Plages ÉLARGIES (2026-08-15, deuxième relance demandée par Lionel :
     * "plus de tentatives, plages de paramètres différentes") — les deux
     * runs précédents ont systématiquement buté sur `sustainedBpmDropDuration`
     * au PLANCHER de `randomConfigs` (1 min), signe que l'optimum pourrait
     * être hors de cette plage. Granularité à la seconde (pas seulement la
     * minute) pour explorer en dessous d'1 min. Fonction séparée plutôt que
     * modifier `randomConfigs` : garde le run du 2026-08-15 matin
     * reproductible tel que documenté dans ROADMAP.md.
     */
    fun randomConfigsWide(attempts: Int, seed: Long): List<SleepScoringConfig> {
        val rng = Random(seed)
        return (0 until attempts).map {
            SleepScoringConfig(
                bpmDropBonus = 0.05f + rng.nextFloat() * 0.35f,           // 0.05-0.40
                significantMovementPenalty = 0.02f + rng.nextFloat() * 0.28f, // 0.02-0.30
                bpmDropThreshold = 0.03f + rng.nextFloat() * 0.19f,       // 0.03-0.22
                movementThreshold = 0.5f + rng.nextFloat() * 4.5f,       // 0.5-5.0
                sustainedBpmDropDuration = Duration.ofSeconds((15 + rng.nextInt(0, 346)).toLong()), // 15s-6min
                tvTurnedOffMultiplier = 0.2f + rng.nextFloat() * 0.8f     // 0.2-1.0
            )
        }
    }

    /**
     * Génère `attempts` variantes proches de [base] (perturbation locale,
     * bornée) — phase d'affinage après une exploration large, pour
     * pousser plus loin autour du meilleur candidat trouvé plutôt que de
     * ne redécouvrir que des points déjà tirés (2026-08-15).
     */
    fun localRefinements(base: SleepScoringConfig, attempts: Int, seed: Long): List<SleepScoringConfig> {
        val rng = Random(seed)
        fun jitter(value: Float, spread: Float, min: Float, max: Float) =
            (value + (rng.nextFloat() * 2f - 1f) * spread).coerceIn(min, max)

        return (0 until attempts).map {
            val durationSeconds = (base.sustainedBpmDropDuration.seconds + rng.nextInt(-30, 31))
                .coerceIn(10L, 400L)
            SleepScoringConfig(
                bpmDropBonus = jitter(base.bpmDropBonus, 0.04f, 0.02f, 0.5f),
                significantMovementPenalty = jitter(base.significantMovementPenalty, 0.04f, 0.01f, 0.35f),
                bpmDropThreshold = jitter(base.bpmDropThreshold, 0.03f, 0.02f, 0.25f),
                movementThreshold = jitter(base.movementThreshold, 0.5f, 0.3f, 5.5f),
                sustainedBpmDropDuration = Duration.ofSeconds(durationSeconds),
                tvTurnedOffMultiplier = jitter(base.tvTurnedOffMultiplier, 0.1f, 0.15f, 1.0f)
            )
        }
    }
}
