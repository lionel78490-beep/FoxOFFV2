package com.projectfox.foxoff.brain.simulation

import com.projectfox.foxoff.brain.SleepScoringConfig
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Grande réinvestigation (2026-08-16, demande explicite de Lionel : "revoir
 * la manière de le détecter", "quelque chose de très optimisé") — 4
 * mécanismes JAMAIS testés jusqu'ici (voir mémoire
 * `project_sleep_detection_investigation` pour ce qui a déjà été écarté :
 * continuité BPM, contexte temporel, actigraphie, HRV). Moteur PARALLÈLE,
 * comme `ContinuityExperimentEngine`/`ContextualDetectionEngine` — ne
 * touche jamais FoxBrain/WeightedSleepAnalyzer/FoxCore réels, réplique
 * fidèlement leurs règles de base puis ajoute les mécanismes ci-dessous,
 * activables indépendamment pour isoler leur effet avant toute combinaison.
 *
 * 1. **Plancher glissant** (`rollingBaseline`) : remplace `minBpmToday`
 *    (minimum ABSOLU de la nuit, ne fait que baisser par conception — cause
 *    racine de la régression du 15-16 août, voir ROADMAP.md Phase 5) par un
 *    minimum sur une FENÊTRE RÉCENTE glissante (`rollingWindowMinutes`). Un
 *    creux ponctuel ancien n'enferme plus le seuil indéfiniment.
 * 2. **Bonus de tendance** (`trendBonus`) : au lieu de ne réagir qu'au
 *    NIVEAU absolu du BPM, récompense une VITESSE de baisse soutenue
 *    (pente sur `trendWindowMinutes`) — signal "en train de s'endormir",
 *    potentiellement plus rapide qu'attendre le franchissement d'un seuil
 *    fixe, et différent du signal de niveau déjà utilisé.
 * 3. **Pénalité de densité de mouvement** (`movementDensityPenalty`) :
 *    au lieu d'un seuil binaire par événement (`movementThreshold`), compte
 *    TOUS les mouvements (même sous le seuil) sur une fenêtre glissante —
 *    une agitation faite de nombreux petits mouvements, individuellement
 *    ignorés aujourd'hui, est pénalisée si elle dépasse un compte normal.
 * 4. **Lissage EMA** (`emaSmoothing`) : la décision (ASLEEP/DROWSY/pause)
 *    se base sur une moyenne mobile exponentielle du score brut plutôt que
 *    le score brut lui-même — amortit le choc d'un seul événement (TV
 *    éteinte, mouvement isolé) sans changer la logique d'accumulation.
 *
 * Combinables librement via [NextGenConfig] — voir [NextGenStrategies] pour
 * les combinaisons testées isolément puis ensemble.
 *
 * [NextGenScorer] est basé sur des `Instant` (pas des index de minute) pour
 * être réutilisable À L'IDENTIQUE sur des nuits SYNTHÉTIQUES (pas de temps
 * régulier, une minute) ET sur de VRAIES nuits rejouées depuis un journal
 * (horodatages irréguliers) — même code testé des deux côtés, condition
 * explicite posée cette session après la régression du 15-16 août (plus
 * jamais valider un mécanisme sur le seul synthétique).
 */
data class NextGenConfig(
    val label: String,

    val rollingBaseline: Boolean = false,
    val rollingWindowMinutes: Long = 90,

    val trendBonus: Boolean = false,
    val trendWindowMinutes: Long = 15,
    val trendBonusAmount: Float = 0.06f,
    val trendSlopeThresholdPerMinute: Float = -0.20f, // bpm/min, négatif = baisse
    val trendBonusCooldownMinutes: Long = 10,
    /** Le bonus de tendance n'est accordé QUE si le score brut a déjà atteint ce plancher — évite qu'il soit, seul, le déclencheur d'un faux positif précoce. */
    val trendRequireMinProb: Float = 0f,

    val movementDensityPenalty: Boolean = false,
    val densityWindowMinutes: Long = 20,
    val densityCountThreshold: Int = 4,
    val densityMinMagnitude: Float = 0.3f, // ignore le vrai bruit capteur (<0.3)
    val densityPenaltyPerExcess: Float = 0.03f,

    val emaSmoothing: Boolean = false,
    val emaAlpha: Float = 0.35f // poids du score brut de cette minute ; (1-alpha) = poids de l'historique lissé
)

object NextGenStrategies {
    val BASELINE = NextGenConfig("BASELINE (production actuelle, aucun mécanisme)")
    val M1_PLANCHER_GLISSANT = NextGenConfig("M1-PLANCHER_GLISSANT", rollingBaseline = true)
    val M2_TENDANCE_BPM = NextGenConfig("M2-TENDANCE_BPM", trendBonus = true)
    val M3_DENSITE_MOUVEMENT = NextGenConfig("M3-DENSITE_MOUVEMENT", movementDensityPenalty = true)
    val M4_LISSAGE_EMA = NextGenConfig("M4-LISSAGE_EMA", emaSmoothing = true)

    fun isolated() = listOf(BASELINE, M1_PLANCHER_GLISSANT, M2_TENDANCE_BPM, M3_DENSITE_MOUVEMENT, M4_LISSAGE_EMA)

