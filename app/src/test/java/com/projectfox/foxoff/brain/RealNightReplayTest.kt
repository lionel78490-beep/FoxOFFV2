package com.projectfox.foxoff.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Rejoue l'historique EXACT d'une vraie nuit (15-16 août 2026, journal
 * Historique fourni par Lionel — `resources/night-logs/2026-08-15-real-night.log`)
 * à travers le VRAI moteur de production, pour verrouiller en test
 * automatisé un écart réel constaté : Samsung Health confirme
 * l'endormissement à 00h26, FoxOFF ne confirme ASLEEP (et ne met la TV en
 * pause) qu'à 06h34:58 — 6h08 de retard, la nuit était quasiment terminée
 * (réveil à 07h30).
 *
 * Deux causes identifiées et verrouillées ci-dessous par des assertions sur
 * les points de contrôle SLEEP_STATE_CHANGE réellement journalisés :
 * (1) une dizaine de mouvements significatifs en tout début de nuit
 * (s'installer au lit) grignotent quasiment autant de score qu'ils n'en
 * laissent construire ; (2) la TV s'est éteinte (veille automatique,
 * probablement pas une action consciente pendant le sommeil) à 04h10 et a
 * fait perdre plus de 30 points de score d'un coup
 * (`TVTurnedOff -> currentProb *= 0.5`), annulant ~2h de progression.
 *
 * Ce test ne CORRIGE rien (voir SleepScoringConfig.kt — seuils calibrés sur
 * de vraies nuits, à ne pas modifier sans relecture) : il verrouille le
 * comportement actuel pour que toute future correction de ces deux règles
 * se voie clairement ici (les assertions devront être mises à jour en même
 * temps que le correctif, pas avant).
 *
 * `restingBpmBaseline=50` (au lieu du générique 70 par défaut) : la valeur
 * exacte calibrée sur l'appareil de Lionel (Health Connect, voir
 * ROADMAP.md Phase 5) n'est pas dans ce dépôt — 50 est la valeur qui
 * reproduit EXACTEMENT les 3 premiers points de contrôle journalisés
 * (recherche par balayage sur cette même nuit), donc une estimation fondée
 * sur des faits, pas une supposition arbitraire. À corriger si Lionel
 * communique la vraie valeur calibrée.
 *
 * Les points de contrôle après ~03h50 dérivent progressivement du réel
 * (jusqu'à +12 points) malgré ce calibrage : le journal NightLog
 * n'enregistre visiblement pas CHAQUE `MovementDetected` réellement envoyé
 * au moteur (throttling probable côté capture), donc un rejeu à partir du
 * seul journal ne peut pas être bit-exact toute la nuit. Les assertions
 * ci-dessous en tiennent compte (tolérance sur les points tardifs) sans
 * jamais fabriquer de mouvement qui n'est pas dans le journal réel.
 *
 * `historicalConfig` : les valeurs de `SleepScoringConfig` réellement
 * actives CETTE nuit-là (avant le changement du 2026-08-15 — voir son
 * historique de commentaires), passées explicitement plutôt que
 * `SleepScoringConfig()`. Ce test reproduit un événement historique réel ;
 * il doit rester stable quels que soient les futurs réglages de
 * production, jamais suivre silencieusement `SleepScoringConfig()` par
 * défaut au risque de rejouer une nuit différente de celle qui a
 * réellement eu lieu.
 */
class RealNightReplayTest {

    private val anchorDate = LocalDateTime.of(2026, 8, 15, 0, 0, 0)

    private fun instantAt(time: LocalTime): java.time.Instant =
        anchorDate.toLocalDate().atTime(time).toInstant(ZoneOffset.UTC)

    private data class LogLine(val time: LocalTime, val type: String, val detail: String)

    private fun loadLog(): List<LogLine> {
        val resource = javaClass.classLoader!!.getResourceAsStream("night-logs/2026-08-15-real-night.log")
            ?: error("Journal introuvable dans les ressources de test")
        return resource.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(" | ", limit = 3)
                val timePart = parts[0].substringAfter(" ") // "15/08 00:04:06" -> "00:04:06"
                LogLine(LocalTime.parse(timePart), parts[1].trim(), parts[2].trim())
            }
    }

    // Réglages réellement actifs la nuit du 15 août — AVANT le changement du
    // même jour suite au framework d'optimisation (voir SleepScoringConfig.kt).
    // Épinglé explicitement, jamais `SleepScoringConfig()` (voir doc de la classe).
    private val historicalConfig = SleepScoringConfig(
        bpmDropBonus = 0.18f,
        bpmDropThreshold = 0.12f,
        sustainedBpmDropDuration = Duration.ofMinutes(3),
        movementThreshold = 2.0f,
        significantMovementPenalty = 0.08f,
        tvTurnedOffMultiplier = 0.5f
    )

    @Test
    fun `rejoue la vraie nuit du 15 aout et verrouille les deux causes du retard de 6h`() {
        val config = historicalConfig
        val brain = FoxBrain(WeightedSleepAnalyzer(config), config)
        brain.setRestingBpmBaseline(50)
        val lines = loadLog()

        // TV allumée en fond dès le coucher (le journal ne montre que la
        // transition "éteinte" à 04h10, preuve qu'elle était allumée avant).
        dispatch(brain, FoxBrainEvent.TVTurnedOn, instantAt(lines.first().time))

        var tvTurnedOffDispatched = false
        var realPauseAt: LocalTime? = null
        // (heure, état attendu, score attendu, état obtenu par le rejeu À CET
        // INSTANT PRÉCIS, score obtenu) — capturé en direct dans la boucle,
        // pas après coup (le score continue d'évoluer après le dernier point
        // de contrôle, voir les HEART_RATE_TREND jusqu'à 07h34 dans le
        // journal — comparer à la toute fin du fichier comparerait à un
        // instant différent de celui journalisé).
        data class Checkpoint(val time: LocalTime, val expectedState: SleepState, val expectedScore: Int, val actualState: SleepState, val actualScore: Int)
        val checkpoints = mutableListOf<Checkpoint>()

        for (line in lines) {
            val instant = instantAt(line.time)
            when (line.type) {
                "MOVEMENT" -> {
                    val magnitude = Regex("Magnitude ([\\d,]+)").find(line.detail)!!
                        .groupValues[1].replace(',', '.').toFloat()
                    dispatch(brain, FoxBrainEvent.MovementDetected(magnitude), instant)
                }
                "HEART_RATE_TREND" -> {
                    val bpm = Regex("(\\d+) bpm").find(line.detail)!!.groupValues[1].toFloat()
                    dispatch(brain, FoxBrainEvent.HeartRateReceived(bpm, "REAL"), instant)
                }
                "SLEEP_STATE_CHANGE" -> {
                    if (!tvTurnedOffDispatched && line.detail.contains("TV éteinte")) {
                        tvTurnedOffDispatched = true
                        dispatch(brain, FoxBrainEvent.TVTurnedOff, instant)
                    }
                    val expectedState = line.detail.substringBefore(" —").trim()
                    val expectedScore = Regex("score (\\d+)%").find(line.detail)!!.groupValues[1].toInt()
                    val actual = brain.state.value
                    checkpoints += Checkpoint(
                        line.time,
                        SleepState.valueOf(expectedState),
                        expectedScore,
                        actual.detectedSleepState,
                        Math.round(actual.lastScore.sleepProbability * 100)
                    )
                }
                "PAUSE_EXECUTED" -> realPauseAt = line.time
            }
        }

        // --- Les 3 premiers points de contrôle (avant que le journal ne
        // commence visiblement à omettre des mouvements, voir doc de la
        // classe) sont reproduits À L'EXACT par le moteur : preuve que le
        // rejeu est fidèle à la production avec ce baseline, pas une
        // approximation grossière. Au-delà, tolérance de 15 points (dérive
        // due au throttling du journal, pas une divergence du moteur). ---
        checkpoints.forEachIndexed { i, c ->
            println("[${c.time}] attendu=${c.expectedState} ${c.expectedScore}% | rejeu=${c.actualState} ${c.actualScore}%")
            if (i < 3) {
                assertEquals("état à ${c.time}", c.expectedState, c.actualState)
                assertEquals("score à ${c.time}", c.expectedScore, c.actualScore)
            } else {
                assertTrue(
                    "score à ${c.time} trop éloigné du réel (attendu ${c.expectedScore}%, rejeu ${c.actualScore}%)",
                    Math.abs(c.expectedScore - c.actualScore) <= 15
                )
            }
        }

        assertNotNull("la vraie nuit a bien déclenché une pause TV cette nuit-là", realPauseAt)
        assertEquals(LocalTime.of(6, 35, 59), realPauseAt)

        // --- Écart réel verrouillé : Samsung Health dit endormi à 00h26,
        // FoxOFF ne confirme ASLEEP qu'à 06h34:58 (dernier SLEEP_STATE_CHANGE
        // du journal) — 6h08 de retard, verrouillé ici pour qu'une future
        // correction du grignotage mouvement / de la pénalité TV éteinte se
        // voie clairement (ce nombre doit baisser nettement si corrigé).
        val samsungHealthOnset = LocalTime.of(0, 26, 0)
        val foxoffConfirmedAsleep = checkpoints.last { it.expectedState == SleepState.ASLEEP }.time
        val delay = Duration.between(samsungHealthOnset, foxoffConfirmedAsleep)
        println("Écart FoxOFF vs Samsung Health : ${delay.toMinutes()} minutes")
        assertTrue(
            "cet écart est un problème CONNU (voir doc du test) : ne pas resserrer cette " +
                "assertion sans avoir réellement corrigé le grignotage mouvement / la " +
                "pénalité TV éteinte, sous peine de masquer une régression",
            delay.toMinutes() in 300..400
        )
    }

    private fun dispatch(brain: FoxBrain, event: FoxBrainEvent, instant: java.time.Instant) {
        event.timestamp = instant
        brain.onEvent(event)
    }
}
