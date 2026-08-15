package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ActigraphyConfig
import com.projectfox.foxoff.brain.simulation.ActigraphyEngine
import com.projectfox.foxoff.brain.simulation.ActigraphyNightResult
import com.projectfox.foxoff.brain.simulation.ActigraphyStrategies
import com.projectfox.foxoff.brain.simulation.NightProfile
import com.projectfox.foxoff.brain.simulation.NightSimulator
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test
import java.time.Duration

/**
 * Expérience "algorithme d'actigraphie" (2026-08-16) — voir
 * `ActigraphyEngine` pour la méthode (Cole-Kripke réadapté en causal, pur
 * et hybride avec le signal BPM existant). Moteur PARALLÈLE, jamais
 * FoxBrain/WeightedSleepAnalyzer/FoxCore réels. Mêmes 125 000 nuits que
 * toutes les expériences précédentes. AUCUNE modification de production.
 *
 * Test "très lourd" (~2-3 min) — exclu du `./gradlew test` de routine, à
 * lancer via `-PheavyTests --tests "*ActigraphyExperimentTest*"`.
 */
class ActigraphyExperimentTest {

    private data class Classified(
        val profile: NightProfile, val result: ActigraphyNightResult,
        val delayMinutes: Int?, val isFalsePositive: Boolean, val isMissed: Boolean
    )
    private data class Stats(
        val n: Int, val avgDelay: Double, val medianDelay: Double, val p95Delay: Int, val worstDelay: Int,
        val fpCount: Int, val fpRate: Double, val missedCount: Int, val missedRate: Double
    )

    private fun classify(profile: NightProfile, config: SleepScoringConfig, strategy: ActigraphyConfig): Classified {
        val result = ActigraphyEngine.run(profile, config, strategy)
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
            p95Delay = percentile(0.95), worstDelay = delays.maxOrNull() ?: -1,
            fpCount = group.count { it.isFalsePositive }, fpRate = group.count { it.isFalsePositive } * 100.0 / group.size,
            missedCount = group.count { it.isMissed }, missedRate = group.count { it.isMissed } * 100.0 / group.size
        )
    }

    private fun printStats(label: String, s: Stats) {
        println(
            "  [$label] n=${s.n} délai(moy/méd/P95/pire)=${"%.1f".format(s.avgDelay)}/${"%.1f".format(s.medianDelay)}/${s.p95Delay}/${s.worstDelay}min " +
                "FP=${s.fpCount}(${"%.2f".format(s.fpRate)}%) manqués=${s.missedCount}(${"%.2f".format(s.missedRate)}%)"
        )
    }

    @Test
    fun `algorithme actigraphie pur et hybride sur 125 000 nuits`() {
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

        println("\n=== Référence : configuration production actuelle (rappel, valeurs déjà connues) ===")
        println("  GLOBAL délai=31,0min FP=2,02% manqués=11,01% (voir runs précédents)")

        ActigraphyStrategies.all().forEach { strategy ->
            println("\n\n########## ${strategy.label} ##########")
            val classified = all.map { classify(it, config, strategy) }
            val perSubset = subsetSets.mapValues { (_, set) -> stats(classified.filter { it.profile in set }) }
            perSubset.forEach { (name, s) -> printStats(name, s) }

            val worstDelays = classified.filter { it.delayMinutes != null }.sortedByDescending { it.delayMinutes }.take(100)
            val worstFp = classified.filter { it.isFalsePositive }.take(100)
            println(
                "  Top 100 pires délais : ${worstDelays.lastOrNull()?.delayMinutes ?: "—"}-${worstDelays.firstOrNull()?.delayMinutes ?: "—"}min | " +
                    listOf("E", "H", "D").joinToString(", ") { l -> "$l=${worstDelays.count { hasLetters(it.profile, l) }}%" }
            )
            println(
                "  Faux positifs (n total=${classified.count { it.isFalsePositive }}) | " +
                    listOf("E", "H", "D").joinToString(", ") { l -> "$l=${worstFp.count { hasLetters(it.profile, l) }}%" }
            )
        }

        println("\n\n=== FIN — aucun fichier de production modifié ===")
    }
}
