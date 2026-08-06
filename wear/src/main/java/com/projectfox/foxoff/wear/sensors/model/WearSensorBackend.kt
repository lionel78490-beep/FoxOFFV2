package com.projectfox.foxoff.wear.sensors.model

/**
 * Identifies the underlying technology used on the watch.
 */
enum class WearSensorBackend {
    /** Standard Android SensorManager. */
    LEGACY,
    /** Wear OS Health Services MeasureClient. */
    HEALTH_SERVICES,
    /** Wear OS Health Services ExerciseClient. */
    EXERCISE,
    /** Unknown backend. */
    UNKNOWN
}
