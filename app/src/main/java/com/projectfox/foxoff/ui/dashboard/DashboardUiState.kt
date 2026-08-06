package com.projectfox.foxoff.ui.dashboard

import com.projectfox.foxoff.brain.SleepState
import java.time.LocalTime

data class DashboardUiState(
    // Global Status
    val mainStatus: String = "Recherche de la montre...",
    val mainStatusColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Yellow,
    
    // Watch
    val watchConnected: Boolean = false,
    val watchName: String = "Aucune montre",
    val watchBattery: String = "--%",
    val watchConnectionType: String = "Bluetooth LE",
    
    // TV
    val tvConnected: Boolean = false,
    val tvName: String = "Non connectée",
    val tvCurrentApp: String = "Aucune",
    val tvLastCommand: String = "Aucune",
    val tvLastCommandTime: LocalTime? = null,
    
    // Heart Rate
    val currentBpm: Int? = null,
    val minBpmToday: Int = 58,
    val maxBpmToday: Int = 112,
    val lastBpmTime: LocalTime? = null,
    
    // Analysis
    val sleepState: SleepState = SleepState.AWAKE,
    val confidence: Int = 0,
    val analysisExplanation: String = "Analyse en attente des données cardiaques..."
)
