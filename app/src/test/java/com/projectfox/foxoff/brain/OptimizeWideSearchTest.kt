package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ConfigOptimizer
import com.projectfox.foxoff.brain.simulation.ConfigStats
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test

/**
 * Test "très lourd" (~8-9 min) — à lancer à la demande via
 * `--tests "*OptimizeWideSearchTest*"`, PAS partie du `./gradlew test`
 * de routine. Voir ROADMAP.md Phase 5.
 *
 * Troisième relance (2026-08-15, demande explicite de Lionel : "plus de
 * tentatives, plages de paramètres différentes... la détection la plus
 * précise et la plus rapide possible"). Les deux runs précédents ont
 * systématiquement retrouvé la MÊME candidate, avec `sustainedBpmDropDuration`
 * au PLANCHER de la plage de recherche (1 min) — signe que l'optimum
 * pourrait être hors de cette plage, jamais exploré faute d'y descendre.
 *
 * Stratégie en 3 phases sur les 100 000 nuits `ProfiledNightGenerator`
 * (même jeu que le run précédent, 10 profils A-J + combinaisons) :
 * 1. **Exploration large** (`ConfigOptimizer.randomConfigsWide`, plages
 *    élargies, granularité à la seconde plutôt qu'à la minute).
 * 2. **Affinage local** autour du meilleur candidat de la phase 1
 *    (`ConfigOptimizer.localRefinements`, perturbation bornée).
 * 3. **Second affinage** autour du meilleur de la phase 2 — pousse plus
 *    loin dans la même direction si elle continue de payer.
 *
 * Contrainte dure identique aux runs précédents (garde-fou explicite,
 * inchangé) : une candidate n'est retenue pour son délai que si son
 * `falsePositiveRate` n'est JAMAIS supérieur à celui de la configuration
 * actuellement en production, à chaque étage (grossier, entraînement
 * complet, validation indépendante). Ne modifie AUCUN fichier de
 * production — rapport imprimé uniquement.
 *
 * Étanchéité vérité-terrain / optimiseur inchangée.
 */
class OptimizeWideSearchTest {

