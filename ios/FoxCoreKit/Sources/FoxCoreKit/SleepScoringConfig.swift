/// Portage direct de `com.projectfox.foxoff.brain.SleepScoringConfig`
/// (Kotlin). Mêmes valeurs par défaut — à retoucher UNIQUEMENT en même
/// temps que l'équivalent Kotlin si on veut garder les deux plateformes
/// alignées sur le même comportement de détection.
public struct SleepScoringConfig {
    public var bpmDropBonus: Float
    public var stationaryDurationBonus: Float
    public var tvOnBonus: Float
    public var lateNightBonus: Float
    public var userInteractionPenalty: Float
    public var significantMovementPenalty: Float

    // Seuils
    public var bpmDropThreshold: Float
    public var movementThreshold: Float
    public var lateNightHour: Int

    public init(
        bpmDropBonus: Float = 0.05,
        stationaryDurationBonus: Float = 0.10,
        tvOnBonus: Float = 0.03,
        lateNightBonus: Float = 0.05,
        userInteractionPenalty: Float = 0.20,
        significantMovementPenalty: Float = 0.15,
        bpmDropThreshold: Float = 0.05,
        movementThreshold: Float = 0.5,
        lateNightHour: Int = 23
    ) {
        self.bpmDropBonus = bpmDropBonus
        self.stationaryDurationBonus = stationaryDurationBonus
        self.tvOnBonus = tvOnBonus
        self.lateNightBonus = lateNightBonus
        self.userInteractionPenalty = userInteractionPenalty
        self.significantMovementPenalty = significantMovementPenalty
        self.bpmDropThreshold = bpmDropThreshold
        self.movementThreshold = movementThreshold
        self.lateNightHour = lateNightHour
    }
}
