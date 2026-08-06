package com.projectfox.foxoff.wear.sensors.engines.exercise

import java.time.Instant

/**
 * Execution metrics for the ExerciseEngine.
 */
data class ExerciseStats(
    val batchCount: Long = 0,
    val sampleCount: Long = 0,
    val uptimeMillis: Long = 0,
    val lastSampleTime: Instant? = null
)
