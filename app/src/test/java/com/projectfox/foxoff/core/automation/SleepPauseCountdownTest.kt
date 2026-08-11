package com.projectfox.foxoff.core.automation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Teste SleepPauseCountdown avec un scope entièrement VIRTUEL
 * (backgroundScope de kotlinx-coroutines-test) : aucune attente réelle,
 * même pour le délai de 10s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepPauseCountdownTest {

    private val countdownDuration = Duration.ofSeconds(10)

    private class Recorder {
        var startedCount = 0
        var executedCount = 0
        var cancelledCount = 0
    }

    private fun TestScope.newCountdown(recorder: Recorder) = SleepPauseCountdown(
        countdownDuration = countdownDuration,
        scope = backgroundScope,
        onCountdownStarted = { recorder.startedCount++ },
        onExecute = { recorder.executedCount++ },
        onCancelled = { recorder.cancelledCount++ }
    )

    @Test
    fun `start executes after the full delay without cancellation`() = runTest {
        val recorder = Recorder()
        val countdown = newCountdown(recorder)

        countdown.start()
        assertTrue(countdown.isPending)
        runCurrent()
        assertEquals(1, recorder.startedCount)

        advanceTimeBy(countdownDuration.toMillis() + 1)
        runCurrent()

        assertEquals(1, recorder.executedCount)
        assertEquals(0, recorder.cancelledCount)
        assertFalse(countdown.isPending)
    }

    @Test
    fun `cancel before the delay elapses prevents execution`() = runTest {
        val recorder = Recorder()
        val countdown = newCountdown(recorder)

        countdown.start()
        advanceTimeBy(countdownDuration.toMillis() / 2)
        runCurrent()

        countdown.cancel()
        runCurrent()

        assertEquals(1, recorder.cancelledCount)
        assertFalse(countdown.isPending)

        // Advance past where the original deadline would have fired.
        advanceTimeBy(countdownDuration.toMillis())
        runCurrent()

        assertEquals(0, recorder.executedCount)
    }

    @Test
    fun `calling start while already pending does not restart or duplicate`() = runTest {
        val recorder = Recorder()
        val countdown = newCountdown(recorder)

        countdown.start()
        advanceTimeBy(countdownDuration.toMillis() / 2)
        runCurrent()

        countdown.start() // ignored, already pending

        assertEquals(1, recorder.startedCount)

        advanceTimeBy(countdownDuration.toMillis() / 2 + 1)
        runCurrent()

        assertEquals(1, recorder.executedCount)
    }

    @Test
    fun `cancel with nothing pending is a no-op`() = runTest {
        val recorder = Recorder()
        val countdown = newCountdown(recorder)

        countdown.cancel()
        runCurrent()

        assertEquals(0, recorder.cancelledCount)
        assertEquals(0, recorder.executedCount)
    }

    @Test
    fun `a new cycle can start again after a previous execution completes`() = runTest {
        val recorder = Recorder()
        val countdown = newCountdown(recorder)

        countdown.start()
        advanceTimeBy(countdownDuration.toMillis() + 1)
        runCurrent()
        assertEquals(1, recorder.executedCount)

        countdown.start()
        advanceTimeBy(countdownDuration.toMillis() + 1)
        runCurrent()

        assertEquals(2, recorder.executedCount)
        assertEquals(2, recorder.startedCount)
    }
}
