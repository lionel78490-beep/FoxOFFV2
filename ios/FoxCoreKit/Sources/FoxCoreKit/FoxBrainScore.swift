/// Portage direct de `com.projectfox.foxoff.brain.FoxBrainScore` (Kotlin).
/// Résultat d'un cycle d'analyse.
public struct FoxBrainScore: Equatable {
    /// Probabilité de sommeil, de 0.0 à 1.0.
    public let sleepProbability: Float
    /// Confiance dans ce score, de 0.0 à 1.0.
    public let confidence: Float
    /// Raison lisible du score.
    public let reason: String

    public init(sleepProbability: Float, confidence: Float, reason: String) {
        self.sleepProbability = sleepProbability
        self.confidence = confidence
        self.reason = reason
    }
}
