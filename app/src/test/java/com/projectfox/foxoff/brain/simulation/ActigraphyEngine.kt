package com.projectfox.foxoff.brain.simulation

import com.projectfox.foxoff.brain.SleepScoringConfig
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Expérience "algorithme d'actigraphie" (2026-08-16, suite à l'échec des
 * expériences de continuité/contexte temporel — voir ROADMAP.md Phase 5 et
 * la mémoire `project_sleep_detection_investigation`). Moteur PARALLÈLE,
 * comme toutes les expériences précédentes — ne touche jamais FoxBrain/
 * WeightedSleepAnalyzer/FoxCore réels.
 *
 * Principe repris de la littérature de recherche du sommeil (algorithme de
 * type Cole-Kripke, 1992) : au lieu d'un système événement par événement
 * (bonus/pénalité à chaque échantillon), classer chaque minute "calme" ou
 * "active" à partir d'une SOMME PONDÉRÉE du mouvement sur une fenêtre
 * glissante récente (plus l'instant présent pèse lourd, plus les minutes
 * plus anciennes pèsent progressivement moins) — capture un PATTERN de
 * repos, pas un seuil instantané.
 *
 * Deux adaptations assumées (pas une copie littérale du papier original) :
 * - **Version causale** : l'algorithme original utilise 2 échantillons
 *   FUTURS (t+1, t+2) — impossible en détection temps réel. Les 7 poids
 *   publiés sont réutilisés tels quels mais tous appliqués à des
 *   échantillons PASSÉS (t, t-1, ..., t-6).
 * - **Seuil recalibré** : les poids/coefficient d'échelle originaux sont
 *   calés sur les unités d'un accéléromètre de poignet réel (centaines de
 *   "counts"/époque) — sans rapport avec l'échelle synthétique de
 *   `NightSimulator` (magnitudes ~0-6). Le coefficient d'échelle et le
 *   seuil ci-dessous sont recalibrés pour cette échelle, PAS ceux du
 *   papier original.
 *
 * Deux variantes testées :
 * - **Pure** : sommeil actigraphique SEUL (mouvement uniquement, aucun
 *   BPM) — reproduit fidèlement l'esprit de l'algorithme original.
 * - **Hybride** : exige l'accord des DEUX signaux indépendants (actigraphie
 *   ET seuil BPM existant) avant de déclencher — teste si combiner deux
 *   signaux imparfaits fait mieux que chacun seul.
 */
data class ActigraphyConfig(
    val label: String,
    val hybridWithBpm: Boolean,
    // Poids Cole-Kripke publiés, réordonnés en causal : index 0 = minute
    // courante (poids le plus fort), index 6 = 6 minutes avant.
    val weights: List<Double> = listOf(1408.0, 441.0, 326.0, 598.0, 404.0, 508.0, 350.0),
    val scaleFactor: Double = 0.05,
    val sleepThreshold: Double = 1.0,
    val sustainedQuietMinutes: Int = 5
)

object ActigraphyStrategies {
    val PURE = ActigraphyConfig("ACTIGRAPHIE_PURE (mouvement seul, sans BPM)", hybridWithBpm = false)
    val HYBRID = ActigraphyConfig("ACTIGRAPHIE_HYBRIDE (mouvement ET BPM doivent s'accorder)", hybridWithBpm = true)
    fun all() = listOf(PURE, HYBRID)
}

data class ActigraphyNightResult(
    val profile: NightProfile,
    val reachedAsleepAt: Instant?,
    val pauseTriggeredAt: Instant?,
    val maxProbability: Float
)

object ActigraphyEngine {

    private const val CONFIDENCE = 0.85f

