package com.projectfox.foxoff.brain

/**
 * Configuration for the weighted sleep scoring system.
 * Values can be tuned to adjust the sensitivity of the detection.
 */
data class SleepScoringConfig(
    val bpmDropBonus: Float = 0.05f,      // +5%
    val stationaryDurationBonus: Float = 0.10f, // +10%
    val tvOnBonus: Float = 0.03f,        // +3%
    val lateNightBonus: Float = 0.05f,   // +5%
    val userInteractionPenalty: Float = 0.20f, // -20%
    val significantMovementPenalty: Float = 0.15f, // -15%
    
    // Thresholds
    val bpmDropThreshold: Float = 0.05f, // 5% drop from baseline
    val movementThreshold: Float = 0.5f,
    val lateNightHour: Int = 23
)
