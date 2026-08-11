package com.projectfox.foxoff.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Preuve que le câblage Room fonctionne réellement (pas seulement qu'il
 * compile) — voir ROADMAP.md Phase 3, fondation Room.
 *
 * Instrumenté (pas un test JVM pur) : `androidx.room:room-runtime-android`
 * n'expose que le builder basé sur `Context` (confirmé par décompilation de
 * l'AAR réel, `Room.android.kt`) — pas de variante sans Context utilisable
 * hors d'un module Kotlin Multiplatform. Robolectric a été volontairement
 * écarté (déconseillé par Google pour les bases Room en mémoire — problèmes
 * de threading documentés). Non exécuté dans cette session, faute
 * d'émulateur/appareil connecté — à lancer via `./gradlew
 * :app:connectedDebugAndroidTest` dès qu'un appareil est disponible.
 */
@RunWith(AndroidJUnit4::class)
class SleepSessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SleepSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.sleepSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertThenGetAllReturnsTheInsertedSession() = runTest {
        val session = SleepSessionEntity(
            startedAt = 1_000L,
            endedAt = 2_000L,
            minBpm = 45,
            maxBpm = 60,
            outcome = "ASLEEP"
        )

        dao.insert(session)

        val sessions = dao.getAll().first()
        assertEquals(1, sessions.size)
        assertEquals(45, sessions.first().minBpm)
        assertEquals(60, sessions.first().maxBpm)
        assertEquals("ASLEEP", sessions.first().outcome)
    }
}
