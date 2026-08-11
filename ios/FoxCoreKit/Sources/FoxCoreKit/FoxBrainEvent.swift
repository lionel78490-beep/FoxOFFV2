import Foundation

/// Portage de `com.projectfox.foxoff.brain.FoxBrainEvent` (Kotlin, `sealed
/// class`). Kotlin donne un timestamp par défaut à chaque sous-type via un
/// champ commun de la classe scellée ; Swift n'a pas d'équivalent direct
/// pour un `enum` à cas associés, d'où cette enveloppe `FoxBrainEvent` qui
/// porte le timestamp à côté du `payload` (le vrai contenu de l'événement).
public struct FoxBrainEvent {
    public let timestamp: Date
    public let payload: Payload

    public init(_ payload: Payload, timestamp: Date = Date()) {
        self.payload = payload
        self.timestamp = timestamp
    }

    public enum Payload {
        // Événements de fréquence cardiaque
        case heartRateReceived(bpm: Float, source: String)
        case passiveHeartRateReceived(bpm: Float)
        case exerciseHeartRateReceived(bpm: Float)

        // Événements de mouvement
        case movementDetected(magnitude: Float)
        case stillnessDetected

        // Événements TV
        case tvTurnedOn
        case tvTurnedOff
        case tvAppChanged(appName: String?)
        case tvCommandSent(command: String, time: Date)

        // Événements montre
        case watchConnected(name: String)
        case watchDisconnected
        case batteryChanged(level: Int, isCharging: Bool)

        // Événements téléphone / interface utilisateur
        case screenLocked
        case screenUnlocked

        // Événements de sommeil
        case sleepDetected
        case sleepCancelled
        case walkingDetected

        // Événements système
        case timeChanged(localTime: Date)
        case monitoringStarted
        case monitoringStopped
    }
}
