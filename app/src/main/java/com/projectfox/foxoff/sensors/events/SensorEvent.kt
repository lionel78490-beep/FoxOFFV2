package com.projectfox.foxoff.sensors.events

import com.projectfox.foxoff.core.events.FoxEvent
import com.projectfox.foxoff.sensors.model.HeartRateSample

sealed interface SensorEvent : FoxEvent {
    data class HeartRateReceived(val sample: HeartRateSample) : SensorEvent
    data class WatchInfoReceived(val name: String, val battery: Int, val isConnected: Boolean) : SensorEvent

    /**
     * La montre active (voir WatchSettings.getLastKnownNodeId) s'est
     * déconnectée au niveau Data Layer (onPeerDisconnected) — signal
     * event-driven, quasi immédiat, distinct d'une simple absence
     * temporaire de mesure BPM.
     */
    object WatchDisconnected : SensorEvent

    /**
     * Magnitude de mouvement agrégée (RMS sur ~30s, voir MovementEngine
     * côté montre) — contre-signal au score de sommeil, absent jusqu'ici
     * (correctif du faux positif du 2026-08-07, voir ROADMAP.md Phase 1/5).
     */
    data class MovementDetected(val magnitude: Float) : SensorEvent
}
