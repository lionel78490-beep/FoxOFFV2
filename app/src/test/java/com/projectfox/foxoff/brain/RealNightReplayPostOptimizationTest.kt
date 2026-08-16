package com.projectfox.foxoff.brain

import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Rejoue le journal Historique EXACT d'une deuxième vraie nuit (15-16 août
 * 2026, `resources/night-logs/2026-08-15-post-optimization-night.log`),
 * cette fois avec la config de PRODUCTION ACTUELLE (`SleepScoringConfig()`,
 * les valeurs appliquées le matin du 15 août suite au framework
 * d'optimisation à 10 000 nuits synthétiques). Contrairement à
 * `RealNightReplayTest` (nuit précédente, config historique), cette nuit a
 * eu lieu APRÈS le changement — c'est la vérification "non revérifiée sur
 * une vraie nuit" que chaque commentaire de `SleepScoringConfig.kt`
 * réclamait explicitement.
 *
 * Écart réel constaté par Lionel : coucher 21h28, endormissement confirmé
 * par Samsung Health à 21h54 (26 min), mais FoxOFF n'a JAMAIS confirmé
 * ASLEEP ni mis la TV en pause de toute la nuit (aucun `SLEEP_STATE_CHANGE`
 * au-delà de DROWSY 40% dans le journal, qui couvre 21h28 à 7h40).
 *
 * Ce test est DIAGNOSTIQUE (pas verrou de non-régression comme
 * `RealNightReplayTest`) : il imprime la trajectoire complète du score pour
 * comprendre pourquoi, pas pour figer un comportement connu. Même limite de
 * fidélité que documentée dans `RealNightReplayTest` : le journal NightLog
 * ne capture visiblement pas CHAQUE `MovementDetected` réellement envoyé au
 * moteur (throttling côté capture) — un rejeu à partir du seul journal ne
 * peut donc pas être bit-exact toute la nuit, mais la TRAJECTOIRE globale
 * du score reste representative du problème.
 *
 * `restingBpmBaseline` calibré par balayage (comme pour la nuit précédente)
 * pour reproduire au mieux les 2 premiers points de contrôle du journal.
 */
class RealNightReplayPostOptimizationTest {

    private val anchorDate = LocalDateTime.of(2026, 8, 15, 0, 0, 0)

    private fun instantAt(dayOffset: Long, time: LocalTime): java.time.Instant =
        anchorDate.toLocalDate().plusDays(dayOffset).atTime(time).toInstant(ZoneOffset.UTC)

    private data class LogLine(val dayOffset: Long, val time: LocalTime, val type: String, val detail: String)

    private fun loadLog(): List<LogLine> {
        val resource = javaClass.classLoader!!.getResourceAsStream("night-logs/2026-08-15-post-optimization-night.log")
            ?: error("Journal introuvable dans les ressources de test")
        var dayOffset = 0L
        var lastTime: LocalTime? = null
        return resource.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(" | ", limit = 3)
                val timePart = parts[0].substringAfter(" ") // "15/08 21:28:25" -> "21:28:25"
                val time = LocalTime.parse(timePart)
                // Le journal traverse minuit (21h28 -> 07h40) : détecte le
                // passage au jour suivant par un retour en arrière de l'heure.
                if (lastTime != null && time < lastTime) dayOffset += 1
                lastTime = time
                LogLine(dayOffset, time, parts[1].trim(), parts[2].trim())
            }
    }

    private data class Checkpoint(
        val dayOffset: Long, val time: LocalTime,
        val expectedState: String, val expectedScore: Int, val expectedReason: String,
        val actualState: SleepState, val actualScore: Int, val actualReason: String
    )

    // Réglages réellement actifs AVANT le changement du 2026-08-15 (voir
    // RealNightReplayTest — même valeurs, dupliquées ici pour comparer les
    // deux configs sur cette DEUXIÈME nuit réelle, qui elle a eu lieu APRÈS
    // le changement).
    private val historicalConfig = SleepScoringConfig(
        bpmDropBonus = 0.18f,
        bpmDropThreshold = 0.12f,
        sustainedBpmDropDuration = Duration.ofMinutes(3),
        movementThreshold = 2.0f,
        significantMovementPenalty = 0.08f,
        tvTurnedOffMultiplier = 0.5f
    )

    private fun replay(baseline: Int, lines: List<LogLine>, fullTrace: Boolean = false, config: SleepScoringConfig = SleepScoringConfig()): Pair<List<Checkpoint>, FoxBrain> {
        val brain = FoxBrain(WeightedSleepAnalyzer(config), config)
        brain.setRestingBpmBaseline(baseline)
        val checkpoints = mutableListOf<Checkpoint>()

        // TV allumée en fond dès le coucher (comme la nuit précédente — le
        // journal ne montre que des transitions, jamais l'état initial).
        dispatch(brain, FoxBrainEvent.TVTurnedOn, instantAt(lines.first().dayOffset, lines.first().time))

        // Contrairement à la nuit du 15 août (un seul TV_OFF, jamais rallumée),
        // cette nuit a un cycle complet : éteinte (inférée, voir ci-dessous),
        // rallumée explicitement (TV_ON 01:15:53), rééteinte explicitement
        // (TV_OFF 03:27:28).
        var firstTvOffInferred = false

        for (line in lines) {
            val instant = instantAt(line.dayOffset, line.time)
            fun trace(label: String) {
                if (!fullTrace) return
                val s = brain.state.value
                val jour = if (line.dayOffset == 0L) "15/08" else "16/08"
                println(
                    "  [$jour ${line.time}] $label -> score=${Math.round(s.lastScore.sleepProbability * 100)}% " +
                        "état=${s.detectedSleepState} raison=${s.lastScore.reason}"
                )
            }
            when (line.type) {
                "MOVEMENT" -> {
                    val magnitude = Regex("Magnitude ([\\d,]+)").find(line.detail)!!
                        .groupValues[1].replace(',', '.').toFloat()
                    dispatch(brain, FoxBrainEvent.MovementDetected(magnitude), instant)
                    if (magnitude > config.movementThreshold) trace("MOVEMENT $magnitude (pénalisant)")
                }
                "HEART_RATE_TREND" -> {
                    val bpm = Regex("(\\d+) bpm").find(line.detail)!!.groupValues[1].toFloat()
                    dispatch(brain, FoxBrainEvent.HeartRateReceived(bpm, "REAL"), instant)
                    trace("HR $bpm bpm")
                }
                "TV_ON" -> { dispatch(brain, FoxBrainEvent.TVTurnedOn, instant); trace("TV_ON") }
                "TV_OFF" -> { dispatch(brain, FoxBrainEvent.TVTurnedOff, instant); trace("TV_OFF") }
                "SLEEP_STATE_CHANGE" -> {
                    // Premier "TV éteinte" jamais accompagné d'une ligne TV_OFF
                    // explicite dans ce format de journal (même quirk que la
                    // nuit précédente, voir RealNightReplayTest) : inférer
                    // l'événement UNE SEULE fois à partir du texte de la raison.
                    if (!firstTvOffInferred && line.detail.contains("TV éteinte")) {
                        firstTvOffInferred = true
                        dispatch(brain, FoxBrainEvent.TVTurnedOff, instant)
                    }
                    val expectedState = line.detail.substringBefore(" —").trim()
                    val expectedScore = Regex("score (\\d+)%").find(line.detail)!!.groupValues[1].toInt()
                    val expectedReason = line.detail.substringAfterLast("— ").trim()
                    val actual = brain.state.value
                    checkpoints += Checkpoint(
                        line.dayOffset, line.time,
                        expectedState, expectedScore, expectedReason,
                        actual.detectedSleepState, Math.round(actual.lastScore.sleepProbability * 100), actual.lastScore.reason
                    )
                }
            }
        }
        return checkpoints to brain
    }

    private fun dispatch(brain: FoxBrain, event: FoxBrainEvent, instant: java.time.Instant) {
        event.timestamp = instant
        brain.onEvent(event)
    }

    @Test
    fun `rejoue la nuit du 15-16 aout avec la config de production actuelle et trace la trajectoire du score`() {
        val lines = loadLog()

        // --- Calibration restingBpmBaseline : balayage pour minimiser
        // l'écart sur les 2 premiers points de contrôle (23:25 et 23:45),
        // même méthode que RealNightReplayTest. ---
        var bestBaseline = 50
        var bestError = Int.MAX_VALUE
        for (candidate in 40..65) {
            val (checkpoints, _) = replay(candidate, lines)
            val error = checkpoints.take(2).sumOf { Math.abs(it.expectedScore - it.actualScore) }
            if (error < bestError) {
                bestError = error
                bestBaseline = candidate
            }
        }
        println("=== Baseline calibrée : $bestBaseline (erreur sur les 2 premiers points de contrôle : $bestError) ===\n")

        println("=== Trace complète (chaque HR/mouvement pénalisant/TV) ===")
        val (checkpoints, brain) = replay(bestBaseline, lines, fullTrace = true)

        println("=== Trajectoire complète (attendu vs rejoué) ===")
        var maxActualScore = 0
        checkpoints.forEach { c ->
            maxActualScore = maxOf(maxActualScore, c.actualScore)
            val jour = if (c.dayOffset == 0L) "15/08" else "16/08"
            println(
                "[$jour ${c.time}] attendu=${c.expectedState} ${c.expectedScore}% (${c.expectedReason}) | " +
                    "rejoué=${c.actualState} ${c.actualScore}% (${c.actualReason})"
            )
        }

        val finalState = brain.state.value
        println(
            "\n=== État final (fin du journal, 16/08 07:40:44) === " +
                "score=${Math.round(finalState.lastScore.sleepProbability * 100)}% " +
                "état=${finalState.detectedSleepState} raison=${finalState.lastScore.reason}"
        )
        println("Score MAX atteint à un point de contrôle sur toute la nuit : $maxActualScore%")
        println(
            "\nRappel : aucun SLEEP_STATE_CHANGE ASLEEP ni PAUSE_EXECUTED dans le vrai journal de " +
                "cette nuit — le rejeu ci-dessus doit montrer POURQUOI, pas juste confirmer QUE."
        )
    }

    /**
     * Même nuit, mais rejouée avec la config HISTORIQUE (avant le
     * 2026-08-15) plutôt que la config actuelle — pour savoir si le
     * changement du 15 août a AGGRAVÉ le problème sur cette nuit précise,
     * ou si le problème aurait existé de toute façon.
     */
    @Test
    fun `compare config actuelle vs historique sur la meme nuit`() {
        val lines = loadLog()

        fun maxScore(config: SleepScoringConfig): Pair<Int, Int> {
            val (checkpoints, brain) = replay(50, lines, config = config)
            val maxCheckpoint = checkpoints.maxOfOrNull { it.actualScore } ?: 0
            val final = Math.round(brain.state.value.lastScore.sleepProbability * 100)
            return maxCheckpoint to final
        }

        val (maxCurrent, finalCurrent) = maxScore(SleepScoringConfig())
        val (maxHistorical, finalHistorical) = maxScore(historicalConfig)

        println("=== Comparaison sur la nuit du 15-16 août (baseline=50) ===")
        println("Config ACTUELLE (post-optimisation 2026-08-15) : score max aux points de contrôle=$maxCurrent%, score final=$finalCurrent%")
        println("Config HISTORIQUE (avant 2026-08-15)            : score max aux points de contrôle=$maxHistorical%, score final=$finalHistorical%")
    }

    /**
     * Teste le candidat `debounceMinBpmFloor` (voir SleepScoringConfig,
     * 2026-08-16) sur cette même vraie nuit : est-ce que le fait d'exiger
     * une confirmation avant de resserrer `minBpmToday` évite le blocage à
     * 0% observé toute la fin de la nuit ? Compare la config de production
     * ACTUELLE (déjà revenue à l'historique) avec et sans le debounce activé.
     */
    @Test
    fun `teste le candidat debounceMinBpmFloor sur la nuit du 15-16 aout`() {
        val lines = loadLog()

        fun run(config: SleepScoringConfig): Triple<Int, Int, Int> {
            val (checkpoints, brain) = replay(50, lines, config = config)
            val maxCheckpoint = checkpoints.maxOfOrNull { it.actualScore } ?: 0
            val final = Math.round(brain.state.value.lastScore.sleepProbability * 100)
            val minBpmFinal = brain.state.value.minBpmToday
            return Triple(maxCheckpoint, final, minBpmFinal)
        }

        val (maxWithout, finalWithout, minBpmWithout) = run(SleepScoringConfig(debounceMinBpmFloor = false))
        val (maxWith, finalWith, minBpmWith) = run(SleepScoringConfig(debounceMinBpmFloor = true))

        println("=== Candidat debounceMinBpmFloor sur la nuit du 15-16 août (config actuelle, baseline=50) ===")
        println("SANS debounce (comportement actuel) : score max=$maxWithout%, score final=$finalWithout%, minBpmToday final=$minBpmWithout")
        println("AVEC debounce                       : score max=$maxWith%, score final=$finalWith%, minBpmToday final=$minBpmWith")

        // --- Question suivante : le debounce seul suffit-il à sauver la
        // config OPTIMISÉE (rapide mais en échec, seuil 7% serré) plutôt
        // que de devoir revenir entièrement à l'historique ? Reconstitue
        // les valeurs post-optimisation du 15 août (plus dans les défauts
        // de production depuis le revert) pour le savoir. ---
        val postOptimizationConfig = SleepScoringConfig(
            bpmDropBonus = 0.143f,
            bpmDropThreshold = 0.070f,
            sustainedBpmDropDuration = java.time.Duration.ofMinutes(1),
            movementThreshold = 1.55f,
            significantMovementPenalty = 0.167f,
            tvTurnedOffMultiplier = 0.62f
        )
        val (maxOptNoDebounce, finalOptNoDebounce, minBpmOptNoDebounce) = run(postOptimizationConfig)
        val (maxOptDebounce, finalOptDebounce, minBpmOptDebounce) = run(postOptimizationConfig.copy(debounceMinBpmFloor = true))

        println("\n=== Config OPTIMISÉE (seuil 7% serré) avec/sans debounce, même nuit ===")
        println("Optimisée SANS debounce (= ce qui a échoué en vrai) : score max=$maxOptNoDebounce%, score final=$finalOptNoDebounce%, minBpmToday final=$minBpmOptNoDebounce")
        println("Optimisée AVEC debounce                             : score max=$maxOptDebounce%, score final=$finalOptDebounce%, minBpmToday final=$minBpmOptDebounce")
    }

    /**
     * Trace complète (chaque événement, pas seulement les points de
     * contrôle) de la config HISTORIQUE (actuellement en production) sur
     * cette même nuit, pour savoir si elle atteint réellement ASLEEP à un
     * moment ou si elle plafonne juste en dessous (PRE_SLEEP).
     */
    @Test
    fun `trace complete config historique actuelle sur la nuit du 15-16 aout`() {
        val lines = loadLog()
        println("=== Trace complète config HISTORIQUE (production actuelle) ===")
        val (checkpoints, brain) = replay(50, lines, fullTrace = true, config = historicalConfig)
        var maxScoreEver = 0
        var maxScoreAt: LocalTime? = null
        // Le trace() interne n'expose pas le max, on le recalcule depuis les checkpoints
        // + on ajoute un print du score après CHAQUE HR pour capter le pic exact.
        checkpoints.forEach { if (it.actualScore > maxScoreEver) { maxScoreEver = it.actualScore; maxScoreAt = it.time } }
        println("\nScore final : ${Math.round(brain.state.value.lastScore.sleepProbability * 100)}%")
        println("Score max (aux points de contrôle) : $maxScoreEver% à $maxScoreAt")
    }
}
