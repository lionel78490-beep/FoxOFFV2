package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ConfigOptimizer
import com.projectfox.foxoff.brain.simulation.ConfigStats
import com.projectfox.foxoff.brain.simulation.ProceduralNightGenerator
import org.junit.Test

/**
 * Test "lourd" (~1-3 min) — à lancer à la demande via
 * `--tests "*OptimizeSleepScoringConfigTest*"`, PAS partie du
 * `./gradlew test` de routine. Voir plan 2026-08-15 ("10 000 nuits —
 * framework d'optimisation de SleepScoringConfig"), motivé par le retard
 * de 6h08 constaté sur une vraie nuit ce jour-là (voir `RealNightReplayTest`).
 *
 * Ne modifie AUCUN fichier de production : génère uniquement un rapport
 * comparatif imprimé sur la sortie du test — jamais appliqué
 * automatiquement à `SleepScoringConfig.kt`.
 *
 * **Deuxième run (2026-08-15)** : le premier run (recherche par perte
 * pondérée) avait trouvé une candidate 2,4× plus rapide mais REJETÉE pour
 * plus de faux positifs sur validation. Lionel a demandé une contrainte
 * plus stricte.
 *
 * **Troisième run (même jour, correction)** : une première tentative de
 * contrainte dure sur `pauseErrorRate` (faux positifs + détections
 * manquées combinés) s'est révélée trompeuse — elle laissait passer des
 * candidates qui ÉCHANGEAIENT une baisse des détections manquées contre
 * une explosion des faux positifs (jusqu'à ×7 par rapport à la
 * production) tout en gardant un total combiné similaire. Pas une
 * découverte sur l'algorithme, un défaut de la métrique elle-même : un
 * total combiné masque ce genre d'échange. Corrigé ici : la contrainte
 * dure porte spécifiquement sur `falsePositiveRate` (jamais dégradée, à
 * CHAQUE étage — grossier, entraînement complet, validation indépendante)
 * puisque c'est la priorité explicite de Lionel ("minimiser drastiquement
 * les faux positifs et les pauses prématurées") ; `missedDetectionRate`
 * reste affichée pour information mais n'est pas contrainte — la baisser
 * est toujours un bonus, jamais un risque à encadrer. Parmi les
 * survivantes (faux positifs ≤ production), la plus rapide gagne. S'il
 * n'en existe aucune, le test le dit clairement plutôt que de proposer un
 * compromis dégradé.
 *
 * Étanchéité vérité-terrain / optimiseur (garde-fou explicite du plan) :
 * les 10 000 profils sont générés UNE SEULE FOIS ci-dessous (seed fixe),
 * avant toute recherche de configuration, et ne sont plus jamais modifiés
 * ensuite — `ConfigOptimizer` ne fait qu'évaluer des `SleepScoringConfig`
 * contre cette liste figée.
 */
class OptimizeSleepScoringConfigTest {

