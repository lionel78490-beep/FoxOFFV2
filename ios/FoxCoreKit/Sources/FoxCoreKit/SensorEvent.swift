import Foundation

/// Portage de `com.projectfox.foxoff.sensors.events.SensorEvent` (Kotlin).
/// Frontière volontairement agnostique de la source : que le BPM vienne de
/// HealthKit (Apple Watch) ou, côté Android, de la Data Layer API (Wear
/// OS), c'est ce type qui alimente `FoxBrain` en aval — voir
/// `FoxBrainEvent.heartRateReceived`, construit à partir de `HeartRateSample.bpm`.
public enum SensorEvent {
    case heartRateReceived(HeartRateSample)
    case watchInfoReceived(name: String, battery: Int, isConnected: Bool)
    case watchDisconnected
}

/// Portage de `com.projectfox.foxoff.sensors.model.HeartRateSample` (Kotlin).
public struct HeartRateSample {
    public let bpm: Float
    public let accuracy: Int
    public let timestamp: Date
    public let backend: SensorBackend

    public init(bpm: Float, accuracy: Int, timestamp: Date = Date(), backend: SensorBackend) {
        self.bpm = bpm
        self.accuracy = accuracy
        self.timestamp = timestamp
        self.backend = backend
    }
}

/// Portage de `com.projectfox.foxoff.sensors.model.SensorBackend` (Kotlin),
/// avec deux cas ajoutés pour distinguer les deux stratégies HealthKit
/// identifiées côté recherche (session d'entraînement vs livraison
/// passive observée) — à affiner une fois l'implémentation watchOS réelle
/// commencée.
public enum SensorBackend {
    case legacy
    case passive
    case exercise
    case unknown
    case healthKitWorkoutSession
    case healthKitObserverQuery
}
