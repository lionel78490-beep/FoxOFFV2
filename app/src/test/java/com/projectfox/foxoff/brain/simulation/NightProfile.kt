package com.projectfox.foxoff.brain.simulation

import kotlin.random.Random

/**
 * Catégories de nuits synthétiques rejouées par [NightSimulator] à travers
 * le vrai moteur de production (FoxBrain + WeightedSleepAnalyzer), voir
 * NightSimulationTest. Chaque catégorie couvre une situation réaliste
 * distincte plutôt qu'une simple variation aléatoire.
 */
enum class NightCategory {
    NORMAL,
    SLOW_ONSET,
    NIGHT_WAKING,
    ISOLATED_MOVEMENT,
    TV_NEVER_ON,
    WATCH_DROPOUT,
    INSOMNIA,
    RESTLESS_LOW_BPM,
    MORNING_WAKE,
    /** Nuit générée par ProceduralNightGenerator (2026-08-15), paramètres continus plutôt que catégoriels. */
    PROCEDURAL
}

/**
 * Description paramétrée d'une nuit simulée de 23h00 à 07h00 (480 minutes).
 * Voir [NightProfileGenerator] pour les 50 profils réels et
 * [NightSimulator] pour la traduction en séquence de FoxBrainEvent.
 */
data class NightProfile(
    val name: String,
    val category: NightCategory,
    val awakeBpm: Int,
    val asleepBpm: Int,
    val bpmNoise: Int = 2,
    /** Minute (depuis 23h00) où le BPM commence à baisser vers `asleepBpm`. Ignoré si `neverSleeps`. */
    val sleepOnsetMinute: Int = 25,
    /** Endormissement hésitant : oscillations supplémentaires pendant la rampe. */
    val hesitant: Boolean = false,
    /** Minutes de début de réveils nocturnes (BPM + mouvement remontent temporairement). */
    val nightWakingMinutes: List<Int> = emptyList(),
    /** Minutes de pics de mouvement isolés (sans réveil BPM associé). */
    val isolatedMovementMinutes: List<Int> = emptyList(),
    val tvOn: Boolean = true,
    /** Fenêtre de minutes sans aucune donnée montre (WatchDisconnected -> WatchConnected). */
    val watchDropoutRange: IntRange? = null,
    /** BPM élevé en continu toute la nuit (insomnie) — prioritaire sur tout le reste. */
    val neverSleeps: Boolean = false,
    /** Petits mouvements fréquents sous le seuil significatif (agitation sans réveil réel). */
    val restlessButLowBpm: Boolean = false,
    /** Minute à partir de laquelle le BPM remonte progressivement vers `awakeBpm` (réveil matinal). */
    val morningWakeMinute: Int? = null,
    /**
     * Minute à laquelle la TV s'éteint seule en cours de nuit (veille
     * automatique) — `null` = TV allumée toute la nuit (comportement
     * historique des 50 profils du 2026-08-14, inchangé). Ajouté le
     * 2026-08-15 : cause n°1 identifiée du retard de 6h08 constaté sur une
     * vraie nuit ce jour-là (voir RealNightReplayTest) — aucun des 50
     * profils d'origine ne testait ce cas.
     */
    val tvOffMinute: Int? = null,
    /**
     * Valeur passée à `FoxBrain.setRestingBpmBaseline()` avant le rejeu —
     * `null` = garder le défaut générique de `FoxBrainState` (70 bpm,
     * comportement historique inchangé). Ajouté le 2026-08-15 pour
     * `ProceduralNightGenerator`, dont les profils utilisent des valeurs
     * réalistes cohérentes avec la calibration inférée sur la vraie nuit
     * de Lionel (~50 bpm, voir RealNightReplayTest) plutôt que le générique.
     */
    val restingBpmBaseline: Int? = null,

    // --- Résultat attendu ---
    val expectAsleep: Boolean,
    val expectAutoPause: Boolean,
    val expectNeverAsleep: Boolean = false,
    /** Le score doit être repassé sous ASLEEP à la toute fin de la nuit simulée. */
    val expectEndsBelowAsleep: Boolean = false,
    /**
     * Vérité terrain explicite (minute depuis 23h00 où la personne est
     * VRAIMENT endormie, selon les paramètres choisis par le générateur —
     * pas re-déduite après coup) — `null` pour les nuits sans endormissement
     * (insomnie) ou pour les 50 profils catégoriels d'origine, qui utilisent
     * `expectAsleep`/`expectAutoPause` (booléens) plutôt qu'un délai chiffré.
     * Utilisée uniquement par `ConfigOptimizer` (2026-08-15) pour mesurer un
     * délai endormissement→pause, jamais par `NightSimulationTest`.
     */
    val groundTruthAsleepMinute: Int? = null,

    /**
     * Minute de début d'une baisse TEMPORAIRE du BPM vers `asleepBpm`
     * AVANT le vrai endormissement (`sleepOnsetMinute`), suivie d'une
     * remontée à `awakeBpm` — simule un "faux signal de sommeil" (profil
     * J, 2026-08-15) : le BPM ressemble un instant à du sommeil réel sans
     * que la personne se soit vraiment endormie. `null` = pas de faux
     * signal (comportement historique inchangé). Voir NightSimulator.bpmAt.
     */
    val falseDipMinute: Int? = null,

    /**
     * Étiquette lisible décrivant la composition du profil (ex: "B+F+H"
     * pour un endormissement normal + micro-réveil + signal bruité) —
     * uniquement pour le reporting/débogage de `ProfiledNightGenerator`
     * (2026-08-15), sans effet sur la simulation elle-même.
     */
    val profileLabel: String = ""
)

