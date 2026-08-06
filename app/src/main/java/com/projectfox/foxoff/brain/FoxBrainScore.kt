package com.projectfox.foxoff.brain

/**
 * Result of a single analysis cycle.
 */
data class FoxBrainScore(
    /** Probability of sleep from 0.0 to 1.0 */
    val sleepProbability: Float,
    /** Confidence in this score from 0.0 to 1.0 */
    val confidence: Float,
    /** Human-readable reason for the score */
    val reason: String
)