    /**
     * Version verrouillée de M2 (2026-08-16, suite au run isolé sur 1000
     * puis 100 000 nuits : FP explosé 1,5% -> 13,3% avec les réglages
     * d'origine) — pente exigée bien plus raide, fenêtre plus longue,
     * cooldown allongé, ET surtout `trendRequireMinProb` : le bonus de
     * tendance ne peut plus À LUI SEUL déclencher un faux positif précoce,
     * seulement accélérer une progression déjà réelle (score déjà >= 15%).
     */
    val M2B_TENDANCE_VERROUILLEE = NextGenConfig(
        "M2b-TENDANCE_VERROUILLEE",
        trendBonus = true,
        trendWindowMinutes = 20,
        trendBonusAmount = 0.05f,
        trendSlopeThresholdPerMinute = -0.35f,
        trendBonusCooldownMinutes = 15,
        trendRequireMinProb = 0.15f
    )

    /** Combinaisons des mécanismes qui ont individuellement tenu la double contrainte FP/manqués. */
    fun combinations() = listOf(
        NextGenConfig("COMBO-M1+M2b", rollingBaseline = true, trendBonus = true,
            trendWindowMinutes = 20, trendBonusAmount = 0.05f, trendSlopeThresholdPerMinute = -0.35f,
            trendBonusCooldownMinutes = 15, trendRequireMinProb = 0.15f),
        NextGenConfig("COMBO-M1+M3", rollingBaseline = true, movementDensityPenalty = true),
        NextGenConfig("COMBO-M1+M4", rollingBaseline = true, emaSmoothing = true),
        NextGenConfig("COMBO-M1+M2b+M4", rollingBaseline = true, trendBonus = true,
            trendWindowMinutes = 20, trendBonusAmount = 0.05f, trendSlopeThresholdPerMinute = -0.35f,
            trendBonusCooldownMinutes = 15, trendRequireMinProb = 0.15f, emaSmoothing = true)
    )
}

/**
 * Cœur de la logique — un état mutable qu'on nourrit événement par
 * événement (BPM/mouvement/TV), quelle que soit la source (synthétique ou
 * journal réel). Réplique fidèlement WeightedSleepAnalyzer/FoxBrain pour
 * les règles inchangées, ajoute les 4 mécanismes ci-dessus.
 */
