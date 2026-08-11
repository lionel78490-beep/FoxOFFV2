/// Portage direct de `com.projectfox.foxoff.brain.FoxBrainAnalyzer` (Kotlin).
/// Contrat pour les moteurs capables de calculer un score de sommeil à
/// partir d'événements.
public protocol FoxBrainAnalyzer {
    /// Analyse un événement par rapport à l'état courant et renvoie un
    /// nouveau score.
    func analyze(_ event: FoxBrainEvent, currentState: FoxBrainState) -> FoxBrainScore
}
