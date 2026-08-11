package com.projectfox.foxoff.core.automation

import android.content.Context
import android.util.Log
import com.projectfox.foxoff.tv.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SleepDetectionHistoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var context: Context

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns fakePrefs

        // record() journalise via FoxLogger -> android.util.Log, indisponible en test JVM pur.
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun record(secondsFromEpoch: Long, outcome: SleepDetectionOutcome) = SleepDetectionRecord(
        detectedAt = Instant.ofEpochSecond(secondsFromEpoch),
        sleepProbability = 0.93f,
        confidence = 0.88f,
        outcome = outcome
    )

    @Test
    fun `history is empty by default`() {
        assertTrue(SleepDetectionHistory.loadAll(context).isEmpty())
    }

    @Test
    fun `a recorded entry round-trips exactly through persistence`() {
        val entry = record(1_700_000_000L, SleepDetectionOutcome.PAUSED)

        SleepDetectionHistory.record(context, entry)
        val loaded = SleepDetectionHistory.loadAll(context)

        assertEquals(1, loaded.size)
        assertEquals(entry, loaded.single())
    }

    @Test
    fun `multiple entries persist in insertion order`() {
        val first = record(1_700_000_000L, SleepDetectionOutcome.PAUSED)
        val second = record(1_700_003_600L, SleepDetectionOutcome.CANCELLED)

        SleepDetectionHistory.record(context, first)
        SleepDetectionHistory.record(context, second)

        val loaded = SleepDetectionHistory.loadAll(context)

        assertEquals(listOf(first, second), loaded)
    }

    @Test
    fun `the reactive mirror reflects every recorded entry immediately`() {
        val entry = record(1_700_000_000L, SleepDetectionOutcome.PAUSED)

        SleepDetectionHistory.record(context, entry)

        assertEquals(listOf(entry), SleepDetectionHistory.records.value)
    }

    @Test
    fun `older entries are trimmed once the bound is exceeded`() {
        // 61 entries with MAX_RECORDS = 60 : the oldest one must be dropped.
        repeat(61) { i ->
            SleepDetectionHistory.record(context, record(1_700_000_000L + i, SleepDetectionOutcome.PAUSED))
        }

        val loaded = SleepDetectionHistory.loadAll(context)

        assertEquals(60, loaded.size)
        // The very first entry (offset 0) should have been trimmed away.
        assertEquals(Instant.ofEpochSecond(1_700_000_001L), loaded.first().detectedAt)
        assertEquals(Instant.ofEpochSecond(1_700_000_060L), loaded.last().detectedAt)
    }
}