/**
 * Génère 50 nuits synthétiques réparties en 9 catégories réalistes (voir
 * tableau du plan). `Random` seedé par profil : résultats reproductibles
 * d'un run à l'autre, pas de flakiness liée au hasard.
 */
object NightProfileGenerator {

    fun generate(): List<NightProfile> {
        val profiles = mutableListOf<NightProfile>()
        var seed = 42_000L

        repeat(10) { i ->
            val rng = Random(seed++)
            profiles += NightProfile(
                name = "Nuit normale #${i + 1}",
                category = NightCategory.NORMAL,
                awakeBpm = 66 + rng.nextInt(-4, 5),
                asleepBpm = 47 + rng.nextInt(-3, 4),
                sleepOnsetMinute = 15 + rng.nextInt(0, 30),
                tvOn = true,
                expectAsleep = true,
                expectAutoPause = true
            )
        }

        repeat(5) { i ->
            val rng = Random(seed++)
            profiles += NightProfile(
                name = "Endormissement lent, BPM hésitant #${i + 1}",
                category = NightCategory.SLOW_ONSET,
                awakeBpm = 70 + rng.nextInt(-4, 5),
                asleepBpm = 48 + rng.nextInt(-3, 4),
                sleepOnsetMinute = 20 + rng.nextInt(0, 20),
                hesitant = true,
                tvOn = true,
                expectAsleep = true,
                expectAutoPause = true
            )
        }

        repeat(5) { i ->
            val rng = Random(seed++)
            profiles += NightProfile(
                name = "Réveil nocturne puis ré-endormissement #${i + 1}",
                category = NightCategory.NIGHT_WAKING,
                awakeBpm = 68 + rng.nextInt(-3, 4),
                asleepBpm = 47 + rng.nextInt(-3, 4),
                sleepOnsetMinute = 20 + rng.nextInt(0, 20),
                nightWakingMinutes = listOf(150 + rng.nextInt(0, 90)),
                tvOn = true,
                expectAsleep = true,
                expectAutoPause = true
            )
        }

        repeat(5) { i ->
            val rng = Random(seed++)
            profiles += NightProfile(
                name = "Mouvement isolé sans réveil réel #${i + 1}",
                category = NightCategory.ISOLATED_MOVEMENT,
                awakeBpm = 67 + rng.nextInt(-3, 4),
                asleepBpm = 47 + rng.nextInt(-3, 4),
                sleepOnsetMinute = 20 + rng.nextInt(0, 20),
                isolatedMovementMinutes = listOf(70 + rng.nextInt(0, 100)),
                tvOn = true,
                expectAsleep = true,
                expectAutoPause = true
            )
        }

        repeat(5) { i ->
            val rng = Random(seed++)
            profiles += NightProfile(
                name = "TV jamais allumée #${i + 1}",
                category = NightCategory.TV_NEVER_ON,
                awakeBpm = 67 + rng.nextInt(-3, 4),
                asleepBpm = 47 + rng.nextInt(-3, 4),
                sleepOnsetMinute = 20 + rng.nextInt(0, 20),
                tvOn = false,
                // shouldSendAutoPause() est une décision PURE qui ne
                // regarde pas tvConnected (voir FoxCore.kt) : la détection
                // du sommeil doit rester correcte même sans TV. Le fait
                // qu'aucune commande TV réelle ne parte alors est décidé
                // plus haut (tvController?.pause(), hors périmètre de ce
                // test) — pas une régression si expectAutoPause=true ici.
                expectAsleep = true,
                expectAutoPause = true
            )
        }

        repeat(5) { i ->
            val rng = Random(seed++)
            val dropStart = 180 + rng.nextInt(0, 60)
            profiles += NightProfile(
                name = "Montre déconnectée en cours de nuit #${i + 1}",
                category = NightCategory.WATCH_DROPOUT,
                awakeBpm = 67 + rng.nextInt(-3, 4),
                asleepBpm = 47 + rng.nextInt(-3, 4),
                sleepOnsetMinute = 20 + rng.nextInt(0, 20),
                watchDropoutRange = dropStart..(dropStart + 30),
                tvOn = true,
                expectAsleep = true,
                expectAutoPause = true
            )
        }

        repeat(5) { i ->
            val rng = Random(seed++)
            profiles += NightProfile(
                name = "Insomnie (BPM élevé toute la nuit) #${i + 1}",
                category = NightCategory.INSOMNIA,
                // >= 84 dans le pire cas (bpmNoise inclus) : reste
                // toujours au-dessus du seuil bas (baseline générique
                // 70 bpm x 1.12 = 78.4, voir WeightedSleepAnalyzer/
                // SleepScoringConfig.bpmDropThreshold — restingBpmBaseline
                // n'est jamais recalibré dans cette simulation, aucun appel
                // à FoxBrain.setRestingBpmBaseline). Un BPM ~70-78 non
                // recalibré est indiscernable du "bas" générique par
                // construction : ne représente pas une vraie insomnie.
                awakeBpm = 90 + rng.nextInt(-3, 6),
                bpmNoise = 3,
                asleepBpm = 90, // sans effet, neverSleeps=true
                neverSleeps = true,
                tvOn = true,
                expectAsleep = false,
                expectAutoPause = false,
                expectNeverAsleep = true
            )
        }

        repeat(5) { i ->
            val rng = Random(seed++)
            profiles += NightProfile(
                name = "Nuit agitée mais BPM bas #${i + 1}",
                category = NightCategory.RESTLESS_LOW_BPM,
                awakeBpm = 67 + rng.nextInt(-3, 4),
                asleepBpm = 47 + rng.nextInt(-3, 4),
                sleepOnsetMinute = 20 + rng.nextInt(0, 20),
                restlessButLowBpm = true,
                tvOn = true,
                expectAsleep = true,
                expectAutoPause = true
            )
        }

        repeat(5) { i ->
            val rng = Random(seed++)
            profiles += NightProfile(
                name = "Réveil matinal (fin de nuit) #${i + 1}",
                category = NightCategory.MORNING_WAKE,
                awakeBpm = 67 + rng.nextInt(-3, 4),
                asleepBpm = 47 + rng.nextInt(-3, 4),
                sleepOnsetMinute = 20 + rng.nextInt(0, 20),
                morningWakeMinute = 400 + rng.nextInt(0, 40),
                tvOn = true,
                expectAsleep = true,
                expectAutoPause = true,
                // PAS expectEndsBelowAsleep : découverte de la simulation
                // (2026-08-14), pas un bug du profil — WeightedSleepAnalyzer
                // n'a AUCUN mécanisme qui fait redescendre sleepProbability
                // quand le BPM remonte seul (sans mouvement significatif
                // associé, >movementThreshold) : le score reste bloqué à son
                // maximum toute la fin de nuit même après un réveil réel
                // sans agitation. Sans conséquence sur tvIsPaused (verrou à
                // sens unique, la TV ne se remet de toute façon jamais en
                // lecture automatiquement), mais peut afficher "Sommeil
                // actif, confiance élevée" sur l'Accueil longtemps après un
                // réveil calme. Signalé à l'utilisateur, non corrigé ici
                // (hors périmètre de ce plan — nécessiterait de modifier
                // WeightedSleepAnalyzer, pas seulement le simulateur).
                expectEndsBelowAsleep = false
            )
        }

        return profiles
    }
}
