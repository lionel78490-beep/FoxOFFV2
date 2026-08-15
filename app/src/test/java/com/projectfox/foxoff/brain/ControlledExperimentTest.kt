package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ConfigOptimizer
import com.projectfox.foxoff.brain.simulation.ConfigStats
import com.projectfox.foxoff.brain.simulation.NightProfile
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test
import java.time.Duration

/**
 * Expérience CONTRÔLÉE (2026-08-16, demande explicite de Lionel suite à
 * l'analyse diagnostique) — teste isolément deux paramètres identifiés
 * comme mécanismes probables (voir ROADMAP.md Phase 5) :
 * - Expérience A : `sustainedBpmDropDuration` seul (60/120/180/240s).
 * - Expérience B : `bpmDropThreshold` seul (0.070 à 0.120).
 * - Expérience C : meilleure valeur de A + meilleure valeur de B combinées.
 * Tous les autres paramètres restent à leur valeur de production actuelle.
 * Mêmes 125 000 nuits (100k train seed=20260815L + 25k validation
 * seed=88 888 888L) que la baseline et l'analyse diagnostique — comparaison
 * strictement contrôlée.
 *
 * AUCUNE sélection automatique de configuration finale, AUCUNE modification
 * de fichier de production — uniquement un tableau comparatif chiffré,
 * séparant systématiquement résultats globaux et résultats par profil
 * (E, H, E+H, E+I, D+H, D+F+H).
 *
 * Test "très lourd" (~2-3 min) — exclu du `./gradlew test` de routine, à
 * lancer via `-PheavyTests --tests "*ControlledExperimentTest*"`.
 */
class ControlledExperimentTest {

