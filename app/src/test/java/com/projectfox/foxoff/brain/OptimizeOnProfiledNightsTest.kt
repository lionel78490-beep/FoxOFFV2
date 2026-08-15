package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ConfigOptimizer
import com.projectfox.foxoff.brain.simulation.ConfigStats
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test

/**
 * Test "très lourd" (~5-8 min) — à lancer à la demande via
 * `-PheavyTests --tests "*OptimizeOnProfiledNightsTest*"` (exclu par
 * défaut du `./gradlew test` de routine depuis app/build.gradle.kts,
 * 2026-08-16), PAS partie du `./gradlew
 * test` de routine. Voir ROADMAP.md Phase 5 — même méthode que
 * `OptimizeSleepScoringConfigTest` (2026-08-15 matin, jeu de 10 000
 * nuits `ProceduralNightGenerator`), rejouée ici sur le jeu plus riche et
 * plus dur de 100 000 nuits (`ProfiledNightGenerator`, 10 profils A-J +
 * combinaisons, demandé par Lionel le même jour) pour vérifier si la
 * config déjà appliquée ce matin tient toujours, ou si une meilleure
 * existe sur ce jeu plus exigeant.
 *
 * Ne modifie AUCUN fichier de production : rapport imprimé uniquement,
 * jamais appliqué automatiquement.
 *
 * Contrainte dure identique à la version corrigée du run précédent
 * (2026-08-15) : une candidate n'est considérée pour son délai que si son
 * `falsePositiveRate` (PAS un total combiné avec les détections manquées
 * — piège déjà découvert et corrigé le même jour) n'est JAMAIS supérieur
 * à celui de la config actuellement en production, à chaque étage
 * (grossier, entraînement complet, validation indépendante).
 *
 * Étanchéité vérité-terrain / optimiseur inchangée : `ProfiledNightGenerator`
 * génère les 100 000 profils UNE SEULE FOIS ci-dessous, avant toute
 * recherche, jamais ajustés ensuite.
 */
class OptimizeOnProfiledNightsTest {

