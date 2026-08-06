package com.projectfox.foxoff.wear.sensors.engines.exercise

/**
 * Represents the internal state of the ExerciseEngine.
 */
enum class ExerciseState {
    /** Engine is created but not initialized. */
    IDLE,
    /** Engine is preparing the ExerciseClient. */
    PREPARING,
    /** Exercise is actively running and collecting data. */
    RUNNING,
    /** Unexpected stop detected, attempting to restart. */
    AUTO_RECOVERING,
    /** Exercise was manually stopped. */
    STOPPED,
    /** Engine encountered an unrecoverable error. */
    ERROR
}
