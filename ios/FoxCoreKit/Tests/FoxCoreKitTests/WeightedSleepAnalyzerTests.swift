import XCTest
@testable import FoxCoreKit

/// Portage de scénarios réalistes pour `WeightedSleepAnalyzer`, dans le même
/// esprit que les tests Kotlin de ce projet (voir
/// app/src/test/java/.../core/application/FoxCoreAutoPauseGateTest.kt côté
/// Android). NON EXÉCUTÉ — écrit sans toolchain Swift disponible ; à lancer
/// avec `swift test` une fois Xcode/macOS accessible.
final class WeightedSleepAnalyzerTests: XCTestCase {

    private func analyzer() -> WeightedSleepAnalyzer {
        WeightedSleepAnalyzer()
    }

    func test_tvTurnedOn_increasesSleepProbability() {
        let state = FoxBrainState()
        let score = analyzer().analyze(FoxBrainEvent(.tvTurnedOn), currentState: state)

        XCTAssertEqual(score.sleepProbability, 0.03, accuracy: 0.0001)
        XCTAssertEqual(score.reason, "TV allumée")
    }

    func test_significantMovement_decreasesSleepProbability() {
        var state = FoxBrainState()
        state.lastScore = FoxBrainScore(sleepProbability: 0.5, confidence: 0.85, reason: "test")

        let score = analyzer().analyze(FoxBrainEvent(.movementDetected(magnitude: 0.9)), currentState: state)

        XCTAssertEqual(score.sleepProbability, 0.35, accuracy: 0.0001)
        XCTAssertEqual(score.reason, "Mouvement important détecté")
    }

    func test_smallMovement_doesNotPenalize() {
        var state = FoxBrainState()
        state.lastScore = FoxBrainScore(sleepProbability: 0.5, confidence: 0.85, reason: "test")

        let score = analyzer().analyze(FoxBrainEvent(.movementDetected(magnitude: 0.1)), currentState: state)

        XCTAssertEqual(score.sleepProbability, 0.5, accuracy: 0.0001)
    }

    func test_walkingDetected_resetsProbabilityToZero() {
        var state = FoxBrainState()
        state.lastScore = FoxBrainScore(sleepProbability: 0.95, confidence: 0.85, reason: "test")

        let score = analyzer().analyze(FoxBrainEvent(.walkingDetected), currentState: state)

        XCTAssertEqual(score.sleepProbability, 0, accuracy: 0.0001)
        XCTAssertEqual(score.reason, "Utilisateur actif (Marche)")
    }

    func test_probabilityNeverExceedsOne() {
        var state = FoxBrainState()
        state.lastScore = FoxBrainScore(sleepProbability: 0.99, confidence: 0.85, reason: "test")

        let score = analyzer().analyze(FoxBrainEvent(.tvTurnedOn), currentState: state)

        XCTAssertLessThanOrEqual(score.sleepProbability, 1)
    }

    func test_probabilityNeverGoesBelowZero() {
        var state = FoxBrainState()
        state.lastScore = FoxBrainScore(sleepProbability: 0.05, confidence: 0.85, reason: "test")

        let score = analyzer().analyze(FoxBrainEvent(.movementDetected(magnitude: 1.0)), currentState: state)

        XCTAssertGreaterThanOrEqual(score.sleepProbability, 0)
    }
}

/// Reproduit `FoxCoreAutoPauseGateTest.kt` côté Kotlin : vérifie
/// `FoxBrain.onEvent` bout en bout plutôt qu'une fonction de garde isolée
/// (celle-ci vit dans FoxCore.kt côté Android, pas dans le package brain).
final class FoxBrainTests: XCTestCase {

    func test_sleepDetected_marksTvAsPaused() {
        let brain = FoxBrain(analyzer: WeightedSleepAnalyzer())

        brain.onEvent(FoxBrainEvent(.sleepDetected))

        XCTAssertTrue(brain.state.tvIsPaused)
    }

    func test_heartRateReceived_updatesMinMaxAndMarksWatchConnected() {
        let brain = FoxBrain(analyzer: WeightedSleepAnalyzer())

        brain.onEvent(FoxBrainEvent(.heartRateReceived(bpm: 70, source: "TEST")))
        brain.onEvent(FoxBrainEvent(.heartRateReceived(bpm: 60, source: "TEST")))
        brain.onEvent(FoxBrainEvent(.heartRateReceived(bpm: 80, source: "TEST")))

        XCTAssertTrue(brain.state.watchConnected)
        XCTAssertEqual(brain.state.currentBpm, 80)
        XCTAssertEqual(brain.state.minBpmToday, 60)
        XCTAssertEqual(brain.state.maxBpmToday, 80)
    }

    func test_watchDisconnected_clearsWatchConnected() {
        let brain = FoxBrain(analyzer: WeightedSleepAnalyzer())
        brain.onEvent(FoxBrainEvent(.watchConnected(name: "Test Watch")))

        brain.onEvent(FoxBrainEvent(.watchDisconnected))

        XCTAssertFalse(brain.state.watchConnected)
    }
}
