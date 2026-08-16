package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ConfigOptimizer
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Recherche de configuration (2026-08-16, après la régression réelle du
 * 15-16 août — voir ROADMAP.md Phase 5 et
 * `RealNightReplayPostOptimizationTest`) qui corrige la LEÇON de cette
 * régression : le run d'optimisation du 15 août n'était validé QUE sur des
 * nuits SYNTHÉTIQUES, jamais sur de vraies nuits — angle mort qui a laissé
 * passer une config qui échouait totalement sur données réelles.
 *
 * Cette recherche ajoute donc les DEUX vraies nuits capturées à ce jour
 * comme contrainte de validation, EN PLUS du jeu synthétique existant
 * (dual constraint FP/manqués habituel) :
 * - Nuit du 15 août (`RealNightReplayTest`, retard réel 6h08 avec la
 *   config d'ALORS) ;
 * - Nuit du 15-16 août (`RealNightReplayPostOptimizationTest`, échec total
 *   avec la config post-optimisation, 76% final avec la config historique
 *   actuelle mais ASLEEP atteint seulement vers 4h15, ~6h20 après
 *   l'endormissement réel).
 *
 * Métrique réelle-nuit : délai (en minutes, depuis la première ligne du
 * journal) jusqu'à ce que le rejeu atteigne ASLEEP (score ≥ 90%) pour la
 * PREMIÈRE fois — comparé au même délai pour la config de PRODUCTION
 * ACTUELLE (déjà revenue à l'historique) rejouée à l'identique. Une
 * candidate n'est retenue QUE si elle : (1) ne dégrade ni le taux de faux
 * positifs ni le taux de détections manquées sur le jeu synthétique
 * (comme d'habitude), ET (2) atteint ASLEEP sur les DEUX vraies nuits
 * (jamais bloquée), ET (3) le fait au moins aussi vite que la production
 * actuelle sur les DEUX vraies nuits. Aucune candidate n'est appliquée
 * automatiquement à la production.
 *
 * Test "lourd" (~2-4 min) — exclu du `./gradlew test` de routine.
 */
class OptimizeWithRealNightsTest {

    // ---- Chargement générique d'un journal réel (même format que
    // RealNightReplayTest/RealNightReplayPostOptimizationTest, dupliqué ici
    // pour rester autonome) ----
    private data class LogLine(val dayOffset: Long, val time: LocalTime, val type: String, val detail: String)

    private fun loadLog(resourceName: String): List<LogLine> {
        val resource = javaClass.classLoader!!.getResourceAsStream("night-logs/$resourceName")
            ?: error("Journal introuvable : $resourceName")
        var dayOffset = 0L
        var lastTime: LocalTime? = null
        return resource.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(" | ", limit = 3)
                val timePart = parts[0].substringAfter(" ")
                val time = LocalTime.parse(timePart)
                if (lastTime != null && time < lastTime) dayOffset += 1
                lastTime = time
                LogLine(dayOffset, time, parts[1].trim(), parts[2].trim())
            }
    }

    private val anchorDate = LocalDateTime.of(2026, 8, 15, 0, 0, 0)
    private fun instantAt(dayOffset: Long, time: LocalTime): Instant =
        anchorDate.toLocalDate().plusDays(dayOffset).atTime(time).toInstant(ZoneOffset.UTC)

    private fun dispatch(brain: FoxBrain, event: FoxBrainEvent, instant: Instant) {
        event.timestamp = instant
        brain.onEvent(event)
    }

    /** Rejoue un journal réel et retourne le délai (min) jusqu'au premier ASLEEP, ou null si jamais atteint. */
    private fun delayToFirstAsleep(lines: List<LogLine>, config: SleepScoringConfig, restingBaseline: Int): Int? {
        val brain = FoxBrain(WeightedSleepAnalyzer(config), config)
        brain.setRestingBpmBaseline(restingBaseline)
        val startInstant = instantAt(lines.first().dayOffset, lines.first().time)
        dispatch(brain, FoxBrainEvent.TVTurnedOn, startInstant)
        var firstTvOffInferred = false

        for (line in lines) {
            val instant = instantAt(line.dayOffset, line.time)
            when (line.type) {
                "MOVEMENT" -> {
                    val magnitude = Regex("Magnitude ([\\d,]+)").find(line.detail)!!
                        .groupValues[1].replace(',', '.').toFloat()
                    dispatch(brain, FoxBrainEvent.MovementDetected(magnitude), instant)
                }
                "HEART_RATE_TREND" -> {
                    val bpm = Regex("(\\d+) bpm").find(line.detail)!!.groupValues[1].toFloat()
                    dispatch(brain, FoxBrainEvent.HeartRateReceived(bpm, "REAL"), instant)
                }
                "TV_ON" -> dispatch(brain, FoxBrainEvent.TVTurnedOn, instant)
                "TV_OFF" -> dispatch(brain, FoxBrainEvent.TVTurnedOff, instant)
                "SLEEP_STATE_CHANGE" -> {
                    if (!firstTvOffInferred && line.detail.contains("TV éteinte")) {
                        firstTvOffInferred = true
                        dispatch(brain, FoxBrainEvent.TVTurnedOff, instant)
                    }
                }
            }
            if (brain.state.value.detectedSleepState == SleepState.ASLEEP) {
                return Duration.between(startInstant, instant).toMinutes().toInt()
            }
        }
        return null
    }

    @Test
    fun `recherche une config qui bat la production sur les 2 vraies nuits sans degrader le synthetique`() {
        val nightA = loadLog("2026-08-15-real-night.log")
        val nightB = loadLog("2026-08-15-post-optimization-night.log")

        val production = SleepScoringConfig() // déjà revenue à l'historique (2026-08-16)

        val baselineDelayA = delayToFirstAsleep(nightA, production, restingBaseline = 50)
        val baselineDelayB = delayToFirstAsleep(nightB, production, restingBaseline = 50)
        println("=== Référence PRODUCTION actuelle ===")
        println("Nuit A (15 août)    : délai jusqu'à ASLEEP = ${baselineDelayA?.let { "$it min" } ?: "JAMAIS atteint"}")
        println("Nuit B (15-16 août) : délai jusqu'à ASLEEP = ${baselineDelayB?.let { "$it min" } ?: "JAMAIS atteint"}")
        requireNotNull(baselineDelayA) { "la production actuelle doit atteindre ASLEEP sur la nuit A (sinon plus de référence valable)" }
        requireNotNull(baselineDelayB) { "la production actuelle doit atteindre ASLEEP sur la nuit B (sinon plus de référence valable)" }

        println("\n=== Chargement du jeu synthétique (référence FP/manqués) ===")
        val synthetic = ProfiledNightGenerator.generate(seed = 20260815L).take(8_000)
        val baselineStats = ConfigOptimizer.evaluateConfig(synthetic, production)
        println(
            "Production sur synthétique (8000 nuits) : FP=${"%.2f".format(baselineStats.falsePositiveRate * 100)}% " +
                "manqués=${"%.2f".format(baselineStats.missedDetectionRate * 100)}% délai moyen=${"%.1f".format(baselineStats.averageDelayMinutes)}min"
        )

        println("\n=== Recherche de candidates (large + affinage local autour de l'historique) ===")
        val candidates = ConfigOptimizer.randomConfigsWide(600, seed = 20260816L) +
            ConfigOptimizer.localRefinements(production, 300, seed = 20260816L + 1)

        data class Result(
            val config: SleepScoringConfig, val fpRate: Double, val missedRate: Double,
            val delayA: Int?, val delayB: Int?
        )

        val passingCoarse = candidates.filter { cfg ->
            val stats = ConfigOptimizer.evaluateConfig(synthetic, cfg)
            stats.falsePositiveRate <= baselineStats.falsePositiveRate && stats.missedDetectionRate <= baselineStats.missedDetectionRate
        }
        println("Candidates passant le filtre synthétique (FP et manqués <= production) : ${passingCoarse.size} / ${candidates.size}")

        val results = passingCoarse.map { cfg ->
            val delayA = delayToFirstAsleep(nightA, cfg, restingBaseline = 50)
            val delayB = delayToFirstAsleep(nightB, cfg, restingBaseline = 50)
            val stats = ConfigOptimizer.evaluateConfig(synthetic, cfg)
            Result(cfg, stats.falsePositiveRate, stats.missedDetectionRate, delayA, delayB)
        }

        val passingRealNights = results.filter { r ->
            r.delayA != null && r.delayB != null && r.delayA <= baselineDelayA && r.delayB <= baselineDelayB
        }
        println("Candidates atteignant ASLEEP sur les 2 vraies nuits, au moins aussi vite que la production : ${passingRealNights.size}")

        if (passingRealNights.isEmpty()) {
            println("\nAUCUNE candidate ne bat la production sur les 2 vraies nuits sans dégrader le synthétique.")
            println("La configuration actuelle (historique, déjà en production) reste la meilleure connue.")
            return
        }

        val best = passingRealNights.minByOrNull { (it.delayA!! + it.delayB!!) }!!
        println("\n=== MEILLEURE candidate trouvée ===")
        println("Nuit A : ${best.delayA} min (production : $baselineDelayA min)")
        println("Nuit B : ${best.delayB} min (production : $baselineDelayB min)")
        println("Synthétique : FP=${"%.2f".format(best.fpRate * 100)}% manqués=${"%.2f".format(best.missedRate * 100)}%")
        println(
            "Config : bpmDropBonus=${best.config.bpmDropBonus} bpmDropThreshold=${best.config.bpmDropThreshold} " +
                "sustainedBpmDropDuration=${best.config.sustainedBpmDropDuration} movementThreshold=${best.config.movementThreshold} " +
                "significantMovementPenalty=${best.config.significantMovementPenalty} tvTurnedOffMultiplier=${best.config.tvTurnedOffMultiplier}"
        )

        // --- Validation indépendante : jeu synthétique différent (seed
        // différent), même règle de non-dégradation, avant de considérer ce
        // résultat comme autre chose qu'un coup de chance sur ces 8000 nuits. ---
        val validation = ProfiledNightGenerator.generate(seed = 99_999_999L).take(4_000)
        val validationStats = ConfigOptimizer.evaluateConfig(validation, best.config)
        val validationBaseline = ConfigOptimizer.evaluateConfig(validation, production)
        println("\n=== Validation indépendante (4000 nuits, seed différent) ===")
        println("Production : FP=${"%.2f".format(validationBaseline.falsePositiveRate * 100)}% manqués=${"%.2f".format(validationBaseline.missedDetectionRate * 100)}%")
        println("Candidate  : FP=${"%.2f".format(validationStats.falsePositiveRate * 100)}% manqués=${"%.2f".format(validationStats.missedDetectionRate * 100)}%")
        val validated = validationStats.falsePositiveRate <= validationBaseline.falsePositiveRate &&
            validationStats.missedDetectionRate <= validationBaseline.missedDetectionRate
        println(if (validated) "VALIDÉE sur jeu indépendant." else "REJETÉE (sur-ajustement) : dégrade le jeu de validation indépendant.")
    }
}