    @Test
    fun `exploration large puis affinage local en quete de la config la plus rapide sans faux positif`() {
        val allProfiles = ProfiledNightGenerator.generate(seed = 20260815L)
        val trainProfiles = allProfiles.subList(0, 80_000)
        val validationProfiles = allProfiles.subList(80_000, 100_000)
        val coarseTrainSubset = trainProfiles.subList(0, 3_000)

        val currentProduction = SleepScoringConfig()
        val currentCoarseStats = ConfigOptimizer.evaluateConfig(coarseTrainSubset, currentProduction)
        val currentTrainStats = ConfigOptimizer.evaluateConfig(trainProfiles, currentProduction)
        val currentValidationStats = ConfigOptimizer.evaluateConfig(validationProfiles, currentProduction)
        val coarseFpThreshold = currentCoarseStats.falsePositiveRate * 1.05
        // Contrainte AJOUTÉE après le run précédent (2026-08-15) : celui-ci
        // n'était contraint que sur les faux positifs, laissant les
        // détections manquées "informatives" — la meilleure candidate
        // trouvée avait alors fait passer les manqués de 11% à 35,6% (plus
        // d'un tiers des nuits jamais mises en pause) pour paraître plus
        // rapide sur le reste. Les DEUX ne doivent plus jamais se dégrader.
        val coarseMissedThreshold = currentCoarseStats.missedDetectionRate * 1.05

        println("=== Configuration actuellement en production (référence) ===")
        printStats("Grossier (3000)", currentCoarseStats)
        printStats("Entraînement (80000)", currentTrainStats)
        printStats("Validation (20000)", currentValidationStats)

        // Filtre + réévaluation complète, réutilisé pour chaque phase.
        fun filterAndConfirm(candidates: List<SleepScoringConfig>, topN: Int): Pair<SleepScoringConfig, ConfigStats>? {
            val coarseSurvivors = candidates
                .map { it to ConfigOptimizer.evaluateConfig(coarseTrainSubset, it) }
                .filter { it.second.falsePositiveRate <= coarseFpThreshold && it.second.missedDetectionRate <= coarseMissedThreshold }
                .sortedBy { it.second.averageDelayMinutes }
            val trainSurvivors = coarseSurvivors.take(topN).map { it.first }
                .map { it to ConfigOptimizer.evaluateConfig(trainProfiles, it) }
                .filter {
                    it.second.falsePositiveRate <= currentTrainStats.falsePositiveRate &&
                        it.second.missedDetectionRate <= currentTrainStats.missedDetectionRate
                }
                .sortedBy { it.second.averageDelayMinutes }
            println("  ${coarseSurvivors.size}/${candidates.size} passent le grossier, ${trainSurvivors.size}/${minOf(topN, coarseSurvivors.size)} confirment sur l'entraînement complet")
            return trainSurvivors.firstOrNull()
        }

        println("\n=== Phase 1 : exploration large (1200 configs, plages élargies) ===")
        val phase1Candidates = ConfigOptimizer.randomConfigsWide(attempts = 1200, seed = 30260815L)
        val phase1Best = filterAndConfirm(phase1Candidates, topN = 25)
        phase1Best?.let { (c, s) -> println("Meilleure phase 1 : ${describe(c)}"); printStats("  entraînement", s) }
            ?: println("Aucune survivante en phase 1.")

        val phase2Center = phase1Best?.first ?: currentProduction
        println("\n=== Phase 2 : affinage local autour de la phase 1 (400 variantes) ===")
        val phase2Candidates = ConfigOptimizer.localRefinements(phase2Center, attempts = 400, seed = 30260816L)
        val phase2Best = filterAndConfirm(phase2Candidates, topN = 12)
        phase2Best?.let { (c, s) -> println("Meilleure phase 2 : ${describe(c)}"); printStats("  entraînement", s) }
            ?: println("Aucune survivante en phase 2.")

        val phase3Center = phase2Best?.first ?: phase2Center
        println("\n=== Phase 3 : second affinage autour de la phase 2 (400 variantes) ===")
        val phase3Candidates = ConfigOptimizer.localRefinements(phase3Center, attempts = 400, seed = 30260817L)
        val phase3Best = filterAndConfirm(phase3Candidates, topN = 12)
        phase3Best?.let { (c, s) -> println("Meilleure phase 3 : ${describe(c)}"); printStats("  entraînement", s) }
            ?: println("Aucune survivante en phase 3.")

        // Le meilleur des 3 phases (+ config actuelle en repli), classé par délai d'entraînement.
        val allCandidatesFound = listOfNotNull(
            currentProduction to currentTrainStats,
            phase1Best,
            phase2Best,
            phase3Best
        ).sortedBy { it.second.averageDelayMinutes }

        val (bestConfig, bestTrainStats) = allCandidatesFound.first()

        if (bestConfig == currentProduction) {
            println("\n=== Verdict ===")
            println("Aucune des 3 phases n'a battu la configuration actuelle en respectant les contraintes (faux positifs ET détections manquées).")
            println("-> La configuration actuelle reste recommandée telle quelle.")
            return
        }

        val bestValidationStats = ConfigOptimizer.evaluateConfig(validationProfiles, bestConfig)
        println("\n=== Meilleure candidate toutes phases confondues : validation sur 20 000 nuits indépendantes ===")
        println(describe(bestConfig))
        printStats("Entraînement (80000)", bestTrainStats)
        printStats("Validation (20000)", bestValidationStats)

        val fpHolds = bestValidationStats.falsePositiveRate <= currentValidationStats.falsePositiveRate
        val missedHolds = bestValidationStats.missedDetectionRate <= currentValidationStats.missedDetectionRate
        val holdsOnValidation = fpHolds && missedHolds

        println("\n=== Verdict ===")
        if (!holdsOnValidation) {
            val raisons = buildList {
                if (!fpHolds) add(
                    "faux positifs sur validation (${"%.2f".format(bestValidationStats.falsePositiveRate * 100)}%) " +
                        "supérieurs à l'actuelle (${"%.2f".format(currentValidationStats.falsePositiveRate * 100)}%)"
                )
                if (!missedHolds) add(
                    "détections manquées sur validation (${"%.2f".format(bestValidationStats.missedDetectionRate * 100)}%) " +
                        "supérieures à l'actuelle (${"%.2f".format(currentValidationStats.missedDetectionRate * 100)}%)"
                )
            }
            println("REJETÉE (sur-ajustement) : ${raisons.joinToString("; ")}.")
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
            "sustainedBpmDropDuration=${config.sustainedBpmDropDuration.seconds}s " +
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
