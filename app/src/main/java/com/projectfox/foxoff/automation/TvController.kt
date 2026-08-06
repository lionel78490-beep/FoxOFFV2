package com.projectfox.foxoff.automation

/**
 * Interface for controlling external television devices.
 */
interface TvController {
    /**
     * Sends a pause command to the television.
     */
    suspend fun pause()
}
