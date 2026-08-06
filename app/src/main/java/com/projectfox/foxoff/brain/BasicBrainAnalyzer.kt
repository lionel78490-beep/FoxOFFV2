package com.projectfox.foxoff.brain

/**
 * Initial implementation of the Fox Brain analyzer using simple rules.
 */
class BasicBrainAnalyzer : FoxBrainAnalyzer {
    
    override fun analyze(event: FoxBrainEvent, currentState: FoxBrainState): FoxBrainScore {
        return when (event) {
            is FoxBrainEvent.HeartRateReceived -> {
                // Example: Drop in HR increases sleep probability
                val prob = if (event.bpm < 65) 0.5f else 0.1f
                FoxBrainScore(prob, 0.7f, "Fréquence cardiaque: ${event.bpm}")
            }
            is FoxBrainEvent.MovementDetected -> {
                val prob = if (event.magnitude < 0.1f) 0.6f else 0.0f
                FoxBrainScore(prob, 0.8f, "Mouvement: ${event.magnitude}")
            }
            is FoxBrainEvent.WatchConnected -> {
                FoxBrainScore(0f, 1.0f, "Montre connectée: ${event.name}")
            }
            else -> currentState.lastScore
        }
    }
}
