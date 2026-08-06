package com.projectfox.foxoff.brain

import java.time.Instant
import java.time.LocalTime

/**
 * Global state of the Fox Brain.
 * This is the single source of truth for the entire application (including UI).
 */
data class FoxBrainState(
    // Global Status
    val isMonitoring: Boolean = false,
    val detectedSleepState: SleepState = SleepState.AWAKE,
    val lastScore: FoxBrainScore = FoxBrainScore(0f, 1f, "En attente de données..."),
    val lastEventTime: Instant? = null,

    // Watch Status
    val watchConnected: Boolean = false,
    val watchName: String = "Recherche...",
    val watchBattery: Int = 0,
    val watchIsCharging: Boolean = false,

    // TV Status
    val tvConnected: Boolean = false,
    val tvName: String = "Déconnectée",
    val tvCurrentApp: String = "Aucune",
    val tvIsPaused: Boolean = false,
    val tvLastCommand: String = "Aucune",
    val tvLastCommandTime: LocalTime? = null,

    // Biological Data
    val currentBpm: Int? = null,
    val minBpmToday: Int = 0,
    val maxBpmToday: Int = 0,
    val lastBpmTime: LocalTime? = null,
    val movementMagnitude: Float = 0f
)
