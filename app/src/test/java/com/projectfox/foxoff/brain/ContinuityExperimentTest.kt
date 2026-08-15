package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ContinuityExperimentEngine
import com.projectfox.foxoff.brain.simulation.ContinuityStrategies
import com.projectfox.foxoff.brain.simulation.ContinuityStrategy
import com.projectfox.foxoff.brain.simulation.ExperimentalNightResult
import com.projectfox.foxoff.brain.simulation.NightProfile
import com.projectfox.foxoff.brain.simulation.NightSimulator
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test
import java.time.Duration

/**
 * Expérience "logique de continuité BPM" (2026-08-16) — 7 stratégies
 * testées via `ContinuityExperimentEngine` (moteur PARALLÈLE, jamais
 * FoxBrain/WeightedSleepAnalyzer/FoxCore réels — voir sa documentation).
 * Mêmes 125 000 nuits, même config de référence (valeurs de production
 * actuelles) que toutes les expériences précédentes.
 *
 * AUCUNE modification de production. Objectif unique : déterminer si une
 * stratégie de continuité améliore SIMULTANÉMENT faux positifs et
 * détections manquées (par rapport à la stratégie 1, qui reproduit la
 * production) tout en réduisant fortement le délai — sinon, conclure que
 * le problème nécessite une approche différente.
 *
 * Test "très lourd" (~2-4 min) — exclu du `./gradlew test` de routine, à
 * lancer via `-PheavyTests --tests "*ContinuityExperimentTest*"`.
 */
class ContinuityExperimentTest {

    private data class Classified(
        val profile: NightProfile,
        val result: ExperimentalNightResult,
        val delayMinutes: Int?,
        val isFalsePositive: Boolean,
        val isMissed: Boolean
    )

    private data class Stats(
        val n: Int,
        val avgDelay: Double,
        val medianDelay: Double,
        val p95Delay: Int,
        val worstDelay: Int,
        val fpCount: Int,
        val fpRate: Double,
        val missedCount: Int,
        val missedRate: Double
    )

    private fun classify(profile: NightProfile, config: SleepScoringConfig, strategy: ContinuityStrategy): Classified {
        val result = ContinuityExperimentEngine.run(profile, config, strategy)
        val truth = profile.groundTruthAsleepMinute
        val pauseMinute = result.pauseTriggeredAt?.let {
            Duration.between(NightSimulator.NIGHT_START, it).toMinutes().toInt()
        }
        return when {
            truth == null && pauseMinute != null -> Classified(profile, result, null, true, false)
            truth == null -> Classified(profile, result, null, false, false)
            pauseMinute != null && pauseMinute < truth -> Classified(profile, result, null, true, false)
            pauseMinute == null -> Classified(profile, result, null, false, true)
            else -> Classified(profile, result, pauseMinute - truth, false, false)
        }
    }

    private fun stats(group: List<Classified>): Stats {
        val delays = group.mapNotNull { it.delayMinutes }.sorted()
        fun percentile(p: Double) = if (delays.isEmpty()) -1 else delays[(p * (delays.size - 1)).toInt().coerceIn(0, delays.size - 1)]
        return Stats(
            n = group.size,
            avgDelay = if (delays.isNotEmpty()) delays.average() else Double.NaN,
            medianDelay = if (delays.isNotEmpty()) percentile(0.50).toDouble() else Double.NaN,
            p95Delay = percentile(0.95),
            worstDelay = delays.maxOrNull() ?: -1,
            fpCount = group.count { it.isFalsePositive },
            fpRate = group.count { it.isFalsePositive } * 100.0 / group.size,
            missedCount = group.count { it.isMissed },
            missedRate = group.count { it.isMissed } * 100.0 / group.size
        )
    }

    private fun printStats(label: String, s: Stats) {
        println(
            "  [$label] n=${s.n} délai(moy/méd/P95/pire)=${"%.1f".format(s.avgDelay)}/${"%.1f".format(s.medianDelay)}/${s.p95Delay}/${s.worstDelay}min " +
                "FP=${s.fpCount}(${"%.2f".format(s.fpRate)}%) manqués=${s.missedCount}(${"%.2f".format(s.missedRate)}%)"
        )
    }

