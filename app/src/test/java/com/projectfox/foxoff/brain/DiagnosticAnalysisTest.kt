package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.NightDiagnostics
import com.projectfox.foxoff.brain.simulation.NightProfile
import com.projectfox.foxoff.brain.simulation.NightSimulationResult
import com.projectfox.foxoff.brain.simulation.NightSimulator
import com.projectfox.foxoff.brain.simulation.ProfiledNightGenerator
import org.junit.Test
import java.time.Duration

/**
 * Analyse DIAGNOSTIQUE pure (2026-08-16, demande explicite de Lionel après
 * confirmation que la config actuelle est un optimum local robuste) —
 * AUCUN paramètre changé, AUCUNE fonction de coût, AUCUNE recherche.
 * Rejoue exactement les 100 000 nuits d'entraînement (seed=20260815L) et
 * les 25 000 nuits de validation (seed=88 888 888L) déjà utilisées pour la
 * baseline, avec la config de production ACTUELLE inchangée, et explique
 * MÉCANIQUEMENT pourquoi les cas lents/manqués/faux positifs se
 * produisent — via `NightDiagnostics` (nouveau, purement observationnel,
 * ne modifie aucun fichier de production).
 *
 * Test "très lourd" (~1-2 min) — exclu du `./gradlew test` de routine, à
 * lancer via `-PheavyTests --tests "*DiagnosticAnalysisTest*"`.
 */
class DiagnosticAnalysisTest {

    private val current = SleepScoringConfig()

    private data class Classified(
        val profile: NightProfile,
        val result: NightSimulationResult,
        val delayMinutes: Int?,
        val isFalsePositive: Boolean,
        val isMissed: Boolean,
        val prematureByMinutes: Int?
    )

    private fun classify(profile: NightProfile, config: SleepScoringConfig): Classified {
        val result = NightSimulator().run(profile, config)
        val truth = profile.groundTruthAsleepMinute
        val pauseMinute = result.pauseTriggeredAt?.let {
            Duration.between(NightSimulator.NIGHT_START, it).toMinutes().toInt()
        }
        return when {
            truth == null && pauseMinute != null -> Classified(profile, result, null, true, false, null)
            truth == null -> Classified(profile, result, null, false, false, null)
            pauseMinute != null && pauseMinute < truth -> Classified(profile, result, null, true, false, truth - pauseMinute)
            pauseMinute == null -> Classified(profile, result, null, false, true, null)
            else -> Classified(profile, result, pauseMinute - truth, false, false, null)
        }
    }

    @Test
    fun `analyse diagnostique des echecs sur les 125 000 nuits deja utilisees`() {
        println("=== Chargement des mêmes 125 000 nuits que la baseline (train 100k + validation 25k) ===")
        val train = ProfiledNightGenerator.generate(seed = 20260815L)
        val validation = ProfiledNightGenerator.generate(seed = 88_888_888L).subList(0, 25_000)
        val all = train + validation

        val classified = all.map { classify(it, current) }
        val detected = classified.filter { it.delayMinutes != null }
        val missed = classified.filter { it.isMissed }
        val falsePositives = classified.filter { it.isFalsePositive }

        println(
            "Total ${classified.size} nuits : ${detected.size} détectées correctement, " +
                "${missed.size} manquées (${"%.2f".format(missed.size * 100.0 / classified.size)}%), " +
                "${falsePositives.size} faux positifs (${"%.2f".format(falsePositives.size * 100.0 / classified.size)}%)"
        )

        // === 4. Répartition par profil (cause probable = composition A-J) ===
        fun letterBreakdown(label: String, group: List<Classified>) {
            println("\n--- Répartition par profil parmi les $label (n=${group.size}) ---")
            for (letter in listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "INSOMNIE")) {
                val count = group.count { letter in it.profile.profileLabel.split("+") }
                if (group.isNotEmpty()) {
                    println("  $letter : $count (${"%.1f".format(count * 100.0 / group.size)}%)")
                }
            }
        }
        letterBreakdown("détections manquées", missed)
        letterBreakdown("faux positifs", falsePositives)

        // === 1. Top 100 pires délais ===
        val worstDelays = detected.filterNot { it.profile.neverSleeps }
            .sortedByDescending { it.delayMinutes }
            .take(100)
        println("\n=== 1. Top 100 pires délais de détection (délai le plus long) ===")
        println("Délai min/max de ce top 100 : ${worstDelays.last().delayMinutes}min / ${worstDelays.first().delayMinutes}min")
        letterBreakdown("top 100 pires délais", worstDelays)

        // === 2. Top 100 pires manqués (les plus "proches" du seuil, jamais franchi) ===
        val worstMissed = missed.sortedByDescending { it.result.maxProbability }.take(100)
        println("\n=== 2. Top 100 pires détections manquées (score max le plus proche du seuil ASLEEP=90%, jamais franchi) ===")
        println(
            "Score max de ce top 100 : ${"%.0f".format(worstMissed.last().result.maxProbability * 100)}% / " +
                "${"%.0f".format(worstMissed.first().result.maxProbability * 100)}%"
        )
        letterBreakdown("top 100 pires manqués", worstMissed)