    fun run(profile: NightProfile, config: SleepScoringConfig, strategy: ActigraphyConfig): ActigraphyNightResult {
        val sim = NightSimulator()
        val rng = Random(profile.hashCode())

        // --- État BPM (identique au moteur "reset immédiat" des expériences précédentes) ---
        var currentProb = 0f
        var minBpmToday = 0
        val restingBpmBaseline = profile.restingBpmBaseline ?: 70
        var belowSince: Instant? = null
        var lastBonusAt: Instant? = null

        // --- État actigraphie ---
        val activityWindow = ArrayDeque<Double>() // plus récent en tête (index 0)
        var consecutiveQuiet = 0
        var actigraphySleep = false

        var tvIsPaused = false
        var reachedAsleepAt: Instant? = null
        var pauseTriggeredAt: Instant? = null
        var maxProbability = 0f

        if (profile.tvOn) currentProb = (currentProb + config.tvOnBonus).coerceIn(0f, 1f)
        var tvTurnedOffDispatched = false

        for (minute in 0 until NightSimulator.NIGHT_MINUTES) {
            val instant = NightSimulator.NIGHT_START.plusSeconds(minute * 60L)
            val inDropout = profile.watchDropoutRange?.contains(minute) == true

            if (!tvTurnedOffDispatched && profile.tvOn && profile.tvOffMinute == minute) {
                tvTurnedOffDispatched = true
                currentProb = (currentProb * config.tvTurnedOffMultiplier).coerceIn(0f, 1f)
            }

            // --- BPM (uniquement utile en mode hybride, calculé quand même pour la variante pure car peu coûteux) ---
            if (!inDropout) {
                val bpm = sim.bpmAt(profile, minute, rng)
                minBpmToday = if (minBpmToday == 0) bpm.toInt() else minOf(minBpmToday, bpm.toInt())
                val baseline = if (minBpmToday > 0) minOf(minBpmToday.toFloat(), restingBpmBaseline.toFloat()) else restingBpmBaseline.toFloat()
                val belowThreshold = bpm < baseline * (1 + config.bpmDropThreshold)
                if (belowThreshold) belowSince = belowSince ?: instant else { belowSince = null; lastBonusAt = null }
                val reference = lastBonusAt ?: belowSince
                val due = belowThreshold && reference != null &&
                    !Duration.between(reference, instant).isNegative &&
                    Duration.between(reference, instant) >= config.sustainedBpmDropDuration
                if (belowThreshold && due) {
                    currentProb = (currentProb + config.bpmDropBonus).coerceIn(0f, 1f)
                    lastBonusAt = instant
                }
            }

            val movement = sim.movementAt(profile, minute, rng)
            if (movement != null && movement > config.movementThreshold) {
                currentProb = (currentProb - config.significantMovementPenalty).coerceIn(0f, 1f)
            }

            // --- Fenêtre d'activité (mouvement brut, 0 si absent ce pas-ci) ---
            activityWindow.addFirst(movement?.toDouble() ?: 0.0)
            if (activityWindow.size > strategy.weights.size) activityWindow.removeLast()

            val dScore = activityWindow.indices.sumOf { i -> strategy.weights.getOrElse(i) { 0.0 } * activityWindow[i] } * strategy.scaleFactor
            val quietNow = dScore < strategy.sleepThreshold
            consecutiveQuiet = if (quietNow) consecutiveQuiet + 1 else 0
            if (consecutiveQuiet >= strategy.sustainedQuietMinutes) actigraphySleep = true
            if (!quietNow) actigraphySleep = false // le sommeil actigraphique cesse dès qu'un vrai mouvement casse le calme

            maxProbability = maxOf(maxProbability, currentProb)

            val asleepNow = if (strategy.hybridWithBpm) {
                actigraphySleep && currentProb >= 0.90f
            } else {
                actigraphySleep
            }

            if (asleepNow) {
                if (reachedAsleepAt == null) reachedAsleepAt = instant
                if (pauseTriggeredAt == null && !tvIsPaused) {
                    pauseTriggeredAt = instant
                    tvIsPaused = true
                }
            }
        }

        return ActigraphyNightResult(profile, reachedAsleepAt, pauseTriggeredAt, maxProbability)
    }
}
