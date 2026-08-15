package com.projectfox.foxoff.brain.simulation

import kotlin.random.Random

/**
 * Générateur à grande échelle (2026-08-15, demande explicite : "générer au
 * minimum 100 000 nuits... chaque nuit doit être différente") — 10 profils
 * nommés A à J, combinables entre eux. Distinct de `ProceduralNightGenerator`
 * (2026-08-15, run précédent déjà documenté dans ROADMAP.md) : celui-ci
 * reste inchangé pour que ce run reste reproductible ; ce nouveau générateur
 * est une nomenclature explicite plutôt qu'un tirage purement continu.
 *
 * Aucun processus ni bot Android : uniquement des `NightProfile` (données),
 * rejoués ensuite par [NightSimulator] à travers le vrai moteur — même
 * principe que le reste du framework depuis le 2026-08-14.
 *
 * ÉTANCHE PAR CONSTRUCTION (même garde-fou que `ProceduralNightGenerator`) :
 * ce fichier ne dépend jamais de `SleepScoringConfig` ni de `ConfigOptimizer`
 * — il ne fait que décrire des nuits (vérité terrain), jamais évaluer une
 * configuration.
 *
 * ## Profils de base (durée d'endormissement, exclusifs — un seul par nuit)
 * - **A — Endormissement rapide** : 20-40 min.
 * - **B — Endormissement normal** : 40-90 min.
 * - **C — Endormissement lent** : 90-180 min.
 * - **D — Très fatigué** : endormissement très rapide, 5-20 min.
 * - **E — Difficulté à s'endormir** : longue période avant sommeil,
 *   120-300 min, hésitant (oscillations).
 * - **Insomnie totale** (mécanisme déjà existant, `neverSleeps`) : tirée
 *   indépendamment, prioritaire sur A-E — représente la queue extrême de E.
 *
 * ## Modificateurs (combinables librement, indépendants les uns des autres)
 * - **F — Réveil après endormissement** : micro-réveil puis retour au
 *   sommeil (réutilise `nightWakingMinutes`, un seul réveil).
 * - **G — Nuit agitée** : BPM et mouvements irréguliers
 *   (`restlessButLowBpm` + bruit BPM et mouvements isolés supplémentaires).
 * - **H — Signal bruité** : bruit BPM fortement augmenté (capteur peu fiable).
 * - **I — Données manquantes** : perte de connexion montre en cours de nuit
 *   (réutilise `watchDropoutRange`).
 * - **J — Faux signal de sommeil** : baisse temporaire du BPM avant le vrai
 *   endormissement, sans sommeil réel (`falseDipMinute`, nouveau ce jour).
 *
 * Une nuit "combinée" (ex: "B+F+H") est une nuit dont le profil de base est
 * B et qui a tiré à la fois le modificateur F et le modificateur H — ce
 * mécanisme d'empilement indépendant génère naturellement un très grand
 * nombre de combinaisons distinctes sans les énumérer une par une.
 */
object ProfiledNightGenerator {

    private const val MIN_COUNT = 100_000
    private const val ONSET_DURATION_MINUTES = 10 // doit rester synchronisé avec NightSimulator.ONSET_DURATION

    private data class BaseTimingProfile(val letter: String, val groundTruthRange: IntRange, val hesitant: Boolean)

    private val baseProfiles = listOf(
        BaseTimingProfile("A", 20..40, hesitant = false) to 0.20f,
        BaseTimingProfile("B", 40..90, hesitant = false) to 0.35f,
        BaseTimingProfile("C", 90..180, hesitant = false) to 0.15f,
        BaseTimingProfile("D", 5..20, hesitant = false) to 0.10f,
        BaseTimingProfile("E", 120..300, hesitant = true) to 0.20f
    )

    /** Génère au moins [count] nuits (100 000 par défaut, jamais moins). Reproductible via [seed]. */
    fun generate(count: Int = MIN_COUNT, seed: Long): List<NightProfile> {
        val actualCount = count.coerceAtLeast(MIN_COUNT)
        return (0 until actualCount).map { i -> generateOne(i, Random(seed + i * 104_729L)) }
    }

    private fun pickBaseProfile(rng: Random): BaseTimingProfile {
        val roll = rng.nextFloat()
        var acc = 0f
        for ((profile, weight) in baseProfiles) {
            acc += weight
            if (roll < acc) return profile
        }
        return baseProfiles.last().first
    }

