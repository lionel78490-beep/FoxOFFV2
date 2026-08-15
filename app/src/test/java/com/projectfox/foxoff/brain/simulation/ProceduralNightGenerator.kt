package com.projectfox.foxoff.brain.simulation

import kotlin.random.Random

/**
 * Générateur de nuits synthétiques à grande échelle pour `ConfigOptimizer`
 * (2026-08-15) — contrairement à `NightProfileGenerator` (9 catégories
 * fixes, 50 nuits, 2026-08-14), les paramètres sont tirés dans des plages
 * réalistes continues, avec une vérité terrain explicite
 * (`groundTruthAsleepMinute`) permettant de mesurer un DÉLAI plutôt qu'un
 * simple oui/non.
 *
 * ÉTANCHE PAR CONSTRUCTION : ce fichier ne connaît ni ne dépend de
 * `SleepScoringConfig`, `FoxBrain`, ni de `ConfigOptimizer` — il ne fait
 * QUE décrire des nuits (la vérité terrain), jamais évaluer une
 * configuration. Les profils générés ici sont figés au moment de l'appel
 * (seed fixe, reproductible) et ne doivent JAMAIS être ajustés en fonction
 * des résultats d'une recherche de configuration — voir le garde-fou
 * explicite dans le plan du 2026-08-15.
 *
 * Deux réalismes ajoutés le 2026-08-15, absents des 50 profils du
 * 2026-08-14 (motivés par le vrai journal de la nuit du 15 août, voir
 * `RealNightReplayTest`) : (1) une "rafale" de mouvements significatifs en
 * tout début de nuit (s'installer au lit — le vrai journal en montrait ~7
 * en 11 minutes), présente sur la quasi-totalité des profils generés ici ;
 * (2) une probabilité que la TV s'éteigne seule en cours de nuit
 * (`tvOffMinute`), cause n°1 identifiée du retard de 6h08 constaté ce
 * jour-là.
 */
object ProceduralNightGenerator {

    // Doit rester synchronisé avec NightSimulator.ONSET_DURATION (10f) —
    // dupliqué ici plutôt que d'élargir la visibilité du companion object
    // privé de NightSimulator pour une seule constante.
    private const val ONSET_DURATION_MINUTES = 10

    fun generate(count: Int, seed: Long): List<NightProfile> =
        (0 until count).map { i -> generateOne(i, Random(seed + i * 7919L)) }

    private fun generateOne(index: Int, rng: Random): NightProfile {
        val neverSleeps = rng.nextFloat() < 0.05f
        val tvNeverOn = !neverSleeps && rng.nextFloat() < 0.10f
        val tvOn = !tvNeverOn
        val hasWatchDropout = rng.nextFloat() < 0.10f
        val hasTvOff = tvOn && !neverSleeps && rng.nextFloat() < 0.25f
        val restless = !neverSleeps && rng.nextFloat() < 0.20f
        val hasMorningWake = !neverSleeps && rng.nextFloat() < 0.15f
        val nightWakingCount = if (neverSleeps) 0 else rng.nextInt(0, 4)
        val hesitant = !neverSleeps && rng.nextFloat() < 0.20f

        val awakeBpm = 60 + rng.nextInt(0, 31) // 60-90
        val asleepBpm = (42 + rng.nextInt(0, 17)).coerceAtMost(awakeBpm - 10) // 42-58, toujours < awakeBpm
        val bpmNoise = 1 + rng.nextInt(0, 3) // 1-3
        val sleepOnsetMinute = 5 + rng.nextInt(0, 56) // 5-60

        // Rafale de mouvements en s'installant au lit — présente sur ~90%
        // des nuits (une nuit sans aucun mouvement au coucher serait
        // irréaliste), intensité variable. Réutilise le mécanisme existant
        // `isolatedMovementMinutes` (magnitude fixe au-dessus du seuil,
        // voir NightSimulator.movementAt) plutôt que d'ajouter un nouveau
        // champ.
        val settlingBurst = if (rng.nextFloat() < 0.90f) {
            val burstCount = 3 + rng.nextInt(0, 6) // 3-8
            (0 until burstCount).map { rng.nextInt(0, 16) }.distinct() // dans les 15 premières minutes
        } else {
            emptyList()
        }
        val scatteredMovements = (0 until rng.nextInt(0, 4)).map {
            60 + rng.nextInt(0, minOf(390, 470 - sleepOnsetMinute))
        }
        val isolatedMovementMinutes = (settlingBurst + scatteredMovements).distinct().sorted()

        val nightWakingMinutes = (0 until nightWakingCount).map {
            60 + rng.nextInt(0, 340)
        }.distinct().sorted()

        val watchDropoutRange = if (hasWatchDropout) {
            val start = 60 + rng.nextInt(0, 340)
            val length = 15 + rng.nextInt(0, 31) // 15-45 min
            start..(start + length)
        } else null

        val tvOffMinute = if (hasTvOff) 60 + rng.nextInt(0, 360) else null

        val morningWakeMinute = if (hasMorningWake) 380 + rng.nextInt(0, 71) else null

        // Baseline calibré cohérent avec l'inférence de RealNightReplayTest
        // (~50 chez Lionel) plutôt que le générique 70 — plage réaliste
        // resserrée autour de l'asleepBpm de la nuit, pas une valeur fixe
        // identique pour toutes les nuits.
        val restingBpmBaseline = (asleepBpm + rng.nextInt(-2, 6)).coerceAtLeast(35)

        val groundTruthAsleepMinute = if (neverSleeps) null else sleepOnsetMinute + ONSET_DURATION_MINUTES

        return NightProfile(
            name = "Nuit procédurale #$index",
            category = NightCategory.PROCEDURAL,
            awakeBpm = awakeBpm,
            asleepBpm = asleepBpm,
            bpmNoise = bpmNoise,
            sleepOnsetMinute = sleepOnsetMinute,
            hesitant = hesitant,
            nightWakingMinutes = nightWakingMinutes,
            isolatedMovementMinutes = isolatedMovementMinutes,
            tvOn = tvOn,
            watchDropoutRange = watchDropoutRange,
            neverSleeps = neverSleeps,
            restlessButLowBpm = restless,
            morningWakeMinute = morningWakeMinute,
            tvOffMinute = tvOffMinute,
            restingBpmBaseline = restingBpmBaseline,
            // expectAsleep/expectAutoPause/expectNeverAsleep : champs hérités
            // de NightProfileGenerator (2026-08-14), non utilisés par
            // ConfigOptimizer (qui se base uniquement sur
            // groundTruthAsleepMinute) — valeurs cohérentes fournies quand
            // même pour que le profil reste utilisable si besoin ailleurs.
            expectAsleep = !neverSleeps,
            expectAutoPause = !neverSleeps,
            expectNeverAsleep = neverSleeps,
            groundTruthAsleepMinute = groundTruthAsleepMinute
        )
    }
}
