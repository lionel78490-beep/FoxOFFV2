package com.projectfox.foxoff.brain

import java.time.Instant

/**
 * Represent all possible inputs for the Fox Brain engine.
 */
sealed class FoxBrainEvent {
    val timestamp: Instant = Instant.now()

    // Heart Rate Events
    data class HeartRateReceived(val bpm: Float, val source: String) : FoxBrainEvent()
    data class PassiveHeartRateReceived(val bpm: Float) : FoxBrainEvent()
    data class ExerciseHeartRateReceived(val bpm: Float) : FoxBrainEvent()

    // Movement Events
    data class MovementDetected(val magnitude: Float) : FoxBrainEvent()
    object StillnessDetected : FoxBrainEvent()

    // TV Events
    object TVTurnedOn : FoxBrainEvent()
    object TVTurnedOff : FoxBrainEvent()
    data class TvAppChanged(val appName: String?) : FoxBrainEvent()
    data class TvCommandSent(val command: String, val time: Instant) : FoxBrainEvent()

    // Device / Watch Events
    data class WatchConnected(val name: String) : FoxBrainEvent()
    object WatchDisconnected : FoxBrainEvent()
    data class BatteryChanged(val level: Int, val isCharging: Boolean) : FoxBrainEvent()

    // Phone / User Interface Events
    object ScreenLocked : FoxBrainEvent()
    object ScreenUnlocked : FoxBrainEvent()

    // Sleep Events
    object SleepDetected : FoxBrainEvent()
    object SleepCancelled : FoxBrainEvent()
    object WalkingDetected : FoxBrainEvent()

    // System Events
    data class TimeChanged(val localTime: Instant) : FoxBrainEvent()
    object MonitoringStarted : FoxBrainEvent()
    object MonitoringStopped : FoxBrainEvent()
}
