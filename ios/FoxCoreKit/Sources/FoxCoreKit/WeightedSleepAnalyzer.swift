import Foundation

/// Portage direct de `com.projectfox.foxoff.brain.WeightedSleepAnalyzer`
/// (Kotlin) : analyseur à base de règles pondérées. Même logique, mêmes
/// seuils — voir `SleepScoringConfig`. `.timeChanged` utilise le calendrier
/// courant (`Calendar.current`) comme équivalent de
/// `ZoneId.systemDefault()` côté Kotlin.
public final class WeightedSleepAnalyzer: FoxBrainAnalyzer {

    private let config: SleepScoringConfig

    public init(config: SleepScoringConfig = SleepScoringConfig()) {
        self.config = config
    }

    public func analyze(_ event: FoxBrainEvent, currentState: FoxBrainState) -> FoxBrainScore {
        var currentProb = currentState.lastScore.sleepProbability
        var reason = currentState.lastScore.reason

        switch event.payload {
        case .heartRateReceived(let bpm, _):
            // Baisse de BPM par rapport au minBpmToday (simplifié, comme côté Kotlin).
            let baseline = Float(currentState.minBpmToday)
            if baseline > 0 && bpm < baseline * (1 + config.bpmDropThreshold) {
                currentProb += config.bpmDropBonus
                reason = "Baisse de la fréquence cardiaque détectée"
            }

        case .movementDetected(let magnitude):
            if magnitude > config.movementThreshold {
                currentProb -= config.significantMovementPenalty
                reason = "Mouvement important détecté"
            }
            // Les petits mouvements ne pénalisent pas, comme côté Kotlin.

        case .stillnessDetected:
            // N'augmente le score que si la TV est allumée.
            if currentState.tvConnected {
                currentProb += config.stationaryDurationBonus
                reason = "Utilisateur immobile (5 min+)"
            }

        case .walkingDetected:
            currentProb = 0
            reason = "Utilisateur actif (Marche)"

        case .tvTurnedOff:
            currentProb *= 0.5
            reason = "TV éteinte"

        case .tvTurnedOn:
            currentProb += config.tvOnBonus
            reason = "TV allumée"

        case .screenUnlocked:
            currentProb -= config.userInteractionPenalty
            reason = "Interaction utilisateur (Écran déverrouillé)"

        case .timeChanged(let localTime):
            let hour = Calendar.current.component(.hour, from: localTime)
            if hour >= config.lateNightHour || hour < 5 {
                currentProb += config.lateNightBonus
                reason = "Heure tardive (\(hour)h)"
            }

        case .sleepCancelled:
            currentProb = 0
            reason = "Sommeil annulé par l'utilisateur"

        default:
            break // Les autres événements ne modifient pas directement le score.
        }

        let finalProb = min(max(currentProb, 0), 1)

        return FoxBrainScore(
            sleepProbability: finalProb,
            confidence: 0.85, // Les règles sont assez fiables.
            reason: reason
        )
    }
}