    @Test
    fun `experience de continuite BPM sur 7 strategies, moteur parallele au simulateur`() {
        println("=== Chargement des mêmes 125 000 nuits ===")
        val train = ProfiledNightGenerator.generate(seed = 20260815L)
        val validation = ProfiledNightGenerator.generate(seed = 88_888_888L).subList(0, 25_000)
        val all = train + validation

        fun hasLetters(profile: NightProfile, vararg letters: String): Boolean {
            val parts = profile.profileLabel.split("+")
            return letters.all { it in parts }
        }
        val subsets = linkedMapOf(
            "GLOBAL" to all,
            "E" to all.filter { hasLetters(it, "E") },
            "H" to all.filter { hasLetters(it, "H") },
            "E+H" to all.filter { hasLetters(it, "E", "H") },
            "D+H" to all.filter { hasLetters(it, "D", "H") },
            "D+F+H" to all.filter { hasLetters(it, "D", "F", "H") }
        )

        val config = SleepScoringConfig() // valeurs de production actuelles, inchangées

        // HashSet plutôt qu'un `.any { }` linéaire — indispensable à 125 000
        // nuits × 6 sous-ensembles, sinon comparaison O(n×m) bien trop lente.
        val subsetSets = subsets.mapValues { (_, profiles) -> profiles.toHashSet() }

        var baselineGlobal: Stats? = null
        var baselinePerSubset: Map<String, Stats>? = null

        val allStrategyResults = ContinuityStrategies.all().map { strategy ->
            println("\n\n########## Stratégie : ${strategy.label} ##########")
            val classified = all.map { classify(it, config, strategy) }

            val perSubset = subsetSets.mapValues { (_, profileSet) ->
                stats(classified.filter { it.profile in profileSet })
            }
            perSubset.forEach { (name, s) -> printStats(name, s) }

            if (strategy === ContinuityStrategies.ResetImmediat) {
                baselineGlobal = perSubset.getValue("GLOBAL")
                baselinePerSubset = perSubset
            }

            val worstDelays = classified.filter { it.delayMinutes != null }.sortedByDescending { it.delayMinutes }.take(100)
            val worstFalsePositives = classified.filter { it.isFalsePositive }.take(100)
            println("  Top 100 pires délais : ${worstDelays.lastOrNull()?.delayMinutes ?: "—"}-${worstDelays.firstOrNull()?.delayMinutes ?: "—"}min, profils dominants : " +
                listOf("E", "H", "D", "F", "G").joinToString(", ") { letter ->
                    "$letter=${worstDelays.count { hasLetters(it.profile, letter) }}%"
                }
            )
            println("  Échantillon faux positifs (n=${classified.count { it.isFalsePositive }}, ${worstFalsePositives.size} listés) — profils dominants : " +
                listOf("E", "H", "D", "F", "G").joinToString(", ") { letter ->
                    "$letter=${worstFalsePositives.count { hasLetters(it.profile, letter) }}%"
                }
            )

            Triple(strategy, perSubset, classified)
        }

        // === Sanity check : la stratégie 1 doit reproduire la production ===
        println("\n\n########## Vérification de fidélité du moteur expérimental ##########")
        println("Stratégie 1 (reset immédiat) — doit être proche des chiffres réels de production (délai moyen=30,9 P95=156 pire=468 FP=2,04% manqués=11,01%) :")
        baselineGlobal?.let { printStats("GLOBAL (stratégie 1)", it) }

        // === Verdict : une stratégie améliore-t-elle FP ET manqués simultanément vs baseline ? ===
        println("\n\n########## VERDICT : critère = FP global <= baseline ET manqués global <= baseline ET délai réduit ##########")
        val base = baselineGlobal!!
        val baseH = baselinePerSubset!!.getValue("H")
        var anyWins = false
        allStrategyResults.forEach { (strategy, perSubset, _) ->
            val g = perSubset.getValue("GLOBAL")
            val h = perSubset.getValue("H")
            val meetsConstraint = g.fpRate <= base.fpRate && g.missedRate <= base.missedRate
            val delayImproved = g.avgDelay < base.avgDelay
            val hImproved = h.avgDelay < baseH.avgDelay
            val verdict = if (meetsConstraint && delayImproved) "CANDIDATE VIABLE" else "REJETÉE"
            if (meetsConstraint && delayImproved) anyWins = true
            println(
                "${strategy.label} : FP=${"%.2f".format(g.fpRate)}% (base ${"%.2f".format(base.fpRate)}%) " +
                    "manqués=${"%.2f".format(g.missedRate)}% (base ${"%.2f".format(base.missedRate)}%) " +
                    "délai=${"%.1f".format(g.avgDelay)}min (base ${"%.1f".format(base.avgDelay)}min) " +
                    "délai_H=${"%.1f".format(h.avgDelay)}min (base ${"%.1f".format(baseH.avgDelay)}min, ${if (hImproved) "amélioré" else "pas amélioré"}) -> $verdict"
            )
        }

        println("\n=== CONCLUSION ===")
        if (anyWins) {
            println("Au moins une stratégie de continuité respecte le critère (voir ci-dessus) — à examiner en détail avant toute décision.")
        } else {
            println("AUCUNE stratégie de continuité testée n'améliore simultanément les faux positifs ET les détections manquées par rapport au reset immédiat actuel.")
            println("-> Conclusion : le problème ne se résout pas par un ajustement de la logique de continuité seule. Aucune modification de production proposée.")
        }
    }
}