    private fun generateOne(index: Int, rng: Random): NightProfile {
        val labels = mutableListOf<String>()

        // Insomnie totale (~4%) : prioritaire, court-circuite le profil de
        // base A-E — reste compatible avec G/H/I/tvOff/tvNeverOn (une
        // personne anxieuse et éveillée peut très bien avoir un BPM
        // irrégulier, un signal bruité, ou une montre qui décroche).
        val neverSleeps = rng.nextFloat() < 0.04f

        val base = if (neverSleeps) null else pickBaseProfile(rng)
        base?.let { labels += it.letter } ?: labels.add("INSOMNIE")

        val awakeBpm = 60 + rng.nextInt(0, 31) // 60-90
        val asleepBpm = (42 + rng.nextInt(0, 17)).coerceAtMost(awakeBpm - 10) // 42-58, toujours < awakeBpm
        var bpmNoise = 1 + rng.nextInt(0, 3) // 1-3

        val groundTruthAsleepMinute = base?.let { rng.nextInt(it.groundTruthRange.first, it.groundTruthRange.last + 1) }
        val sleepOnsetMinute = (groundTruthAsleepMinute?.minus(ONSET_DURATION_MINUTES))?.coerceAtLeast(0) ?: 0
        val hesitant = base?.hesitant == true

        // --- Modificateurs indépendants ---

        // F — Réveil après endormissement (micro-réveil).
        val nightWakingMinutes = if (!neverSleeps && rng.nextFloat() < 0.25f) {
            labels += "F"
            listOf((groundTruthAsleepMinute!! + 30 + rng.nextInt(0, 300)).coerceAtMost(440))
        } else emptyList()

        // G — Nuit agitée (BPM + mouvements irréguliers).
        var restlessButLowBpm = false
        val extraAgitatedMovements = mutableListOf<Int>()
        if (rng.nextFloat() < 0.20f) {
            labels += "G"
            restlessButLowBpm = true
            bpmNoise += 2 + rng.nextInt(0, 3) // bruit supplémentaire
            val extraCount = 2 + rng.nextInt(0, 4)
            val windowStart = groundTruthAsleepMinute ?: 60
            extraAgitatedMovements += (0 until extraCount).map {
                (windowStart + rng.nextInt(0, (470 - windowStart).coerceAtLeast(1))).coerceIn(0, 470)
            }
        }

        // H — Signal bruité (capteur peu fiable, indépendant de l'agitation).
        if (rng.nextFloat() < 0.15f) {
            labels += "H"
            bpmNoise += 4 + rng.nextInt(0, 5) // bruit fortement augmenté
        }

        // I — Données manquantes (perte de connexion montre).
        val watchDropoutRange = if (rng.nextFloat() < 0.12f) {
            labels += "I"
            val start = 40 + rng.nextInt(0, 380)
            val length = 10 + rng.nextInt(0, 51) // 10-60 min
            start..(start + length)
        } else null

        // J — Faux signal de sommeil (baisse temporaire du BPM avant le vrai
        // endormissement) — seulement s'il reste assez de place avant
        // l'endormissement réel pour un creux complet + un retour à
        // l'éveil.
        val falseDipMinute = if (!neverSleeps && sleepOnsetMinute > 60 && rng.nextFloat() < 0.10f) {
            labels += "J"
            10 + rng.nextInt(0, (sleepOnsetMinute - 40).coerceAtLeast(1))
        } else null

        // Rafale de mouvements en s'installant au lit — réduite pour le
        // profil D (très fatigué : moins de temps/énergie pour s'agiter).
        val isFatigueProfile = base?.letter == "D"
        val settlingBurst = if (rng.nextFloat() < (if (isFatigueProfile) 0.4f else 0.90f)) {
            val burstCount = (if (isFatigueProfile) 1 else 3) + rng.nextInt(0, if (isFatigueProfile) 3 else 6)
            (0 until burstCount).map { rng.nextInt(0, 16) }.distinct()
        } else emptyList()

        val isolatedMovementMinutes = (settlingBurst + extraAgitatedMovements).distinct().sorted()

        // TV : éteinte seule en cours de nuit (~25%, cause n°1 identifiée le
        // 15 août) et TV jamais allumée (~10%), indépendants des profils A-J.
        val tvNeverOn = !neverSleeps && rng.nextFloat() < 0.10f
        val tvOn = !tvNeverOn
        val tvOffMinute = if (tvOn && !neverSleeps && rng.nextFloat() < 0.25f) {
            60 + rng.nextInt(0, 360)
        } else null

        // Réveil matinal (~15%), indépendant.
        val morningWakeMinute = if (!neverSleeps && rng.nextFloat() < 0.15f) 380 + rng.nextInt(0, 71) else null

        val restingBpmBaseline = (asleepBpm + rng.nextInt(-2, 6)).coerceAtLeast(35)

        return NightProfile(
            name = "Nuit #${index} [${labels.joinToString("+")}]",
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
            restlessButLowBpm = restlessButLowBpm,
            morningWakeMinute = morningWakeMinute,
            tvOffMinute = tvOffMinute,
            restingBpmBaseline = restingBpmBaseline,
            expectAsleep = !neverSleeps,
            expectAutoPause = !neverSleeps,
            expectNeverAsleep = neverSleeps,
            groundTruthAsleepMinute = groundTruthAsleepMinute,
            falseDipMinute = falseDipMinute,
            profileLabel = labels.joinToString("+")
        )
    }
}
