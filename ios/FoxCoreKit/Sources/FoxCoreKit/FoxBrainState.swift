import Foundation

/// Portage direct de `com.projectfox.foxoff.brain.FoxBrainState` (Kotlin).
/// Source de vérité unique de l'état, y compris pour l'UI (SwiftUI côté
/// iOS/watchOS, comme Compose l'observe côté Android via `StateFlow`).
///
/// Kotlin distingue `Instant` (horodatage absolu) et `LocalTime` (heure du
/// jour). Swift n'a pas d'équivalent direct à `LocalTime` dans la
/// bibliothèque standard : `Date` est utilisé partout ici par simplicité ;
/// à réévaluer si l'affichage nécessite explicitement une heure locale
/// sans date (`DateComponents` serait alors plus proche du Kotlin `LocalTime`).
public struct FoxBrainState: Equatable {
    // Statut global
    public var isMonitoring: Bool
    public var detectedSleepState: SleepState
    public var lastScore: FoxBrainScore
    public var lastEventTime: Date?

    // Statut montre
    public var watchConnected: Bool
    public var watchName: String
    public var watchBattery: Int
    public var watchIsCharging: Bool

    // Statut TV
    public var tvConnected: Bool
    public var tvName: String
    public var tvCurrentApp: String
    public var tvIsPaused: Bool
    public var tvLastCommand: String
    public var tvLastCommandTime: Date?

    // Données biologiques
    public var currentBpm: Int?
    public var minBpmToday: Int
    public var maxBpmToday: Int
    public var lastBpmTime: Date?
    public var movementMagnitude: Float

    public init(
        isMonitoring: Bool = false,
        detectedSleepState: SleepState = .awake,
        lastScore: FoxBrainScore = FoxBrainScore(sleepProbability: 0, confidence: 1, reason: "En attente de données..."),
        lastEventTime: Date? = nil,
        watchConnected: Bool = false,
        watchName: String = "Recherche...",
        watchBattery: Int = 0,
        watchIsCharging: Bool = false,
        tvConnected: Bool = false,
        tvName: String = "Déconnectée",
        tvCurrentApp: String = "Aucune",
        tvIsPaused: Bool = false,
        tvLastCommand: String = "Aucune",
        tvLastCommandTime: Date? = nil,
        currentBpm: Int? = nil,
        minBpmToday: Int = 0,
        maxBpmToday: Int = 0,
        lastBpmTime: Date? = nil,
        movementMagnitude: Float = 0
    ) {
        self.isMonitoring = isMonitoring
        self.detectedSleepState = detectedSleepState
        self.lastScore = lastScore
        self.lastEventTime = lastEventTime
        self.watchConnected = watchConnected
        self.watchName = watchName
        self.watchBattery = watchBattery
        self.watchIsCharging = watchIsCharging
        self.tvConnected = tvConnected
        self.tvName = tvName
        self.tvCurrentApp = tvCurrentApp
        self.tvIsPaused = tvIsPaused
        self.tvLastCommand = tvLastCommand
        self.tvLastCommandTime = tvLastCommandTime
        self.currentBpm = currentBpm
        self.minBpmToday = minBpmToday
        self.maxBpmToday = maxBpmToday
        self.lastBpmTime = lastBpmTime
        self.movementMagnitude = movementMagnitude
    }
}
