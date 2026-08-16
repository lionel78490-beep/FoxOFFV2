package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.NextGenConfig
import com.projectfox.foxoff.brain.simulation.NextGenDetectionEngine
import com.projectfox.foxoff.brain.simulation.NextGenNightResult
import com.projectfox.foxoff.brain.simulation.NextGenStrategies
import com.projectfox.foxoff.brain.simulation.NightProfile
import com.projectfox.foxoff.brain.simulation.NightSimulator
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test
import java.time.Duration

/**
 * Grande réinvestigation (2026-08-16, demande explicite : "revoir la
 * manière de le détecter", "quelque chose de très optimisé", "100 000
 * tests avant validation") — teste les 4 nouveaux mécanismes de
 * `NextGenDetectionEngine`, ISOLÉMENT d'abord (comme les expériences
 * précédentes : durée/seuil, continuité, contexte), sur 100 000+ nuits
 * synthétiques. AUCUNE application à la production — voir
 * `NextGenRealNightValidationTest` pour la validation sur les 2 vraies
 * nuits (obligatoire, leçon de la régression du 15-16 août).
 *
 * Test "très lourd" (100k nuits × 5 stratégies) — exclu du `./gradlew test`
 * de routine, à lancer via `-PheavyTests --tests "*NextGenDetectionTest*"`.
 */
class NextGenDetectionTest {

    private data class Classified(
        val profile: NightProfile, val delayMinutes: Int?, val isFalsePositive: Boolean, val isMissed: Boolean
    )
    private data class Stats(
        val n: Int, val avgDelay: Double, val medianDelay: Double, val p95Delay: Int, val worstDelay: Int,
        val fpCount: Int, val fpRate: Double, val missedCount: Int, val missedRate: Double
    )

