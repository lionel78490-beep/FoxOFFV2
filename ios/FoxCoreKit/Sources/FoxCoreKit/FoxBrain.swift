import Foundation
import Combine

/// Portage direct de `com.projectfox.foxoff.brain.FoxBrain` (Kotlin).
/// L'intelligence centrale de FoxOFF : oriente l'analyse multi-source et
/// maintient l'état global. `Combine.CurrentValueSubject` est l'équivalent
/// Apple du `StateFlow` Kotlin observé par Compose côté Android — côté
/// SwiftUI, `statePublisher` s'observe avec `.onReceive` ou en enveloppant
/// `FoxBrain` dans un `ObservableObject`.
public final class FoxBrain {

    private let analyzer: FoxBrainAnalyzer
    private let stateSubject: CurrentValueSubject<FoxBrainState, Never>

    public var state: FoxBrainState { stateSubject.value }
    public var statePublisher: AnyPublisher<FoxBrainState, Never> {
        stateSubject.eraseToAnyPublisher()
    }

    public init(analyzer: FoxBrainAnalyzer) {
        self.analyzer = analyzer
        self.stateSubject = CurrentValueSubject(FoxBrainState())
    }

    /// Point d'entrée principal pour les données entrantes.
    public func onEvent(_ event: FoxBrainEvent) {
        let currentScore = analyzer.analyze(event, currentState: stateSubject.value)

        var baseState = stateSubject.value
        baseState.lastScore = currentScore
        baseState.lastEventTime = event.timestamp
        baseState.detectedSleepState = Self.determineSleepState(currentScore)

        switch event.payload {
        case .heartRateReceived(let bpm, _):
            let bpmInt = Int(bpm)
            // Un BPM reçu EST une preuve applicative de présence : il doit
            // à lui seul ramener l'état à "Connectée", sans attendre un
            // watchInfo séparé (même principe que côté Kotlin).
            baseState.watchConnected = true
            baseState.currentBpm = bpmInt
            baseState.lastBpmTime = event.timestamp
            baseState.minBpmToday = baseState.minBpmToday == 0 ? bpmInt : min(baseState.minBpmToday, bpmInt)
            baseState.maxBpmToday = max(baseState.maxBpmToday, bpmInt)

        case .watchConnected(let name):
            baseState.watchConnected = true
            baseState.watchName = name

        case .watchDisconnected:
            baseState.watchConnected = false

        case .batteryChanged(let level, let isCharging):
            baseState.watchBattery = level
            baseState.watchIsCharging = isCharging

        case .tvTurnedOn:
            baseState.tvConnected = true

        case .tvTurnedOff:
            baseState.tvConnected = false

        case .tvAppChanged(let appName):
            baseState.tvCurrentApp = appName ?? "Aucune"

        case .tvCommandSent(let command, let time):
            baseState.tvLastCommand = command
            baseState.tvLastCommandTime = time

        case .movementDetected(let magnitude):
            baseState.movementMagnitude = magnitude

        case .monitoringStarted:
            baseState.isMonitoring = true

        case .monitoringStopped:
            baseState.isMonitoring = false

        case .sleepDetected:
            baseState.tvIsPaused = true

        default:
            break
        }

        stateSubject.send(baseState)
    }

    private static func determineSleepState(_ score: FoxBrainScore) -> SleepState {
        switch score.sleepProbability {
        case 0.90...: return .asleep
        case 0.70..<0.90: return .preSleep
        case 0.40..<0.70: return .drowsy
        default: return .awake
        }
    }
}
