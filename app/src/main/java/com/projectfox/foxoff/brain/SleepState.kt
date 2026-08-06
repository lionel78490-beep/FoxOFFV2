package com.projectfox.foxoff.brain

/**
 * Represents the determined sleep state of the user.
 */
enum class SleepState {
    /** User is fully awake (0-40%). */
    AWAKE,
    /** Light fatigue detected (40-70%). */
    DROWSY,
    /** Pre-sleep state (70-90%). */
    PRE_SLEEP,
    /** Confirmed or highly probable sleep (90%+). */
    ASLEEP
}