        // === 3. Faux positifs — top 100 les plus prématurés ===
        val worstFalsePositives = falsePositives.sortedByDescending { it.prematureByMinutes ?: 0 }.take(100)
        println("\n=== 3. Top 100 faux positifs les plus prématurés ===")
        if (worstFalsePositives.isNotEmpty()) {
            println(
                "Avance sur la vérité terrain (ou détection à tort sur une nuit d'insomnie) : " +
                    "min=${worstFalsePositives.last().prematureByMinutes ?: "N/A (insomnie)"}min, " +
                    "max=${worstFalsePositives.first().prematureByMinutes ?: "N/A (insomnie)"}min"
            )
        }
        letterBreakdown("top 100 faux positifs", worstFalsePositives)

        // === Comparaison nuits rapides (<=10min) vs très tardives (>60min) ===
        val fast = detected.filter { (it.delayMinutes ?: 0) <= 10 }
        val slow = detected.filter { (it.delayMinutes ?: 0) > 60 }
        println("\n=== Comparaison : détections rapides (<=10min, n=${fast.size}) vs très tardives (>60min, n=${slow.size}) ===")
        fun avgOf(group: List<Classified>, f: (NightProfile) -> Number) =
            if (group.isEmpty()) Double.NaN else group.map { f(it.profile).toDouble() }.average()
        println("  bpmNoise moyen         : rapides=${"%.2f".format(avgOf(fast) { it.bpmNoise })} | tardives=${"%.2f".format(avgOf(slow) { it.bpmNoise })}")
        println("  mouvements isolés/nuit : rapides=${"%.2f".format(avgOf(fast) { it.isolatedMovementMinutes.size })} | tardives=${"%.2f".format(avgOf(slow) { it.isolatedMovementMinutes.size })}")
        println("  réveils nocturnes/nuit : rapides=${"%.2f".format(avgOf(fast) { it.nightWakingMinutes.size })} | tardives=${"%.2f".format(avgOf(slow) { it.nightWakingMinutes.size })}")
        println("  % avec TV éteinte      : rapides=${"%.1f".format(fast.count { it.profile.tvOffMinute != null } * 100.0 / fast.size.coerceAtLeast(1))}% | tardives=${"%.1f".format(slow.count { it.profile.tvOffMinute != null } * 100.0 / slow.size.coerceAtLeast(1))}%")
        println("  % avec déconnexion montre : rapides=${"%.1f".format(fast.count { it.profile.watchDropoutRange != null } * 100.0 / fast.size.coerceAtLeast(1))}% | tardives=${"%.1f".format(slow.count { it.profile.watchDropoutRange != null } * 100.0 / slow.size.coerceAtLeast(1))}%")
        println("  % hésitant (endorm. E) : rapides=${"%.1f".format(fast.count { it.profile.hesitant } * 100.0 / fast.size.coerceAtLeast(1))}% | tardives=${"%.1f".format(slow.count { it.profile.hesitant } * 100.0 / slow.size.coerceAtLeast(1))}%")
        letterBreakdown("détections rapides <=10min", fast)
        letterBreakdown("détections très tardives >60min", slow)

        // === 5-9. Études de cas détaillées (trajectoire complète) ===
        println("\n\n########## ÉTUDES DE CAS DÉTAILLÉES (trajectoire minute par minute, transitions clés uniquement) ##########")

        println("\n=== Cas 1-3 : pires délais ===")
        worstDelays.take(3).forEachIndexed { i, c ->
            println("\n--- Délai #${i + 1} : ${c.profile.name} — délai=${c.delayMinutes}min, vérité terrain=${c.profile.groundTruthAsleepMinute}min ---")
            NightDiagnostics.printKeyTransitions(NightDiagnostics.runWithTrace(c.profile, current))
        }

        println("\n=== Cas 1-3 : pires détections manquées (near-miss) ===")
        worstMissed.take(3).forEachIndexed { i, c ->
            println("\n--- Manqué #${i + 1} : ${c.profile.name} — score max=${"%.0f".format(c.result.maxProbability * 100)}%, vérité terrain=${c.profile.groundTruthAsleepMinute}min ---")
            NightDiagnostics.printKeyTransitions(NightDiagnostics.runWithTrace(c.profile, current))
        }

        println("\n=== Cas 1-3 : pires faux positifs ===")
        worstFalsePositives.take(3).forEachIndexed { i, c ->
            println("\n--- Faux positif #${i + 1} : ${c.profile.name} — avance=${c.prematureByMinutes ?: "N/A"}min, vérité terrain=${c.profile.groundTruthAsleepMinute ?: "jamais (insomnie)"} ---")
            NightDiagnostics.printKeyTransitions(NightDiagnostics.runWithTrace(c.profile, current))
        }
    }
}
