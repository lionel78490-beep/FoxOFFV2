package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ContextualDetectionEngine
import com.projectfox.foxoff.brain.simulation.ContextualNightResult
import com.projectfox.foxoff.brain.simulation.ContextualStrategies
import com.projectfox.foxoff.brain.simulation.ContextualConfig
import com.projectfox.foxoff.brain.simulation.NightProfile
import com.projectfox.foxoff.brain.simulation.NightSimulator
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test
import java.time.Duration

/**
 * Expérience "logique contextuelle" (2026-08-16) — 4 mécanismes (A, B, C,
 * E — voir `ContextualDetectionEngine`) + vérification "D" (profils
 * D/D+H/D+F+H préservés). Moteur PARALLÈLE, jamais FoxBrain/
 * WeightedSleepAnalyzer/FoxCore réels. Mêmes 125 000 nuits que toutes les
 * expériences précédentes. AUCUNE modification de production.
 *
 * Test "très lourd" (~2-4 min) — exclu du `./gradlew test` de routine, à
 * lancer via `-PheavyTests --tests "*ContextualDetectionTest*"`.
 */
class ContextualDetectionTest {

    private data class Classified(
        val profile: NightProfile,
        val result: ContextualNightResult,
        val delayMinutes: Int?,
        val isFalsePositive: Boolean,
        val isMissed: Boolean
    )

    private data class Stats(
        val n: Int, val avgDelay: Double, val medianDelay: Double, val p95Delay: Int, val worstDelay: Int,
        val fpCount: Int, val fpRate: Double, val missedCount: Int, val missedRate: Double
    )

