package com.projectfox.foxoff.core.service

import android.content.Context
import android.content.Intent
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Aucune décision n'est testée ici (déjà couverte par
 * ServiceReconciliationDecisionTest) — juste que ce receiver filtre bien
 * l'action et délègue à FoxServiceReconciliation.reconcileNow(), la seule
 * chose qui lui est propre.
 */
class FoxBootReceiverTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        mockkObject(FoxServiceReconciliation)
        every { FoxServiceReconciliation.reconcileNow(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(FoxServiceReconciliation)
        unmockkStatic(Log::class)
    }

    @Test
    fun `BOOT_COMPLETED triggers reconciliation`() {
        val context = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        FoxBootReceiver().onReceive(context, intent)

        verify(exactly = 1) { FoxServiceReconciliation.reconcileNow(context) }
    }

    @Test
    fun `an unrelated action is ignored`() {
        val context = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>()
        every { intent.action } returns "some.other.action"

        FoxBootReceiver().onReceive(context, intent)

        verify(exactly = 0) { FoxServiceReconciliation.reconcileNow(any()) }
    }
}
