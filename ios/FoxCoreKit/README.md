# FoxCoreKit — portage Swift de la logique de détection de sommeil

## Statut : NON VÉRIFIÉ

Ce package a été écrit sans accès à un Mac/Xcode/toolchain Swift. Rien n'a
été compilé ni exécuté. À faire dès qu'un environnement macOS est
disponible (voir `.claude/plans` pour le plan complet iOS/Apple Watch) :

```bash
cd "ios/FoxCoreKit"
swift build
swift test
```

Corriger toute erreur de compilation ou d'exécution avant d'aller plus loin
— même logique que le cycle "build + tests après chaque changement" suivi
côté Android tout au long de ce projet.

## Ce que contient ce package

Portage direct du package Kotlin `com.projectfox.foxoff.brain` (logique de
scoring du sommeil) et de `com.projectfox.foxoff.sensors.events.SensorEvent`
(frontière d'ingestion des données de fréquence cardiaque) — **aucune
dépendance à une API Apple** (pas de HealthKit, pas de WatchConnectivity)
volontairement, pour rester portable et testable sans matériel ni
simulateur.

| Fichier Swift | Équivalent Kotlin |
|---|---|
| `FoxBrain.swift` | `brain/FoxBrain.kt` |
| `FoxBrainState.swift` | `brain/FoxBrainState.kt` |
| `FoxBrainEvent.swift` | `brain/FoxBrainEvent.kt` |
| `FoxBrainScore.swift` | `brain/FoxBrainScore.kt` |
| `FoxBrainAnalyzer.swift` | `brain/FoxBrainAnalyzer.kt` |
| `WeightedSleepAnalyzer.swift` | `brain/WeightedSleepAnalyzer.kt` |
| `SleepScoringConfig.swift` | `brain/SleepScoringConfig.kt` |
| `SleepState.swift` | `brain/SleepState.kt` |
| `SensorEvent.swift` | `sensors/events/SensorEvent.kt` + `sensors/model/HeartRateSample.kt` |

## Différences volontaires par rapport au Kotlin

- **`FoxBrainEvent`** : Kotlin utilise une `sealed class` où chaque
  sous-type hérite d'un champ `timestamp` commun. Swift n'a pas
  d'équivalent direct pour un `enum` à cas associés — `FoxBrainEvent` est
  donc une `struct` qui porte `timestamp` à côté d'un `payload` (l'`enum`
  qui contient le contenu réel de l'événement).
- **`StateFlow` → `Combine`** : `FoxBrain.statePublisher`
  (`AnyPublisher<FoxBrainState, Never>`) est l'équivalent de
  `FoxBrain.state: StateFlow<FoxBrainState>` côté Kotlin. Pour l'observer
  depuis SwiftUI, envelopper `FoxBrain` dans un `ObservableObject` qui
  republie l'état via `@Published` (pas fait ici — ce package reste sans
  dépendance UI).
- **`LocalTime` → `Date`** : Kotlin distingue `Instant` (horodatage absolu)
  de `LocalTime` (heure du jour). Swift n'a pas de type `LocalTime` en
  bibliothèque standard ; `Date` est utilisé partout par simplicité. À
  revoir si l'affichage a explicitement besoin d'une heure sans date.
- **`SensorBackend`** : deux cas ajoutés (`healthKitWorkoutSession`,
  `healthKitObserverQuery`) pour les deux stratégies HealthKit identifiées
  lors de la recherche de faisabilité (voir plan) — à affiner une fois
  l'implémentation watchOS réelle commencée.

## Ce qui n'est PAS dans ce package (volontairement)

- Rien côté HealthKit (capture BPM) ni WatchConnectivity (transport
  montre↔iPhone) — ce sont les prochaines étapes, bloquées tant qu'un Mac
  n'est pas disponible pour créer un vrai projet Xcode.
- Le portage du protocole TV (Android TV Remote v2 : `polo.proto` /
  `remotemessage.proto`, pairing PIN + TLS mutuel) — pas commencé.
- L'équivalent de `SleepPauseCoordinator.kt` (notification + compte à
  rebours annulable) — dépend d'APIs de notification iOS
  (`UserNotifications`), pas encore écrit.