    private fun classify(profile: NightProfile, result: NextGenNightResult): Classified {
        val truth = profile.groundTruthAsleepMinute
        val pauseMinute = result.pauseTriggeredAt?.let { Duration.between(NightSimulator.NIGHT_START, it).toMinutes().toInt() }
        return when {
            truth == null && pauseMinute != null -> Classified(profile, null, true, false)
            truth == null -> Classified(profile, null, false, false)
            pauseMinute != null && pauseMinute < truth -> Classified(profile, null, true, false)
            pauseMinute == null -> Classified(profile, null, false, true)
            else -> Classified(profile, pauseMinute - truth, false, false)
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
    fun `4 nouveaux mecanismes testes isolement sur 100 000 nuits synthetiques`() {
        println("=== Chargement de 100 000 nuits (seed identique aux expériences précédentes pour comparabilité) ===")
        val nights = ProfiledNightGenerator.generate(seed = 20260815L).take(100_000)

        fun hasLetters(p: NightProfile, vararg letters: String): Boolean {
            val parts = p.profileLabel.split("+")
            return letters.all { it in parts }
        }
        val subsets = linkedMapOf(
            "GLOBAL" to nights,
            "E" to nights.filter { hasLetters(it, "E") },
            "H" to nights.filter { hasLetters(it, "H") },
            "E+H" to nights.filter { hasLetters(it, "E", "H") },
            "D+H" to nights.filter { hasLetters(it, "D", "H") },
            "D+F+H" to nights.filter { hasLetters(it, "D", "F", "H") }
        )
        val subsetSets = subsets.mapValues { (_, profiles) -> profiles.toHashSet() }
        val config = SleepScoringConfig() // config de production ACTUELLE, inchangée

        var baselineGlobal: Stats? = null

        NextGenStrategies.isolated().forEach { strategy ->
            println("\n\n########## ${strategy.label} ##########")
            val classified = nights.map { classify(it, NextGenDetectionEngine.run(it, config, strategy)) }
            val perSubset = subsetSets.mapValues { (_, set) -> stats(classified.filter { it.profile in set }) }
            perSubset.forEach { (name, s) -> printStats(name, s) }
            if (strategy == NextGenStrategies.BASELINE) baselineGlobal = perSubset.getValue("GLOBAL")

            val global = perSubset.getValue("GLOBAL")
            baselineGlobal?.let { base ->
                val fpOk = global.fpRate <= base.fpRate
                val missedOk = global.missedRate <= base.missedRate
                val faster = global.avgDelay < base.avgDelay
                println(
                    "  VERDICT vs BASELINE : FP ${if (fpOk) "OK" else "DÉGRADÉ"} | manqués ${if (missedOk) "OK" else "DÉGRADÉ"} | " +
                        "délai ${if (faster) "MEILLEUR" else "PAS MEILLEUR"} (${"%.1f".format(base.avgDelay)} -> ${"%.1f".format(global.avgDelay)}min)"
                )
            }
        }

        println("\n\n=== FIN — aucun fichier de production modifié ===")
    }

    /**
     * Suite du run isolé : M2 (tendance BPM) a fait exploser les FP
     * (1,5% -> 13,3%) avec ses réglages d'origine — teste sa version
     * verrouillée (`M2B_TENDANCE_VERROUILLEE`) seule, puis les combinaisons
     * des mécanismes qui ont individuellement tenu la double contrainte
     * (M1 systématiquement, M3/M4 neutres à légèrement positifs).
     */
    @Test
    fun `version verrouillee et combinaisons sur 100 000 nuits`() {
        val nights = ProfiledNightGenerator.generate(seed = 20260815L).take(100_000)
        fun hasLetters(p: NightProfile, vararg letters: String): Boolean {
            val parts = p.profileLabel.split("+")
            return letters.all { it in parts }
        }
        val subsets = linkedMapOf(
            "GLOBAL" to nights,
            "E" to nights.filter { hasLetters(it, "E") },
            "H" to nights.filter { hasLetters(it, "H") },
            "E+H" to nights.filter { hasLetters(it, "E", "H") },
            "D+H" to nights.filter { hasLetters(it, "D", "H") },
            "D+F+H" to nights.filter { hasLetters(it, "D", "F", "H") }
        )
        val subsetSets = subsets.mapValues { (_, profiles) -> profiles.toHashSet() }
        val config = SleepScoringConfig()

        val baselineClassified = nights.map { classify(it, NextGenDetectionEngine.run(it, config, NextGenStrategies.BASELINE)) }
        val baselineGlobal = stats(baselineClassified)
        println("=== Référence BASELINE (rappel) ===")
        printStats("GLOBAL", baselineGlobal)

        (listOf(NextGenStrategies.M2B_TENDANCE_VERROUILLEE) + NextGenStrategies.combinations()).forEach { strategy ->
            println("\n\n########## ${strategy.label} ##########")
            val classified = nights.map { classify(it, NextGenDetectionEngine.run(it, config, strategy)) }
            val perSubset = subsetSets.mapValues { (_, set) -> stats(classified.filter { it.profile in set }) }
            perSubset.forEach { (name, s) -> printStats(name, s) }

            val global = perSubset.getValue("GLOBAL")
            val fpOk = global.fpRate <= baselineGlobal.fpRate
            val missedOk = global.missedRate <= baselineGlobal.missedRate
            val faster = global.avgDelay < baselineGlobal.avgDelay
            println(
                "  VERDICT vs BASELINE : FP ${if (fpOk) "OK" else "DÉGRADÉ"} (${"%.2f".format(baselineGlobal.fpRate)}%->${"%.2f".format(global.fpRate)}%) | " +
                    "manqués ${if (missedOk) "OK" else "DÉGRADÉ"} (${"%.2f".format(baselineGlobal.missedRate)}%->${"%.2f".format(global.missedRate)}%) | " +
                    "délai ${if (faster) "MEILLEUR" else "PAS MEILLEUR"} (${"%.1f".format(baselineGlobal.avgDelay)}->${"%.1f".format(global.avgDelay)}min)"
            )
        }

        println("\n\n=== FIN — aucun fichier de production modifié ===")
    }
}
