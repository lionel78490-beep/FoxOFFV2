package com.projectfox.foxoff.ui.dashboard

import com.projectfox.foxoff.brain.SleepState
import java.time.LocalTime

data class DashboardUiState(
    // Global Status
    val mainStatus: String = "Recherche de la montre...",
    val mainStatusColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Yellow,
    
    // Watch
    val watchConnected: Boolean = false,
    // true si une montre a déjà été associée avec succès par le passé (même
    // si elle n'est pas connectée actuellement) — permet d'afficher "Hors
    // ligne" plutôt que "Aucune montre" pendant une reconnexion.
    val watchKnown: Boolean = false,
    val watchName: String = "Aucune montre",
    val watchBattery: String = "--%",
    // Remplace le libellé de statut normalement calculé (Connectée/Hors
    // ligne) quand une sous-phase de WatchPresenceCoordinator est en cours —
    // null en fonctionnement normal (voir WatchConnectionState).
    val watchStatusOverride: String? = null,
    // "Bluetooth LE" était affiché en dur alors que rien ne confirme que la
    // liaison est réellement locale (voir l'enquête sur isNearby en cours) —
    // libellé générique et honnête tant que ce n'est pas confirmé.
    val watchConnectionType: String = "Connectée via Wear OS",
    
    // TV — source de vérité unique : TvStateRepository (via
    // FoxCore.tvEngine.state), la même que celle utilisée par l'onglet TV
    // (RemoteScreen). tvStatus == null signifie "aucune TV mémorisée".
    // Ne pas confondre avec FoxBrainState.tvConnected, qui reste réservé au
    // moteur de scoring de sommeil (voir WeightedSleepAnalyzer) et n'est
    // plus utilisé pour l'affichage.
    val tvName: String = "Aucune TV associée",
    val tvStatus: com.projectfox.foxoff.tv.TvConnectionStatus? = null,
    val tvCurrentApp: String = "Aucune",
    val tvLastCommand: String = "Aucune",
    val tvLastCommandTime: LocalTime? = null,
    
    // Heart Rate
    val currentBpm: Int? = null,
    val minBpmToday: Int? = null,
    val maxBpmToday: Int? = null,
    val lastBpmTime: LocalTime? = null,
    
    // Analysis
    val sleepState: SleepState = SleepState.AWAKE,
    val confidence: Int = 0,
    val analysisExplanation: String = "Analyse en attente des données cardiaques..."
)
