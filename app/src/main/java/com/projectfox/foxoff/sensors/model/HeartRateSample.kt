package com.projectfox.foxoff.sensors.model

import java.time.Instant

data class HeartRateSample(
    val bpm: Float,
    val accuracy: Int,
    val timestamp: Instant,
    val backend: SensorBackend
)
