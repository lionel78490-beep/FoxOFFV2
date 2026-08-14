package com.projectfox.foxoff.core.application

import android.content.Context
import com.projectfox.foxoff.core.security.FoxEncryptedPrefs
import com.projectfox.foxoff.tv.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackgroundServiceSettingsTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var context: Context

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        // Le chiffrement réel (FoxEncryptedPrefs) exige l'Android Keystore,
        // absent des tests JVM — voir sa KDoc pour ce point d'injection.
        FoxEncryptedPrefs.setTestFactory { _, _ -> fakePrefs }
    }

    @After
    fun tearDown() {
        FoxEncryptedPrefs.resetForTests()
    }

    @Test
    fun `background surveillance is disabled by default`() {
        assertFalse(BackgroundServiceSettings.isEnabled(context))
    }

    @Test
    fun `enabling and disabling persists across reads`() {
        BackgroundServiceSettings.setEnabled(context, true)
        assertTrue(BackgroundServiceSettings.isEnabled(context))

        BackgroundServiceSettings.setEnabled(context, false)
        assertFalse(BackgroundServiceSettings.isEnabled(context))
    }

    @Test
    fun `the prompt has not been seen by default`() {
        assertFalse(BackgroundServiceSettings.hasSeenPrompt(context))
    }

    @Test
    fun `marking the prompt as seen persists across reads and is independent of the enabled flag`() {
        BackgroundServiceSettings.markPromptSeen(context)

        assertTrue(BackgroundServiceSettings.hasSeenPrompt(context))
        // Répondre "Plus tard" marque la proposition comme vue sans jamais
        // activer la surveillance elle-même.
        assertFalse(BackgroundServiceSettings.isEnabled(context))
    }
}