class NextGenScorer(
    private val config: SleepScoringConfig,
    private val strategy: NextGenConfig,
    restingBpmBaseline: Int,
    tvOnAtStart: Boolean
) {
    private val restingBpmBaseline = restingBpmBaseline.toFloat()
    var rawProb: Float = 0f
        private set
    var smoothedProb: Float = 0f
        private set
    private var minBpmToday = 0

    private var belowSince: Instant? = null
    private var lastBonusAt: Instant? = null
    private var lastTrendBonusAt: Instant? = null

    private val bpmWindow = ArrayDeque<Pair<Instant, Float>>()
    private val movementWindow = ArrayDeque<Pair<Instant, Float>>()

    var reachedAsleepAt: Instant? = null
        private set
    var pauseTriggeredAt: Instant? = null
        private set
    var maxProbability: Float = 0f
        private set

    init {
        if (tvOnAtStart) rawProb = (rawProb + config.tvOnBonus).coerceIn(0f, 1f)
        updateDecision(Instant.EPOCH)
    }

    fun onTvOff(instant: Instant) {
        rawProb = (rawProb * config.tvTurnedOffMultiplier).coerceIn(0f, 1f)
        updateDecision(instant)
    }

    fun onTvOn(instant: Instant) {
        rawProb = (rawProb + config.tvOnBonus).coerceIn(0f, 1f)
        updateDecision(instant)
    }

    fun onHeartRate(bpm: Float, instant: Instant) {
        // --- Mécanisme 1 : plancher glissant --- IMPORTANT : calculé à
        // partir de bpmWindow AVANT d'y ajouter la lecture courante (bug
        // corrigé le 2026-08-16 — la première version incluait par erreur
        // la lecture courante dans son propre calcul de seuil, la
        // qualifiant trivialement comme "assez basse" à chaque nouveau
        // minimum ; la logique de production, elle, a toujours exclu la
        // lecture courante — voir WeightedSleepAnalyzer/minBpmToday. Ce
        // moteur doit rester fidèle à cette même exclusion).
        val effectiveMinBpm = if (strategy.rollingBaseline) {
            bpmWindow.filter { Duration.between(it.first, instant).toMinutes() <= strategy.rollingWindowMinutes }
                .minOfOrNull { it.second }?.toInt() ?: minBpmToday
        } else minBpmToday

        val baseline = if (effectiveMinBpm > 0) minOf(effectiveMinBpm.toFloat(), restingBpmBaseline) else restingBpmBaseline
        val belowThreshold = bpm < baseline * (1 + config.bpmDropThreshold)

        minBpmToday = if (minBpmToday == 0) bpm.toInt() else minOf(minBpmToday, bpm.toInt())

        bpmWindow.addLast(instant to bpm)
        val historyLimitMinutes = maxOf(strategy.rollingWindowMinutes, strategy.trendWindowMinutes)
        while (bpmWindow.isNotEmpty() && Duration.between(bpmWindow.first().first, instant).toMinutes() > historyLimitMinutes) {
            bpmWindow.removeFirst()
        }

        if (belowThreshold) {
            belowSince = belowSince ?: instant
        } else {
            belowSince = null
            lastBonusAt = null
        }
        val reference = lastBonusAt ?: belowSince
        val due = belowThreshold && reference != null &&
            !Duration.between(reference, instant).isNegative &&
            Duration.between(reference, instant) >= config.sustainedBpmDropDuration

        if (belowThreshold && due) {
            rawProb = (rawProb + config.bpmDropBonus).coerceIn(0f, 1f)
            lastBonusAt = instant
        }

        // --- Mécanisme 2 : bonus de tendance ---
        if (strategy.trendBonus && bpmWindow.size >= 2) {
            val inWindow = bpmWindow.filter { Duration.between(it.first, instant).toMinutes() <= strategy.trendWindowMinutes }
            if (inWindow.size >= 2) {
                val first = inWindow.first()
                val last = inWindow.last()
                val minutesSpan = Duration.between(first.first, last.first).toMinutes().coerceAtLeast(1)
                val slopePerMinute = (last.second - first.second) / minutesSpan
                val cooldownOk = lastTrendBonusAt == null ||
                    Duration.between(lastTrendBonusAt, instant).toMinutes() >= strategy.trendBonusCooldownMinutes
                val progressOk = rawProb >= strategy.trendRequireMinProb
                if (slopePerMinute <= strategy.trendSlopeThresholdPerMinute && cooldownOk && progressOk) {
                    rawProb = (rawProb + strategy.trendBonusAmount).coerceIn(0f, 1f)
                    lastTrendBonusAt = instant
                }
            }
        }
        updateDecision(instant)
    }

    fun onMovement(magnitude: Float, instant: Instant) {
        if (magnitude > config.movementThreshold) {
            rawProb = (rawProb - config.significantMovementPenalty).coerceIn(0f, 1f)
        }
        movementWindow.addLast(instant to magnitude)
        while (movementWindow.isNotEmpty() && Duration.between(movementWindow.first().first, instant).toMinutes() > strategy.densityWindowMinutes) {
            movementWindow.removeFirst()
        }

        // --- Mécanisme 3 : densité de mouvement ---
        if (strategy.movementDensityPenalty) {
            val count = movementWindow.count { it.second >= strategy.densityMinMagnitude }
            val excess = count - strategy.densityCountThreshold
            if (excess > 0) {
                rawProb = (rawProb - strategy.densityPenaltyPerExcess).coerceIn(0f, 1f)
            }
        }
        updateDecision(instant)
    }

    private fun updateDecision(instant: Instant) {
        // --- Mécanisme 4 : lissage EMA — la DÉCISION se base sur le score
        // lissé, l'accumulateur brut ci-dessus reste inchangé.
        smoothedProb = if (strategy.emaSmoothing) {
            strategy.emaAlpha * rawProb + (1 - strategy.emaAlpha) * smoothedProb
        } else rawProb

        maxProbability = maxOf(maxProbability, smoothedProb)
        if (reachedAsleepAt == null && smoothedProb >= 0.90f) reachedAsleepAt = instant
        // Décision de pause : mêmes seuils que FoxCore.shouldSendAutoPause
        // (score>=0.90 ET confidence>=0.80 — confidence toujours 0.85 en
        // production/simulation, voir WeightedSleepAnalyzer, donc
        // équivalent ici à score>=0.90).
        if (pauseTriggeredAt == null && smoothedProb >= 0.90f) pauseTriggeredAt = instant
    }
}

data class NextGenNightResult(
    val profile: NightProfile,
    val reachedAsleepAt: Instant?,
    val pauseTriggeredAt: Instant?,
    val maxProbability: Float
)

object NextGenDetectionEngine {

    fun run(profile: NightProfile, config: SleepScoringConfig, strategy: NextGenConfig): NextGenNightResult {
        val sim = NightSimulator()
        val rng = Random(profile.hashCode())
        val scorer = NextGenScorer(config, strategy, profile.restingBpmBaseline ?: 70, profile.tvOn)

        var tvTurnedOffDispatched = false
        for (minute in 0 until NightSimulator.NIGHT_MINUTES) {
            val instant = NightSimulator.NIGHT_START.plusSeconds(minute * 60L)
            val inDropout = profile.watchDropoutRange?.contains(minute) == true

            if (!tvTurnedOffDispatched && profile.tvOn && profile.tvOffMinute == minute) {
                tvTurnedOffDispatched = true
                scorer.onTvOff(instant)
            }
            if (!inDropout) {
                scorer.onHeartRate(sim.bpmAt(profile, minute, rng), instant)
            }
            sim.movementAt(profile, minute, rng)?.let { magnitude -> scorer.onMovement(magnitude, instant) }
        }

        return NextGenNightResult(profile, scorer.reachedAsleepAt, scorer.pauseTriggeredAt, scorer.maxProbability)
    }
}
