package com.projectfox.foxoff.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couverture de `FoxBrain.onEvent()` — le dispatch d'état, isolé de la
 * logique de scoring (`WeightedSleepAnalyzerTest` la couvre déjà) via un
 * analyseur factice à score contrôlé. Voir ROADMAP.md Phase 5.
 */
class FoxBrainTest {

    /** Renvoie toujours le même score, pour isoler le dispatch d'état de FoxBrain du calcul de score. */
    private class FixedScoreAnalyzer(private val score: FoxBrainScore) : FoxBrainAnalyzer {
        override fun analyze(event: FoxBrainEvent, currentState: FoxBrainState): FoxBrainScore = score
    }

    private fun brainWithScore(prob: Float, confidence: Float = 0.85f) =
        FoxBrain(FixedScoreAnalyzer(FoxBrainScore(prob, confidence, "test")))

    // --- État initial ---

    @Test
    fun `initial state has sensible defaults`() {
        val brain = brainWithScore(0f)

        val state = brain.state.value

        assertFalse(state.isMonitoring)
        assertEquals(SleepState.AWAKE, state.detectedSleepState)
        assertFalse(state.watchConnected)
        assertFalse(state.tvConnected)
        assertFalse(state.tvIsPaused)
        assertNull(state.currentBpm)
        assertEquals(0, state.minBpmToday)
        assertEquals(0, state.maxBpmToday)
    }

    // --- HeartRateReceived ---

    @Test
    fun `heart rate received marks watch connected and updates current bpm`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.HeartRateReceived(bpm = 72f, source = "TEST"))

        assertTrue(brain.state.value.watchConnected)
        assertEquals(72, brain.state.value.currentBpm)
        assertNotNull(brain.state.value.lastBpmTime)
    }

    @Test
    fun `heart rate received tracks min and max across multiple samples`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.HeartRateReceived(bpm = 70f, source = "TEST"))
        brain.onEvent(FoxBrainEvent.HeartRateReceived(bpm = 60f, source = "TEST"))
        brain.onEvent(FoxBrainEvent.HeartRateReceived(bpm = 80f, source = "TEST"))

        assertEquals(80, brain.state.value.currentBpm)
        assertEquals(60, brain.state.value.minBpmToday)
        assertEquals(80, brain.state.value.maxBpmToday)
    }

    // --- Watch events ---

    @Test
    fun `watch connected sets name and connected flag`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.WatchConnected(name = "Galaxy Watch 8"))

        assertTrue(brain.state.value.watchConnected)
        assertEquals("Galaxy Watch 8", brain.state.value.watchName)
    }

    @Test
    fun `watch disconnected clears connected flag`() {
        val brain = brainWithScore(0f)
        brain.onEvent(FoxBrainEvent.WatchConnected(name = "Galaxy Watch 8"))

        brain.onEvent(FoxBrainEvent.WatchDisconnected)

        assertFalse(brain.state.value.watchConnected)
    }

    @Test
    fun `battery changed updates level and charging state`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.BatteryChanged(level = 42, isCharging = true))

        assertEquals(42, brain.state.value.watchBattery)
        assertTrue(brain.state.value.watchIsCharging)
    }

    // --- TV events ---

    @Test
    fun `tv turned on and off toggles tv connected`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.TVTurnedOn)
        assertTrue(brain.state.value.tvConnected)

        brain.onEvent(FoxBrainEvent.TVTurnedOff)
        assertFalse(brain.state.value.tvConnected)
    }

    @Test
    fun `tv app changed updates current app, null becomes Aucune`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.TvAppChanged("Netflix"))
        assertEquals("Netflix", brain.state.value.tvCurrentApp)

        brain.onEvent(FoxBrainEvent.TvAppChanged(null))
        assertEquals("Aucune", brain.state.value.tvCurrentApp)
    }

    @Test
    fun `tv command sent updates last command`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.TvCommandSent("Pause", java.time.Instant.now()))

        assertEquals("Pause", brain.state.value.tvLastCommand)
        assertNotNull(brain.state.value.tvLastCommandTime)
    }

    // --- Movement / monitoring / sleep ---

    @Test
    fun `movement detected updates magnitude`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.MovementDetected(magnitude = 0.42f))

        assertEquals(0.42f, brain.state.value.movementMagnitude, 0.0001f)
    }

    @Test
    fun `monitoring started and stopped toggles isMonitoring`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.MonitoringStarted)
        assertTrue(brain.state.value.isMonitoring)

        brain.onEvent(FoxBrainEvent.MonitoringStopped)
        assertFalse(brain.state.value.isMonitoring)
    }

    @Test
    fun `sleep detected marks tv as paused`() {
        val brain = brainWithScore(0f)

        brain.onEvent(FoxBrainEvent.SleepDetected)

        assertTrue(brain.state.value.tvIsPaused)
    }

    // --- lastScore / lastEventTime toujours mis à jour ---

    @Test
    fun `every event updates lastScore and lastEventTime regardless of type`() {
        val brain = brainWithScore(0.42f, confidence = 0.77f)

        brain.onEvent(FoxBrainEvent.BatteryChanged(level = 10, isCharging = false))

        assertEquals(0.42f, brain.state.value.lastScore.sleepProbability, 0.0001f)
        assertEquals(0.77f, brain.state.value.lastScore.confidence, 0.0001f)
        assertNotNull(brain.state.value.lastEventTime)
    }

    // --- determineSleepState : mapping des seuils ---

    @Test
    fun `score of 0_95 is classified as ASLEEP`() {
        val brain = brainWithScore(0.95f)
        brain.onEvent(FoxBrainEvent.TVTurnedOn)
        assertEquals(SleepState.ASLEEP, brain.state.value.detectedSleepState)
    }

    @Test
    fun `score exactly at 0_90 boundary is classified as ASLEEP`() {
        val brain = brainWithScore(0.90f)
        brain.onEvent(FoxBrainEvent.TVTurnedOn)
        assertEquals(SleepState.ASLEEP, brain.state.value.detectedSleepState)
    }

    @Test
    fun `score just below 0_90 is classified as PRE_SLEEP`() {
        val brain = brainWithScore(0.8999f)
        brain.onEvent(FoxBrainEvent.TVTurnedOn)
        assertEquals(SleepState.PRE_SLEEP, brain.state.value.detectedSleepState)
    }

    @Test
    fun `score exactly at 0_70 boundary is classified as PRE_SLEEP`() {
        val brain = brainWithScore(0.70f)
        brain.onEvent(FoxBrainEvent.TVTurnedOn)
        assertEquals(SleepState.PRE_SLEEP, brain.state.value.detectedSleepState)
    }

    @Test
    fun `score just below 0_70 is classified as DROWSY`() {
        val brain = brainWithScore(0.6999f)
        brain.onEvent(FoxBrainEvent.TVTurnedOn)
        assertEquals(SleepState.DROWSY, brain.state.value.detectedSleepState)
    }

    @Test
    fun `score exactly at 0_40 boundary is classified as DROWSY`() {
        val brain = brainWithScore(0.40f)
        brain.onEvent(FoxBrainEvent.TVTurnedOn)
        assertEquals(SleepState.DROWSY, brain.state.value.detectedSleepState)
    }

    @Test
    fun `score just below 0_40 is classified as AWAKE`() {
        val brain = brainWithScore(0.3999f)
        brain.onEvent(FoxBrainEvent.TVTurnedOn)
        assertEquals(SleepState.AWAKE, brain.state.value.detectedSleepState)
    }
}
