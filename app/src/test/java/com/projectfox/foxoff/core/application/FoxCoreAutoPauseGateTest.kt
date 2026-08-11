package com.projectfox.foxoff.core.application

import com.projectfox.foxoff.brain.FoxBrainScore
import com.projectfox.foxoff.brain.FoxBrainState
import com.projectfox.foxoff.brain.SleepState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie FoxCore.shouldSendAutoPause : décision pure derrière le
 * déclenchement de la pause TV automatique, comportement central de
 * l'application (aucun réglage utilisateur ne le conditionne).
 *
 * FoxCore.startOrchestration() n'appelle SleepPauseCoordinator.onSleepDetected
 * (et donc, in fine, tvController?.pause()) que dans la branche
 * `if (shouldSendAutoPause(...))` — c'est le SEUL point d'appel de
 * tvController?.pause() dans la boucle de décision automatique. Prouver que
 * shouldSendAutoPause() renvoie false ici prouve donc qu'aucune commande TV
 * automatique n'est envoyée dans ce cas (voir FoxCore.kt, Brain Decision Loop).
 */
class FoxCoreAutoPauseGateTest {

    private val asleepHighConfidence = FoxBrainState(
        detectedSleepState = SleepState.ASLEEP,
        lastScore = FoxBrainScore(sleepProbability = 0.95f, confidence = 0.9f, reason = "test"),
        tvIsPaused = false
    )

    // Reproduit exactement le scénario signalé (capture "Sommeil probable" /
    // "Endormi" à 110 BPM) : currentBpm ne fait pas partie des conditions de
    // shouldSendAutoPause, mais on le fixe ici pour documenter la
    // correspondance avec le cas réel observé.
    private val reportedScenario = asleepHighConfidence.copy(currentBpm = 110)

    @Test
    fun `allows auto pause when user asleep with high confidence`() {
        assertTrue(FoxCore.shouldSendAutoPause(asleepHighConfidence))
    }

    @Test
    fun `allows auto pause for the reported 110 bpm ASLEEP scenario`() {
        assertTrue(FoxCore.shouldSendAutoPause(reportedScenario))
    }

    @Test
    fun `blocks auto pause when tv already marked as paused`() {
        val alreadyPaused = asleepHighConfidence.copy(tvIsPaused = true)

        assertFalse(FoxCore.shouldSendAutoPause(alreadyPaused))
    }

    @Test
    fun `blocks auto pause when confidence too low`() {
        val lowConfidence = asleepHighConfidence.copy(
            lastScore = FoxBrainScore(sleepProbability = 0.95f, confidence = 0.5f, reason = "test")
        )

        assertFalse(FoxCore.shouldSendAutoPause(lowConfidence))
    }

    @Test
    fun `blocks auto pause when user not asleep`() {
        val awake = asleepHighConfidence.copy(detectedSleepState = SleepState.AWAKE)

        assertFalse(FoxCore.shouldSendAutoPause(awake))
    }
}
