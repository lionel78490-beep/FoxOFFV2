package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.ConfigOptimizer
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie le générateur à 100 000+ nuits (2026-08-15, profils A-J +
 * combinaisons, voir `ProfiledNightGenerator`) : volume, diversité, et un
 * passage complet à travers le vrai moteur (`ConfigOptimizer.evaluateConfig`,
 * pas de doublure) contre la configuration de production actuelle, pour un
 * premier rapport chiffré sur ce jeu plus riche. Ne modifie AUCUN fichier de
 * production, ne relance PAS de recherche de configuration ici (juste un
 * état des lieux) — voir ROADMAP.md pour la suite proposée.
 */
class ProfiledNightGeneratorTest {

    @Test
    fun `genere au moins 100 000 nuits toutes differentes et evalue la production dessus`() {
        val profiles = ProfiledNightGenerator.generate(seed = 20260815L)

        assertTrue("au moins 100 000 nuits attendues, obtenu ${profiles.size}", profiles.size >= 100_000)

        // --- Diversité ("chaque nuit doit être différente") ---
        // `name` contient l'index (toujours unique) : exclu de la
        // comparaison pour mesurer la vraie diversité des PARAMÈTRES, pas
        // juste un numéro de série.
        val distinct = profiles.map { it.copy(name = "") }.distinct().size
        val distinctRatio = distinct.toDouble() / profiles.size
        println("=== Diversité : $distinct/${profiles.size} nuits strictement distinctes (${"%.2f".format(distinctRatio * 100)}%) ===")
        assertTrue("trop de doublons pour un tirage censé être continu/aléatoire", distinctRatio > 0.99)

        // --- Répartition par profil (labels combinés inclus) ---
        val letterCounts = mutableMapOf<String, Int>()
        for (letter in listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "INSOMNIE")) {
            letterCounts[letter] = profiles.count { letter in it.profileLabel.split("+") }
        }
        println("\n=== Répartition par profil (une nuit peut compter dans plusieurs, profils combinés) ===")
        letterCounts.forEach { (letter, count) ->
            println("$letter : $count (${"%.1f".format(count * 100.0 / profiles.size)}%)")
        }

        val combinedCount = profiles.count { it.profileLabel.contains("+") }
        println("\nNuits avec au moins un modificateur combiné : $combinedCount (${"%.1f".format(combinedCount * 100.0 / profiles.size)}%)")

        // Sanity : chaque lettre doit apparaître un nombre substantiel de
        // fois (aucun profil oublié/jamais tiré par erreur de probabilité).
        letterCounts.forEach { (letter, count) ->
            assertTrue("profil $letter quasi absent ($count occurrences) — probable bug de génération", count > 100)
        }

        // --- Passage complet à travers le vrai moteur (FoxBrain +
        // WeightedSleepAnalyzer + FoxCore.shouldSendAutoPause), config de
        // production actuelle, pour un premier rapport chiffré. ---
        val production = SleepScoringConfig()
        val stats = ConfigOptimizer.evaluateConfig(profiles, production)
        println(
            "\n=== Production sur ${profiles.size} nuits (profils A-J + combinaisons) ===\n" +
                "délai moyen=${"%.1f".format(stats.averageDelayMinutes)}min " +
                "pire=${stats.worstDelayMinutes}min " +
                "fauxPositifs=${stats.falsePositiveCount}/${stats.nightCount} (${"%.2f".format(stats.falsePositiveRate * 100)}%) " +
                "manqués=${stats.missedDetectionCount}/${stats.nightCount} (${"%.2f".format(stats.missedDetectionRate * 100)}%)"
        )
    }
}
