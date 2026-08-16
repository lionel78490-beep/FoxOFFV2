package com.projectfox.foxoff.brain

import com.projectfox.foxoff.brain.simulation.NextGenConfig
import com.projectfox.foxoff.brain.simulation.NextGenScorer
import com.projectfox.foxoff.brain.simulation.NextGenStrategies
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Validation OBLIGATOIRE sur les 2 vraies nuits capturées à ce jour — leçon
 * de la régression du 15-16 août (voir ROADMAP.md Phase 5) : plus jamais
 * valider un mécanisme sur le seul synthétique avant de le proposer.
 * Rejoue les journaux réels à travers `NextGenScorer` (même classe que
 * `NextGenDetectionEngine`, alimentée ici par les vrais événements du
 * journal plutôt que par une génération synthétique).
 */
class NextGenRealNightValidationTest {

    private data class LogLine(val dayOffset: Long, val time: LocalTime, val type: String, val detail: String)

    private fun loadLog(resourceName: String): List<LogLine> {
        val resource = javaClass.classLoader!!.getResourceAsStream("night-logs/$resourceName")
            ?: error("Journal introuvable : $resourceName")
        var dayOffset = 0L
        var lastTime: LocalTime? = null
        return resource.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(" | ", limit = 3)
                val timePart = parts[0].substringAfter(" ")
                val time = LocalTime.parse(timePart)
                if (lastTime != null && time < lastTime) dayOffset += 1
                lastTime = time
                LogLine(dayOffset, time, parts[1].trim(), parts[2].trim())
            }
    }

    private val anchorDate = LocalDateTime.of(2026, 8, 15, 0, 0, 0)
    private fun instantAt(dayOffset: Long, time: LocalTime): Instant =
        anchorDate.toLocalDate().plusDays(dayOffset).atTime(time).toInstant(ZoneOffset.UTC)

    private data class RealNightResult(val delayToAsleepMinutes: Int?, val pauseTriggeredAt: Instant?)

    private fun replay(lines: List<LogLine>, config: SleepScoringConfig, strategy: NextGenConfig, restingBaseline: Int): RealNightResult {
        val startInstant = instantAt(lines.first().dayOffset, lines.first().time)
        val scorer = NextGenScorer(config, strategy, restingBaseline, tvOnAtStart = true)
        var firstTvOffInferred = false

        for (line in lines) {
            val instant = instantAt(line.dayOffset, line.time)
            when (line.type) {
                "MOVEMENT" -> {
                    val magnitude = Regex("Magnitude ([\\d,]+)").find(line.detail)!!
                        .groupValues[1].replace(',', '.').toFloat()
                    scorer.onMovement(magnitude, instant)
                }
                "HEART_RATE_TREND" -> {
                    val bpm = Regex("(\\d+) bpm").find(line.detail)!!.groupValues[1].toFloat()
                    scorer.onHeartRate(bpm, instant)
                }
                "TV_ON" -> scorer.onTvOn(instant)
                "TV_OFF" -> scorer.onTvOff(instant)
                "SLEEP_STATE_CHANGE" -> {
                    if (!firstTvOffInferred && line.detail.contains("TV éteinte")) {
                        firstTvOffInferred = true
                        scorer.onTvOff(instant)
                    }
                }
            }
        }
        val delay = scorer.reachedAsleepAt?.let { Duration.between(startInstant, it).toMinutes().toInt() }
        return RealNightResult(delay, scorer.pauseTriggeredAt)
    }

    @Test
    fun `valide les mecanismes retenus sur les 2 vraies nuits`() {
        val nightA = loadLog("2026-08-15-real-night.log")
        val nightB = loadLog("2026-08-15-post-optimization-night.log")
        val config = SleepScoringConfig() // production actuelle, inchangée

        val strategiesToTest = listOf(
            NextGenStrategies.BASELINE,
            NextGenStrategies.M1_PLANCHER_GLISSANT,
            NextGenStrategies.M2B_TENDANCE_VERROUILLEE,
            NextGenStrategies.M3_DENSITE_MOUVEMENT,
            NextGenStrategies.M4_LISSAGE_EMA
        ) + NextGenStrategies.combinations()

        println("=== Validation sur les 2 vraies nuits (baseline=50) ===")
        println("%-30s | %-22s | %-22s".format("Stratégie", "Nuit A (15 août)", "Nuit B (15-16 août)"))
        strategiesToTest.forEach { strategy ->
            val resultA = replay(nightA, config, strategy, restingBaseline = 50)
            val resultB = replay(nightB, config, strategy, restingBaseline = 50)
            println(
                "%-30s | délai=%-6s pause=%-6s | délai=%-6s pause=%-6s".format(
                    strategy.label.take(30),
                    resultA.delayToAsleepMinutes?.let { "${it}min" } ?: "jamais",
                    if (resultA.pauseTriggeredAt != null) "oui" else "non",
                    resultB.delayToAsleepMinutes?.let { "${it}min" } ?: "jamais",
                    if (resultB.pauseTriggeredAt != null) "oui" else "non"
                )
            )
        }
        println("\n=== FIN — aucun fichier de production modifié ===")
    }
}
