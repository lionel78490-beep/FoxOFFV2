package com.projectfox.foxoff.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fondation de persistance Room (voir DECISIONS.md ADR-004) — pas encore
 * consommée par l'UI (Phase 7 de ROADMAP.md). `endedAt` null signifie une
 * session en cours. `outcome` reprend le vocabulaire de `SleepState`
 * (brain/SleepState.kt) sous forme de chaîne plutôt qu'une dépendance
 * directe au module brain, pour garder cette entité stable même si
 * SleepState évolue.
 */
@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val minBpm: Int,
    val maxBpm: Int,
    val outcome: String
)