    @Test
    fun `experience controlee A B C sur sustainedBpmDropDuration et bpmDropThreshold`() {
        println("=== Chargement des mêmes 125 000 nuits que la baseline et l'analyse diagnostique ===")
        val train = ProfiledNightGenerator.generate(seed = 20260815L)
        val validation = ProfiledNightGenerator.generate(seed = 88_888_888L).subList(0, 25_000)
        val all = train + validation

        fun hasLetters(profile: NightProfile, vararg letters: String): Boolean {
            val parts = profile.profileLabel.split("+")
            return letters.all { it in parts }
        }

        val subsets = linkedMapOf(
            "GLOBAL (125 000)" to all,
            "Profil E seul (toutes nuits contenant E)" to all.filter { hasLetters(it, "E") },
            "Profil H seul (toutes nuits contenant H)" to all.filter { hasLetters(it, "H") },
            "E+H" to all.filter { hasLetters(it, "E", "H") },
            "E+I" to all.filter { hasLetters(it, "E", "I") },
            "D+H" to all.filter { hasLetters(it, "D", "H") },
            "D+F+H" to all.filter { hasLetters(it, "D", "F", "H") }
        )
        println("Tailles des sous-ensembles : " + subsets.entries.joinToString(" | ") { "${it.key}=${it.value.size}" })

        data class Candidate(val label: String, val config: SleepScoringConfig)

        val production = SleepScoringConfig()
        val expA = listOf(60L, 120L, 180L, 240L).map { s ->
            Candidate("A: durée=${s}s", production.copy(sustainedBpmDropDuration = Duration.ofSeconds(s)))
        }
        val expB = listOf(0.070f, 0.080f, 0.090f, 0.100f, 0.110f, 0.120f).map { t ->
            Candidate("B: seuil=${"%.3f".format(t)}", production.copy(bpmDropThreshold = t))
        }

        fun evaluateAll(candidates: List<Candidate>): Map<String, Map<String, ConfigStats>> =
            candidates.associate { c ->
                c.label to subsets.mapValues { (_, profiles) -> ConfigOptimizer.evaluateConfig(profiles, c.config) }
            }

        fun printResults(title: String, results: Map<String, Map<String, ConfigStats>>) {
            println("\n\n########## $title ##########")
            results.forEach { (label, bySubset) ->
                println("\n--- $label ---")
                bySubset.forEach { (subsetName, stats) ->
                    println(
                        "  [$subsetName] n=${stats.nightCount} " +
                            "délai(moy/méd/P95/pire)=${"%.1f".format(stats.averageDelayMinutes)}/" +
                            "${"%.1f".format(stats.medianDelayMinutes)}/${stats.p95DelayMinutes}/${stats.worstDelayMinutes}min " +
                            "FP=${stats.falsePositiveCount}(${"%.2f".format(stats.falsePositiveRate * 100)}%) " +
                            "manqués=${stats.missedDetectionCount}(${"%.2f".format(stats.missedDetectionRate * 100)}%) " +
                            "score=${"%.1f".format(stats.meanLoss)}"
                    )
                }
            }
        }

        println("\n=== Référence : configuration production actuelle (durée=60s, seuil=0,070) ===")
        val productionResults = subsets.mapValues { (_, profiles) -> ConfigOptimizer.evaluateConfig(profiles, production) }
        productionResults.forEach { (subsetName, stats) ->
            println(
                "  [$subsetName] n=${stats.nightCount} " +
                    "délai(moy/méd/P95/pire)=${"%.1f".format(stats.averageDelayMinutes)}/" +
                    "${"%.1f".format(stats.medianDelayMinutes)}/${stats.p95DelayMinutes}/${stats.worstDelayMinutes}min " +
                    "FP=${stats.falsePositiveCount}(${"%.2f".format(stats.falsePositiveRate * 100)}%) " +
                    "manqués=${stats.missedDetectionCount}(${"%.2f".format(stats.missedDetectionRate * 100)}%) " +
                    "score=${"%.1f".format(stats.meanLoss)}"
            )
        }

        val resultsA = evaluateAll(expA)
        printResults("EXPÉRIENCE A — sustainedBpmDropDuration seul (tout le reste = production)", resultsA)

        val resultsB = evaluateAll(expB)
        printResults("EXPÉRIENCE B — bpmDropThreshold seul (tout le reste = production)", resultsB)

        // --- Sélection des "meilleures" valeurs de A et B, UNIQUEMENT pour
        // construire l'entrée de l'Expérience C — règle explicite, imprimée,
        // PAS une recommandation de production (demande explicite de Lionel :
        // ne rien sélectionner automatiquement pour la production). ---
        println("\n\n########## Sélection des entrées pour l'Expérience C ##########")
        println("Règle A : durée minimisant le taux de FAUX POSITIFS sur le sous-ensemble E, parmi les durées qui n'augmentent pas les manqués globaux de plus de 2 points vs production.")
        val bestA = expA.filter { c ->
            val globalMissed = resultsA.getValue(c.label).getValue("GLOBAL (125 000)").missedDetectionRate
            globalMissed <= productionResults.getValue("GLOBAL (125 000)").missedDetectionRate + 0.02
        }.minByOrNull { resultsA.getValue(it.label).getValue("Profil E seul (toutes nuits contenant E)").falsePositiveRate }
            ?: expA.first()
        println("-> Retenu pour C : ${bestA.label} (FP profil E = ${"%.2f".format(resultsA.getValue(bestA.label).getValue("Profil E seul (toutes nuits contenant E)").falsePositiveRate * 100)}%)")

        println("\nRègle B : seuil minimisant le P95 du délai sur le sous-ensemble H, parmi les seuils qui n'augmentent pas les faux positifs globaux vs production.")
        val bestB = expB.filter { c ->
            val globalFp = resultsB.getValue(c.label).getValue("GLOBAL (125 000)").falsePositiveRate
            globalFp <= productionResults.getValue("GLOBAL (125 000)").falsePositiveRate
        }.minByOrNull { resultsB.getValue(it.label).getValue("Profil H seul (toutes nuits contenant H)").p95DelayMinutes }
            ?: expB.first()
        println("-> Retenu pour C : ${bestB.label} (P95 profil H = ${resultsB.getValue(bestB.label).getValue("Profil H seul (toutes nuits contenant H)").p95DelayMinutes}min)")

        val durationForC = (bestA.config.sustainedBpmDropDuration)
        val thresholdForC = bestB.config.bpmDropThreshold
        val candidateC = Candidate(
            "C: durée=${durationForC.seconds}s + seuil=${"%.3f".format(thresholdForC)}",
            production.copy(sustainedBpmDropDuration = durationForC, bpmDropThreshold = thresholdForC)
        )
        val resultsC = evaluateAll(listOf(candidateC))
        printResults("EXPÉRIENCE C — combinaison ciblée (meilleure durée de A + meilleur seuil de B)", resultsC)

        println("\n\n=== FIN — aucune configuration sélectionnée automatiquement, aucun fichier de production modifié ===")
    }
}
