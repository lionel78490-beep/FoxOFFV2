package com.projectfox.foxoff.ui.dashboard

import android.util.Log
import com.projectfox.foxoff.brain.FoxBrainEvent
import com.projectfox.foxoff.core.application.FoxCore
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reproduit le bug matériel rapporté : la montre passe Hors ligne puis se
 * reconnecte SANS que l'écran Dashboard ne soit fermé/relancé ni qu'on
 * change d'onglet — exactement la chaîne réactive que
 * WatchPresenceCoordinator déclenche en production (FoxBrain.state ->
 * DashboardViewModel.uiState). Utilise le vrai singleton FoxCore, comme le
 * fait DashboardViewModel en production (aucune injection de dépendance
 * n'existe ici) — un double ne prouverait rien sur la propagation réelle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelWatchPresenceTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // DashboardViewModel.init appelle FoxDiagnostic.dumpCommunication(),
        // qui utilise android.util.Log — indisponible dans un test JVM pur.
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `watch disconnect then reconnect propagate live without recreating the ViewModel`() = runTest {
        // 1. État initial connecté.
        FoxCore.brain.onEvent(FoxBrainEvent.WatchConnected("Galaxy Watch8"))
        val viewModel = DashboardViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.watchConnected)
        assertEquals("Galaxy Watch8", viewModel.uiState.value.watchName)

        // 2. Émission WatchDisconnected (même événement que WatchPresenceCoordinator
        //    publie via FoxCore.eventBus une fois traduit par l'orchestrateur).
        FoxCore.brain.onEvent(FoxBrainEvent.WatchDisconnected)
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. DashboardUiState devient hors ligne SANS recréer le ViewModel.
        assertFalse(viewModel.uiState.value.watchConnected)

        // 4. Émission d'un nouveau WatchConnected (équivalent à la réception d'un
        //    WatchInfoReceived réel, traduit en WatchConnected par FoxCore.startOrchestration()).
        FoxCore.brain.onEvent(FoxBrainEvent.WatchConnected("Galaxy Watch8"))
        testDispatcher.scheduler.advanceUntilIdle()

        // 5. Retour à connecté, toujours la même instance de ViewModel/écran.
        assertTrue(viewModel.uiState.value.watchConnected)
        assertEquals("Galaxy Watch8", viewModel.uiState.value.watchName)
    }
}
