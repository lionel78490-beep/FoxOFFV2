package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ConfigOptimizer
import com.projectfox.foxoff.brain.simulation.ConfigStats
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test

/**
 * Test "très lourd" (~8-9 min) — à lancer à la demande via
 * `-PheavyTests --tests "*OptimizeFinalOptimizationTest*"`, PAS partie du
 * `./gradlew test` de routine. Voir ROADMAP.md Phase 5.
 *
 * Optimisation finale (2026-08-16, demande explicite et détaillée de
 * Lionel) — reprend la baseline validée du jour précédent (100 000 nuits,
 * seed=20260815L, délai moyen 31,0 min, pire 466 min, faux positifs 2,04%,
 * manqués 10,95%) comme jeu d'ENTRAÎNEMENT COMPLET (pas de split
 * 80/20 cette fois — la validation se fait sur un jeu ENTIÈREMENT séparé,
 * généré avec une seed différente, ≥20 000 nuits).
 *
 * Contrainte dure sur les DEUX axes simultanément (leçon du run précédent,
 * 2026-08-15 : une contrainte sur un seul axe laisse l'autre dériver sans
 * limite) : une candidate n'est retenue pour son délai que si son
 * `falsePositiveRate` ET son `missedDetectionRate` ne sont JAMAIS
 * supérieurs à ceux de la configuration actuelle, à chaque étage.
 *
 * Étanchéité vérité-terrain / optimiseur inchangée : `ProfiledNightGenerator`
 * génère les profils UNE SEULE FOIS, jamais ajustés en fonction des
 * résultats. Ne modifie AUCUN fichier de production — rapport imprimé
 * uniquement, jamais appliqué automatiquement.
 */
class OptimizeFinalOptimizationTest {

