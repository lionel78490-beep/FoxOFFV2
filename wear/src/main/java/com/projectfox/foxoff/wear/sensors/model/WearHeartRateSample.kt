package com.projectfox.foxoff.wear.sensors.model

import java.time.Instant

/**
 * Data model for a heart rate measurement on the watch.
 */
data class WearHeartRateSample(
    val bpm: Float,
    val accuracy: Int,
    val timestamp: Instant,
    val backend: WearSensorBackend
)
