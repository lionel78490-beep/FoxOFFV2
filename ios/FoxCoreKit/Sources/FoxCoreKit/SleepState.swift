/// Portage direct de `com.projectfox.foxoff.brain.SleepState` (Kotlin).
public enum SleepState {
    /// Utilisateur pleinement éveillé (0-40%).
    case awake
    /// Fatigue légère détectée (40-70%).
    case drowsy
    /// État de pré-sommeil (70-90%).
    case preSleep
    /// Sommeil confirmé ou hautement probable (90%+).
    case asleep
}