    @Test
    fun `optimisation finale sur 100 000 nuits, validation independante sur 25 000 nuits`() {
        // Jeu d'ENTRAÎNEMENT : exactement la baseline validée (même seed).
        val trainProfiles = ProfiledNightGenerator.generate(seed = 20260815L)
        val coarseSubset = trainProfiles.subList(0, 4_000)

        // Jeu de VALIDATION INDÉPENDANTE : seed totalement différente, aucun
        // recouvrement avec l'entraînement. ProfiledNightGenerator impose un
        // minimum de 100 000 (MIN_COUNT) : on en garde 25 000 (>= 20 000
        // demandés) plutôt que de modifier le générateur.
        val validationProfiles = ProfiledNightGenerator.generate(seed = 88_888_888L).subList(0, 25_000)

        val current = SleepScoringConfig()
        val currentCoarseStats = ConfigOptimizer.evaluateConfig(coarseSubset, current)
        val currentTrainStats = ConfigOptimizer.evaluateConfig(trainProfiles, current)
        val currentValidationStats = ConfigOptimizer.evaluateConfig(validationProfiles, current)

        println("=== 1. Configuration actuellement en production ===")
        println(describe(current))
        printFullStats("Entraînement (100 000)", currentTrainStats)
        printFullStats("Validation indépendante (25 000, seed différente)", currentValidationStats)

        fun passesDualConstraint(stats: ConfigStats) =
            stats.falsePositiveRate <= currentTrainStats.falsePositiveRate &&
                stats.missedDetectionRate <= currentTrainStats.missedDetectionRate

        fun filterAndConfirm(candidates: List<SleepScoringConfig>, topN: Int): List<Pair<SleepScoringConfig, ConfigStats>> {
            val coarseSurvivors = candidates
                .map { it to ConfigOptimizer.evaluateConfig(coarseSubset, it) }
                .filter {
                    it.second.falsePositiveRate <= currentCoarseStats.falsePositiveRate * 1.05 &&
                        it.second.missedDetectionRate <= currentCoarseStats.missedDetectionRate * 1.05
                }
                .sortedBy { it.second.averageDelayMinutes }
            val trainSurvivors = coarseSurvivors.take(topN).map { it.first }
                .map { it to ConfigOptimizer.evaluateConfig(trainProfiles, it) }
                .filter { passesDualConstraint(it.second) }
                .sortedBy { it.second.averageDelayMinutes }
            println(
                "  ${coarseSurvivors.size}/${candidates.size} passent le filtre grossier, " +
                    "${trainSurvivors.size}/${minOf(topN, coarseSurvivors.size)} confirment sur les 100 000 nuits d'entraînement"
            )
            return trainSurvivors
        }

        println("\n=== 2. Recherche large (1000 configurations, plages élargies) ===")
        val phase1 = filterAndConfirm(ConfigOptimizer.randomConfigsWide(attempts = 1000, seed = 40_260_816L), topN = 25)

        val phase2Center = phase1.firstOrNull()?.first ?: current
        println("\n=== 3. Affinage fin autour de la meilleure candidate (350 variantes) ===")
        val phase2 = filterAndConfirm(ConfigOptimizer.localRefinements(phase2Center, attempts = 350, seed = 40_260_817L), topN = 12)

        val phase3Center = phase2.firstOrNull()?.first ?: phase2Center
        println("\n=== 4. Second affinage fin (350 variantes) ===")
        val phase3 = filterAndConfirm(ConfigOptimizer.localRefinements(phase3Center, attempts = 350, seed = 40_260_818L), topN = 12)

        val ranked = (phase1 + phase2 + phase3)
            .distinctBy { it.first }
            .sortedBy { it.second.averageDelayMinutes }

        println("\n=== 5. Résultats sur les 100 000 nuits d'optimisation ===")
        if (ranked.isEmpty()) {
            println("Aucune configuration candidate ne respecte la contrainte double (faux positifs ET détections manquées) sur l'entraînement.")
            println("-> La configuration actuelle reste recommandée telle quelle. Rien à valider davantage.")
            return
        }
        val top3 = ranked.take(3)
        top3.forEachIndexed { i, (config, stats) ->
            println("\n--- Rang ${i + 1} ---")
            println(describe(config))
            printFullStats("Entraînement (100 000)", stats)
        }

        println("\n=== 6. Validation indépendante (25 000 nuits, seed différente) ===")
        val top3Validated = top3.map { (config, trainStats) ->
            val validationStats = ConfigOptimizer.evaluateConfig(validationProfiles, config)
            Triple(config, trainStats, validationStats)
        }
        top3Validated.forEachIndexed { i, (config, _, validationStats) ->
            println("\n--- Rang ${i + 1} sur validation ---")
            printFullStats("Validation indépendante (25 000)", validationStats)
        }

        println("\n=== 7. Comparaison avec la baseline ===")
        top3Validated.forEachIndexed { i, (_, trainStats, validationStats) ->
            println(
                "Rang ${i + 1} — délai moyen : ${"%.1f".format(currentTrainStats.averageDelayMinutes)}min (base, train) -> " +
                    "${"%.1f".format(trainStats.averageDelayMinutes)}min (train) / ${"%.1f".format(validationStats.averageDelayMinutes)}min (validation) ; " +
                    "faux positifs : ${"%.2f".format(currentValidationStats.falsePositiveRate * 100)}% (base, val) -> " +
                    "${"%.2f".format(validationStats.falsePositiveRate * 100)}% (val) ; " +
                    "manqués : ${"%.2f".format(currentValidationStats.missedDetectionRate * 100)}% (base, val) -> " +
                    "${"%.2f".format(validationStats.missedDetectionRate * 100)}% (val)"
            )
        }

        println("\n=== 8/9/10. Paramètres modifiés et verdict de généralisation ===")
        top3Validated.forEachIndexed { i, (config, _, validationStats) ->
            val fpHolds = validationStats.falsePositiveRate <= currentValidationStats.falsePositiveRate
            val missedHolds = validationStats.missedDetectionRate <= currentValidationStats.missedDetectionRate
            val verdict = if (fpHolds && missedHolds) "GÉNÉRALISE (confirmé sur données indépendantes)" else "SUR-AJUSTÉE (ne tient pas sur validation)"
            println("Rang ${i + 1} : $verdict — ${describe(config)}")
        }

        println("\nNON APPLIQUÉE AUTOMATIQUEMENT — en attente de validation explicite de Lionel avant toute modification de SleepScoringConfig.kt.")
    }

    private fun describe(config: SleepScoringConfig) =
        "bpmDropBonus=${"%.3f".format(config.bpmDropBonus)} " +
            "bpmDropThreshold=${"%.3f".format(config.bpmDropThreshold)} " +
            "sustainedBpmDropDuration=${config.sustainedBpmDropDuration.seconds}s " +
            "movementThreshold=${"%.2f".format(config.movementThreshold)} " +
            "significantMovementPenalty=${"%.3f".format(config.significantMovementPenalty)} " +
            "tvTurnedOffMultiplier=${"%.2f".format(config.tvTurnedOffMultiplier)}"

    private fun printFullStats(label: String, stats: ConfigStats) {
        println(
            "$label :\n" +
                "  délai moyen=${"%.1f".format(stats.averageDelayMinutes)}min " +
                "médian=${"%.1f".format(stats.medianDelayMinutes)}min " +
                "P95=${stats.p95DelayMinutes}min " +
                "pire=${stats.worstDelayMinutes}min\n" +
                "  fauxPositifs=${stats.falsePositiveCount}/${stats.nightCount} (${"%.2f".format(stats.falsePositiveRate * 100)}%) " +
                "manqués=${stats.missedDetectionCount}/${stats.nightCount} (${"%.2f".format(stats.missedDetectionRate * 100)}%) " +
                "scoreGlobal(perteMoyenne)=${"%.1f".format(stats.meanLoss)}"
        )
    }
}
