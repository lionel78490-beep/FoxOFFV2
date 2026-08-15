package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.NightProfileGenerator
import com.projectfox.foxoff.brain.simulation.NightSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Rejoue 50 nuits de sommeil synthétiques (voir NightProfileGenerator, 9
 * catégories réalistes) à travers le VRAI moteur de production (FoxBrain +
 * WeightedSleepAnalyzer + FoxCore.shouldSendAutoPause), sans attendre 50
 * vraies nuits. Filet de non-régression pour toute future modification de
 * seuil (SleepScoringConfig) : une nuit qui se met à échouer signale un
 * vrai changement de comportement de détection, à examiner avant de
 * merger.
 *
 * Chaque nuit imprime une ligne de résultat (visible dans la sortie
 * console de `./gradlew testDebugUnitTest`), suivie d'un récapitulatif.
 */
class NightSimulationTest {

    @Test
    fun `cinquante nuits simulees couvrent les scenarios cles sans regression`() {
        val profiles = NightProfileGenerator.generate()
        assertEquals("le générateur doit produire exactement 50 nuits", 50, profiles.size)

        val simulator = NightSimulator()
        val results = profiles.map { simulator.run(it) }

        println("=== Résultats des 50 nuits simulées ===")
        results.forEach { println(it.summaryLine()) }

        val failures = results.filterNot { it.passed }
        val passCount = results.size - failures.size
        println("=== Récapitulatif : $passCount/${results.size} nuits conformes ===")

        if (failures.isNotEmpty()) {
            val detail = failures.joinToString("\n") { r ->
                "- ${r.profile.name} (${r.profile.category}) ${r.notes}"
            }
            fail("$passCount/${results.size} nuits conformes — nuits non conformes :\n$detail")
        }
    }
}