    @Test
    fun `recherche la config la plus rapide sans jamais degrader le taux d'erreur de mise en pause`() {
        val allProfiles = ProceduralNightGenerator.generate(count = 10_000, seed = 20260815L)
        val trainProfiles = allProfiles.subList(0, 8000)
        val validationProfiles = allProfiles.subList(8000, 10_000)
        val coarseTrainSubset = trainProfiles.subList(0, 1000)

        val production = SleepScoringConfig()
        val productionCoarseStats = ConfigOptimizer.evaluateConfig(coarseTrainSubset, production)
        val productionTrainStats = ConfigOptimizer.evaluateConfig(trainProfiles, production)
        val productionValidationStats = ConfigOptimizer.evaluateConfig(validationProfiles, production)

        println("=== Configuration de production (référence) ===")
        printStats("Grossier (1000)", productionCoarseStats)
        printStats("Entraînement (8000)", productionTrainStats)
        printStats("Validation (2000)", productionValidationStats)

        // --- Étage 1 : filtre grossier sur 1000 nuits, 2000 configs tirées
        // (recherche plus large que le premier run — la contrainte dure
        // rejette beaucoup plus de candidates, il faut plus de tentatives
        // pour espérer trouver des survivantes). Tolérance de 5% sur le
        // seuil ici UNIQUEMENT parce que l'échantillon de 1000 nuits est
        // trop petit pour mesurer un taux d'erreur avec précision (bruit
        // d'échantillonnage) — resserré à zéro tolérance à l'étage suivant.
        val candidates = ConfigOptimizer.randomConfigs(attempts = 2000, seed = 20260815L)
        val coarseThreshold = productionCoarseStats.falsePositiveRate * 1.05
        val coarseSurvivors = candidates
            .map { it to ConfigOptimizer.evaluateConfig(coarseTrainSubset, it) }
            .filter { it.second.falsePositiveRate <= coarseThreshold }
            .sortedBy { it.second.averageDelayMinutes }

        println(
            "\n=== Étage 1 (1000 nuits) : ${coarseSurvivors.size}/${candidates.size} configs " +
                "ne dégradent pas le taux de FAUX POSITIFS (seuil ${"%.2f".format(coarseThreshold * 100)}%) ==="
        )

        if (coarseSurvivors.isEmpty()) {
            println("\nAUCUNE candidate ne passe même le filtre grossier.")
            println("-> La configuration de production reste recommandée telle quelle.")
            return
        }

        // --- Étage 2 : les 50 meilleures survivantes réévaluées sur les
        // 8000 nuits d'entraînement complètes, contrainte STRICTE (aucune
        // tolérance — échantillon assez grand pour une mesure fiable).
        val top50 = coarseSurvivors.take(50).map { it.first }
        val trainSurvivors = top50
            .map { it to ConfigOptimizer.evaluateConfig(trainProfiles, it) }
            .filter { it.second.falsePositiveRate <= productionTrainStats.falsePositiveRate }
            .sortedBy { it.second.averageDelayMinutes }

        println(
            "\n=== Étage 2 (8000 nuits, contrainte stricte sur les faux positifs) : " +
                "${trainSurvivors.size}/${top50.size} confirment sur l'entraînement complet ==="
        )
        trainSurvivors.take(5).forEachIndexed { i, (config, stats) ->
            println("#${i + 1} ${describe(config)}")
            printStats("  entraînement", stats)
        }

        if (trainSurvivors.isEmpty()) {
            println("\nAucune des 50 meilleures candidates grossières ne tient la contrainte stricte sur l'entraînement complet.")
            println("-> La configuration de production reste recommandée telle quelle.")
            return
        }

        // --- Étage 3 : la meilleure survivante, validée sur les 2000 nuits
        // indépendantes — même contrainte stricte, jamais relâchée.
        val (bestConfig, bestTrainStats) = trainSurvivors.first()
        val bestValidationStats = ConfigOptimizer.evaluateConfig(validationProfiles, bestConfig)

        println("\n=== Étage 3 : meilleure candidate validée sur 2000 nuits indépendantes ===")
        println(describe(bestConfig))
        printStats("Entraînement (8000)", bestTrainStats)
        printStats("Validation (2000)", bestValidationStats)

        val holdsOnValidation = bestValidationStats.falsePositiveRate <= productionValidationStats.falsePositiveRate

        println("\n=== Verdict ===")
        if (!holdsOnValidation) {
            println(
                "REJETÉE (sur-ajustement) : taux de FAUX POSITIFS sur validation " +
                    "(${"%.2f".format(bestValidationStats.falsePositiveRate * 100)}%) supérieur à la production " +
                    "(${"%.2f".format(productionValidationStats.falsePositiveRate * 100)}%)."
            )
            println("-> AUCUNE proposition retenue. La configuration de production reste recommandée telle quelle.")
        } else {
            println("PROPOSITION VALIDÉE — délai réduit, taux de faux positifs JAMAIS dégradé (grossier, entraînement, validation) :")
            println(describe(bestConfig))
            println(
                "Délai moyen vs production (validation) : " +
                    "${"%.1f".format(productionValidationStats.averageDelayMinutes)}min -> " +
                    "${"%.1f".format(bestValidationStats.averageDelayMinutes)}min"
            )
            println(
                "Faux positifs vs production (validation) : " +
                    "${"%.2f".format(productionValidationStats.falsePositiveRate * 100)}% -> " +
                    "${"%.2f".format(bestValidationStats.falsePositiveRate * 100)}%"
            )
            println(
                "Détections manquées vs production (validation, informatif, non contraint) : " +
                    "${"%.2f".format(productionValidationStats.missedDetectionRate * 100)}% -> " +
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
                "manqués=${stats.missedDetectionCount}/${stats.nightCount} (${"%.2f".format(stats.missedDetectionRate * 100)}%) " +
                "erreurMiseEnPause=${"%.2f".format(stats.pauseErrorRate * 100)}%"
        )
    }
}
