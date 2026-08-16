package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.NextGenDetectionEngine
import com.projectfox.foxoff.brain.simulation.NextGenStrategies
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test
import java.time.Duration

/**
 * Validation indépendante (seed différente, 2026-08-16) des candidates
 * NON écartées par les vraies nuits (M3 rejeté — voir
 * `NextGenRealNightValidationTest`, néfaste sur la nuit du 15-16 août
 * malgré un profil neutre en synthétique). Dernière étape avant de
 * pouvoir proposer quoi que ce soit à Lionel — aucune application encore.
 *
 * Test "lourd" (40k nuits × 4 stratégies) — exclu du `./gradlew test` de
 * routine.
 */
class NextGenIndependentValidationTest {

    private fun classify(profile: com.projectfox.foxoff.brain.simulation.NightProfile, result: com.projectfox.foxoff.brain.simulation.NextGenNightResult): Triple<Int?, Boolean, Boolean> {
        val truth = profile.groundTruthAsleepMinute
        val pauseMinute = result.pauseTriggeredAt?.let { Duration.between(com.projectfox.foxoff.brain.simulation.NightSimulator.NIGHT_START, it).toMinutes().toInt() }
        return when {
            truth == null && pauseMinute != null -> Triple(null, true, false)
            truth == null -> Triple(null, false, false)
            pauseMinute != null && pauseMinute < truth -> Triple(null, true, false)
            pauseMinute == null -> Triple(null, false, true)
            else -> Triple(pauseMinute - truth, false, false)
        }
    }

    @Test
    fun `valide M1 et M2b sur un jeu synthetique independant`() {
        println("=== Jeu indépendant : 40 000 nuits, seed différente de tous les runs précédents ===")
        val nights = ProfiledNightGenerator.generate(seed = 77_777_777L).take(40_000)
        val config = SleepScoringConfig()

        val strategies = listOf(
            NextGenStrategies.BASELINE,
            NextGenStrategies.M1_PLANCHER_GLISSANT,
            NextGenStrategies.M2B_TENDANCE_VERROUILLEE,
            NextGenStrategies.combinations().first { it.label == "COMBO-M1+M2b" }
        )

        var baselineFp = 0.0
        var baselineMissed = 0.0
        var baselineDelay = 0.0

        strategies.forEach { strategy ->
            val results = nights.map { classify(it, NextGenDetectionEngine.run(it, config, strategy)) }
            val delays = results.mapNotNull { it.first }
            val fpCount = results.count { it.second }
            val missedCount = results.count { it.third }
            val fpRate = fpCount * 100.0 / nights.size
            val missedRate = missedCount * 100.0 / nights.size
            val avgDelay = if (delays.isNotEmpty()) delays.average() else Double.NaN

            if (strategy == NextGenStrategies.BASELINE) {
                baselineFp = fpRate; baselineMissed = missedRate; baselineDelay = avgDelay
            }

            println(
                "[${strategy.label}] FP=${"%.2f".format(fpRate)}% manqués=${"%.2f".format(missedRate)}% délai=${"%.1f".format(avgDelay)}min " +
                    "| vs baseline: FP ${if (fpRate <= baselineFp) "OK" else "DÉGRADÉ"} manqués ${if (missedRate <= baselineMissed) "OK" else "DÉGRADÉ"}"
            )
        }
        println("\n=== FIN — aucun fichier de production modifié ===")
    }
}