    @Test
    fun `recherche sur les 100 000 nuits profilees la config la plus rapide sans degrader les faux positifs`() {
        val allProfiles = ProfiledNightGenerator.generate(seed = 20260815L)
        val trainProfiles = allProfiles.subList(0, 80_000)
        val validationProfiles = allProfiles.subList(80_000, 100_000)
        val coarseTrainSubset = trainProfiles.subList(0, 3_000)

        val currentProduction = SleepScoringConfig() // déjà les valeurs appliquées ce matin
        val currentCoarseStats = ConfigOptimizer.evaluateConfig(coarseTrainSubset, currentProduction)
        val currentTrainStats = ConfigOptimizer.evaluateConfig(trainProfiles, currentProduction)
        val currentValidationStats = ConfigOptimizer.evaluateConfig(validationProfiles, currentProduction)

        println("=== Configuration actuellement en production (référence) ===")
        printStats("Grossier (3000)", currentCoarseStats)
        printStats("Entraînement (80000)", currentTrainStats)
        printStats("Validation (20000)", currentValidationStats)

        // --- Étage 1 : filtre grossier, tolérance 5% (échantillon de 3000
        // nuits, plus grand qu'hier car les taux d'erreur sont mesurés sur
        // un jeu plus varié — nécessaire pour rester statistiquement fiable). ---
        val candidates = ConfigOptimizer.randomConfigs(attempts = 1200, seed = 20260815L)
        val coarseThreshold = currentCoarseStats.falsePositiveRate * 1.05
        val coarseSurvivors = candidates
            .map { it to ConfigOptimizer.evaluateConfig(coarseTrainSubset, it) }
            .filter { it.second.falsePositiveRate <= coarseThreshold }
            .sortedBy { it.second.averageDelayMinutes }

        println(
            "\n=== Étage 1 (3000 nuits) : ${coarseSurvivors.size}/${candidates.size} configs " +
                "ne dégradent pas le taux de FAUX POSITIFS (seuil ${"%.2f".format(coarseThreshold * 100)}%) ==="
        )

        if (coarseSurvivors.isEmpty()) {
            println("\nAUCUNE candidate ne passe même le filtre grossier.")
            println("-> La configuration actuelle reste recommandée telle quelle.")
            return
        }

        // --- Étage 2 : les 40 meilleures survivantes réévaluées sur les
        // 80 000 nuits d'entraînement complètes, contrainte STRICTE. ---
        val top40 = coarseSurvivors.take(40).map { it.first }
        val trainSurvivors = top40
            .map { it to ConfigOptimizer.evaluateConfig(trainProfiles, it) }
            .filter { it.second.falsePositiveRate <= currentTrainStats.falsePositiveRate }
            .sortedBy { it.second.averageDelayMinutes }

        println(
            "\n=== Étage 2 (80000 nuits, contrainte stricte sur les faux positifs) : " +
                "${trainSurvivors.size}/${top40.size} confirment sur l'entraînement complet ==="
        )
        trainSurvivors.take(5).forEachIndexed { i, (config, stats) ->
            println("#${i + 1} ${describe(config)}")
            printStats("  entraînement", stats)
        }

        if (trainSurvivors.isEmpty()) {
            println("\nAucune des 40 meilleures candidates grossières ne tient la contrainte stricte sur l'entraînement complet.")
            println("-> La configuration actuelle reste recommandée telle quelle.")
            return
        }

        // --- Étage 3 : la meilleure survivante, validée sur les 20 000
        // nuits indépendantes — même contrainte stricte. ---
        val (bestConfig, bestTrainStats) = trainSurvivors.first()
        val bestValidationStats = ConfigOptimizer.evaluateConfig(validationProfiles, bestConfig)

        println("\n=== Étage 3 : meilleure candidate validée sur 20 000 nuits indépendantes ===")
        println(describe(bestConfig))
        printStats("Entraînement (80000)", bestTrainStats)
        printStats("Validation (20000)", bestValidationStats)

        val holdsOnValidation = bestValidationStats.falsePositiveRate <= currentValidationStats.falsePositiveRate

        println("\n=== Verdict ===")
        if (!holdsOnValidation) {
            println(
                "REJETÉE (sur-ajustement) : taux de FAUX POSITIFS sur validation " +
                    "(${"%.2f".format(bestValidationStats.falsePositiveRate * 100)}%) supérieur à l'actuelle " +
                    "(${"%.2f".format(currentValidationStats.falsePositiveRate * 100)}%)."
            )
            println("-> AUCUNE proposition retenue. La configuration actuelle reste recommandée telle quelle.")
        } else {
            println("PROPOSITION VALIDÉE — délai réduit, taux de faux positifs JAMAIS dégradé (grossier, entraînement, validation) :")
            println(describe(bestConfig))
            println(
                "Délai moyen vs actuelle (validation) : " +
                    "${"%.1f".format(currentValidationStats.averageDelayMinutes)}min -> " +
                    "${"%.1f".format(bestValidationStats.averageDelayMinutes)}min"
            )
            println(
                "Faux positifs vs actuelle (validation) : " +
                    "${"%.2f".format(currentValidationStats.falsePositiveRate * 100)}% -> " +
                    "${"%.2f".format(bestValidationStats.falsePositiveRate * 100)}%"
            )
            println(
                "Détections manquées vs actuelle (validation, informatif, non contraint) : " +
                    "${"%.2f".format(currentValidationStats.missedDetectionRate * 100)}% -> " +
                    "${"%.2f".format(bestValidationStats.missedDetectionRate * 100)}%"
            )
            println("NON APPLIQUÉE AUTOMATIQUEMENT — proposition à relire avant toute modification de SleepScoringConfig.kt.")
        }
    }

    private fun describe(config: SleepScoringConfig) =
        "bpmDropBonus=${"%.3f".format(config.bpmDropBonus)} " +
            "bpmDropThreshold=${"%.3f".format(config.bpmDropThreshold)} " +
            "sustainedBpmDropDuration=${config.sustainedBpmDropDuration.toMinutes()}min " +
            "movementThreshold=${"%.2f".format(config.movementThreshold)} " +
            "significantMovementPenalty=${"%.3f".format(config.significantMovementPenalty)} " +
            "tvTurnedOffMultiplier=${"%.2f".format(config.tvTurnedOffMultiplier)}"

    private fun printStats(label: String, stats: ConfigStats) {
        println(
            "$label : délai moyen=${"%.1f".format(stats.averageDelayMinutes)}min " +
                "pire=${stats.worstDelayMinutes}min " +
                "fauxPositifs=${stats.falsePositiveCount}/${stats.nightCount} (${"%.2f".format(stats.falsePositiveRate * 100)}%) " +
                "manqués=${stats.missedDetectionCount}/${stats.nightCount} (${"%.2f".format(stats.missedDetectionRate * 100)}%)"
        )
    }
}
