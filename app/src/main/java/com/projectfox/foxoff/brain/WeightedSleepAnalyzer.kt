package com.projectfox.foxoff.brain

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Advanced rule-based analyzer using a weighted scoring system.
 */
class WeightedSleepAnalyzer(
    private val config: SleepScoringConfig = SleepScoringConfig()
) : FoxBrainAnalyzer {

    override fun analyze(event: FoxBrainEvent, currentState: FoxBrainState): FoxBrainScore {
        var currentProb = currentState.lastScore.sleepProbability
        var reason = currentState.lastScore.reason

        when (event) {
            is FoxBrainEvent.HeartRateReceived -> {
                // Tant qu'aucun vrai minBpmToday n'existe encore (avant le
                // premier échantillon de la session), on s'appuie sur
                // restingBpmBaseline (moyenne générique au repos) plutôt que
                // de désactiver la règle entièrement — voir FoxBrainState.
                // Dès qu'un minBpmToday réel apparaît, il prend le dessus :
                // la calibration devient alors propre à l'utilisateur.
                val baseline = if (currentState.minBpmToday > 0) {
                    currentState.minBpmToday.toFloat()
                } else {
                    currentState.restingBpmBaseline.toFloat()
                }
                val belowThreshold = event.bpm < baseline * (1 + config.bpmDropThreshold)
                val since = currentState.bpmBelowBaselineSince
                // Le point de référence pour la PROCHAINE échéance est le
                // dernier octroi s'il y en a eu un, sinon le tout début de
                // l'épisode — permet un octroi PÉRIODIQUE (toutes les
                // sustainedBpmDropDuration) tant que le BPM reste bas,
                // plutôt qu'un octroi unique pour tout l'épisode (qui
                // plafonnait le score bien trop bas pour jamais atteindre
                // ASLEEP — régression corrigée le 2026-08-08).
                val reference = currentState.lastBpmDropBonusAt ?: since
                val due = reference != null &&
                        !Duration.between(reference, event.timestamp).isNegative &&
                        Duration.between(reference, event.timestamp) >= config.sustainedBpmDropDuration

                if (belowThreshold && due) {
                    currentProb += config.bpmDropBonus
                    reason = "Baisse soutenue de la fréquence cardiaque détectée"
                }
            }

            is FoxBrainEvent.MovementDetected -> {
                if (event.magnitude > config.movementThreshold) {
                    currentProb -= config.significantMovementPenalty
                    reason = "Mouvement important détecté"
                } else {
                    // Small movements don't penalize much, or could be neutral
                }
            }

            is FoxBrainEvent.StillnessDetected -> {
                // Only increase score if TV is ON
                if (currentState.tvConnected) {
                    currentProb += config.stationaryDurationBonus
                    reason = "Utilisateur immobile (5 min+)"
                }
            }

            is FoxBrainEvent.WalkingDetected -> {
                currentProb = 0f
                reason = "Utilisateur actif (Marche)"
            }

            is FoxBrainEvent.TVTurnedOff -> {
                // Could potentially reset or lower score
                currentProb *= 0.5f
                reason = "TV éteinte"
            }

            is FoxBrainEvent.TVTurnedOn -> {
                currentProb += config.tvOnBonus
                reason = "TV allumée"
            }

            is FoxBrainEvent.ScreenUnlocked -> {
                currentProb -= config.userInteractionPenalty
                reason = "Interaction utilisateur (Écran déverrouillé)"
            }

            is FoxBrainEvent.TimeChanged -> {
                val hour = LocalDateTime.ofInstant(event.localTime, ZoneId.systemDefault()).hour
                if (hour >= config.lateNightHour || hour < 5) {
                    currentProb += config.lateNightBonus
                    reason = "Heure tardive (${hour}h)"
                }
            }
            
            is FoxBrainEvent.SleepCancelled -> {
                currentProb = 0f
                reason = "Sommeil annulé par l'utilisateur"
            }
            
            else -> {
                // Other events don't directly change the score yet
            }
        }

        // Clamp probability between 0.0 and 1.0
        val finalProb = currentProb.coerceIn(0f, 1f)
        
        return FoxBrainScore(
            sleepProbability = finalProb,
            confidence = 0.85f, // Rules are quite reliable
            reason = reason
        )
    }
}
