package com.projectfox.foxoff.wear.sensors.api

import com.projectfox.foxoff.wear.sensors.model.WearHeartRateSample
import kotlinx.coroutines.flow.Flow

/**
 * Interface for watch-side heart rate collection engines.
 */
interface WearSensorEngine {
    /** Stream of heart rate samples. */
    val samples: Flow<WearHeartRateSample>

    /** Prepares the engine for use. */
    suspend fun initialize()

    /** Starts active monitoring. */
    suspend fun start()

    /** Stops active monitoring. */
    suspend fun stop()

    /** Releases all resources. */
    suspend fun shutdown()

    /** Returns diagnostic information about the engine. */
    fun getDiagnosticInfo(): String = "No info"
}
