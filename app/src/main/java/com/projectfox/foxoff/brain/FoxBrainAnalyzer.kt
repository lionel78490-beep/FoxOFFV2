package com.projectfox.foxoff.brain

/**
 * Contract for engines capable of calculating sleep scores from events.
 */
interface FoxBrainAnalyzer {
    /**
     * Analyses an event relative to the current brain state and returns a new score.
     */
    fun analyze(event: FoxBrainEvent, currentState: FoxBrainState): FoxBrainScore
}