    private fun classify(profile: NightProfile, config: SleepScoringConfig, strategy: ContextualConfig): Classified {
        val result = ContextualDetectionEngine.run(profile, config, strategy)
        val truth = profile.groundTruthAsleepMinute
        val pauseMinute = result.pauseTriggeredAt?.let { Duration.between(NightSimulator.NIGHT_START, it).toMinutes().toInt() }
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
    fun `logique contextuelle - 4 mecanismes sur 125 000 nuits`() {
        println("=== Chargement des mêmes 125 000 nuits ===")
        val train = ProfiledNightGenerator.generate(seed = 20260815L)
        val validation = ProfiledNightGenerator.generate(seed = 88_888_888L).subList(0, 25_000)
        val all = train + validation

        fun hasLetters(p: NightProfile, vararg letters: String): Boolean {
            val parts = p.profileLabel.split("+")
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
        val subsetSets = subsets.mapValues { (_, profiles) -> profiles.toHashSet() }

        val config = SleepScoringConfig() // valeurs de production, inchangées

        var baselineStats: Map<String, Stats>? = null
        val allResults = ContextualStrategies.all().map { strategy ->
            println("\n\n########## ${strategy.label} ##########")
            val classified = all.map { classify(it, config, strategy) }
            val perSubset = subsetSets.mapValues { (_, set) -> stats(classified.filter { it.profile in set }) }
            perSubset.forEach { (name, s) -> printStats(name, s) }

            if (strategy === ContextualStrategies.BASELINE) baselineStats = perSubset

            val worstDelays = classified.filter { it.delayMinutes != null }.sortedByDescending { it.delayMinutes }.take(100)
            val worstFp = classified.filter { it.isFalsePositive }.take(100)
            println(
                "  Top 100 pires délais : ${worstDelays.lastOrNull()?.delayMinutes ?: "—"}-${worstDelays.firstOrNull()?.delayMinutes ?: "—"}min | " +
                    listOf("E", "H", "D").joinToString(", ") { l -> "$l=${worstDelays.count { hasLetters(it.profile, l) }}%" }
            )
            println(
                "  Top 100 faux positifs (n total=${classified.count { it.isFalsePositive }}) | " +
                    listOf("E", "H", "D").joinToString(", ") { l -> "$l=${worstFp.count { hasLetters(it.profile, l) }}%" }
            )

            Triple(strategy, perSubset, classified)
        }

        println("\n\n########## Vérification de fidélité (BASELINE doit reproduire la production : délai=31,0 P95=155 FP=2,04% manqués=10,95%) ##########")
        baselineStats?.get("GLOBAL")?.let { printStats("GLOBAL (BASELINE)", it) }

        println("\n\n########## VERDICT — priorités : 1) FP global<=base 2) manqués global<=base 3) délai H réduit 4) E/E+H amélioré 5) D préservé ##########")
        val base = baselineStats!!
        val baseGlobal = base.getValue("GLOBAL")
        val interesting = mutableListOf<Pair<ContextualConfig, Map<String, Stats>>>()

        allResults.forEach { (strategy, perSubset, _) ->
            if (strategy === ContextualStrategies.BASELINE) return@forEach
            val g = perSubset.getValue("GLOBAL")
            val e = perSubset.getValue("E")
            val eh = perSubset.getValue("E+H")
            val h = perSubset.getValue("H")
            val dh = perSubset.getValue("D+H")
            val dfh = perSubset.getValue("D+F+H")

            val p1 = g.fpRate <= baseGlobal.fpRate
            val p2 = g.missedRate <= baseGlobal.missedRate
            val p3 = h.avgDelay < base.getValue("H").avgDelay
            val p4 = e.fpRate <= base.getValue("E").fpRate && eh.missedRate <= base.getValue("E+H").missedRate
            val p5 = dh.avgDelay <= base.getValue("D+H").avgDelay * 1.10 && dfh.avgDelay <= base.getValue("D+F+H").avgDelay * 1.10

            println(
                "${strategy.label} : P1(FP global)=${if (p1) "OK" else "NON"} P2(manqués global)=${if (p2) "OK" else "NON"} " +
                    "P3(délai H réduit)=${if (p3) "OK" else "NON"} P4(E/E+H amélioré)=${if (p4) "OK" else "NON"} " +
                    "P5(D préservé)=${if (p5) "OK" else "NON"}"
            )
            println(
                "  Détail : FP=${"%.2f".format(g.fpRate)}%(base ${"%.2f".format(baseGlobal.fpRate)}%) " +
                    "manqués=${"%.2f".format(g.missedRate)}%(base ${"%.2f".format(baseGlobal.missedRate)}%) " +
                    "délaiH=${"%.1f".format(h.avgDelay)}min(base ${"%.1f".format(base.getValue("H").avgDelay)}min) " +
                    "FP_E=${"%.2f".format(e.fpRate)}%(base ${"%.2f".format(base.getValue("E").fpRate)}%) " +
                    "manqués_E+H=${"%.2f".format(eh.missedRate)}%(base ${"%.2f".format(base.getValue("E+H").missedRate)}%) " +
                    "délaiD+H=${"%.1f".format(dh.avgDelay)}min(base ${"%.1f".format(base.getValue("D+H").avgDelay)}min)"
            )
            if (p1 && p2) interesting += strategy to perSubset
        }

        if (interesting.isEmpty()) {
            println("\n=== CONCLUSION ===")
            println("AUCUNE stratégie contextuelle ne respecte même les priorités 1 et 2 (ne pas dégrader FP et manqués globaux).")
            println("-> Aucune modification de production proposée. La logique actuelle nécessite une conception différente au-delà du contexte temporel simple testé ici.")
        } else {
            println("\n\n########## Études de cas détaillées pour les candidates intéressantes (P1 ET P2 respectées) ##########")
            interesting.forEach { (strategy, _) ->
                println("\n--- ${strategy.label} ---")
                val classifiedForThis = all.map { classify(it, config, strategy) }
                val fpSample = classifiedForThis.filter { it.isFalsePositive && hasLetters(it.profile, "E") }.take(3)
                val hFastSample = classifiedForThis.filter { it.delayMinutes != null && hasLetters(it.profile, "H") }
                    .sortedBy { it.delayMinutes }.take(2)
                (fpSample + hFastSample).forEach { c ->
                    println("\n  Cas : ${c.profile.name} — vérité terrain=${c.profile.groundTruthAsleepMinute ?: "jamais"}min")
                    ContextualDetectionEngine.printKeyTransitions(ContextualDetectionEngine.runWithTrace(c.profile, config, strategy))
                }
            }
        }

        println("\n\n=== FIN — aucun fichier de production modifié ===")
    }
}
