# FoxOFF V2 — Feuille de route technique

> Document de référence du projet, à tenir à jour à chaque phase franchie.
> Dernière mise à jour : 2026-08-07 (v5 — chantier prioritaire temporaire
> "Application complète et utilisable" CLOS, tous les points de sa liste
> "manque ou cassé" corrigés et vérifiés (build+tests) ; deux points de la
> Phase 2 traités en avance (seuils déplacés vers `SleepScoringConfig`,
> `FoxModule` mort supprimé). Reprise de la Phase 1 (validation matérielle
> `ASLEEP` + Task 1.6) toujours en attente d'une nuit de test réelle côté
> utilisateur. Chantier annexe non numéroté ouvert en parallèle : support
> multi-marques montre (Garmin Connect IQ opérationnel côté code, iOS/Apple
> Watch en pause faute d'accès à un Mac) — hors périmètre V2 initial, voir
> DECISIONS.md).

## Vision

FoxOFF détecte l'endormissement de l'utilisateur via une montre Wear OS (fréquence
cardiaque + mouvement) et coupe automatiquement la TV Android (via le protocole
Android TV Remote v2). V2 fait passer le projet d'un prototype fonctionnel par
morceaux à une application **professionnelle, modulaire, testée et
publiable sur le Play Store**.

Le projet couvre 7 sous-systèmes :

| Sous-système | Rôle | État actuel |
|---|---|---|
| App Android (téléphone) | UI, orchestration, réception capteurs | Fonctionnel, onboarding persistant, réglages complets |
| Wear OS | Capture BPM/mouvement, envoi au téléphone | Collecte réelle en place (Health Services, passive + exercise), validée sur matériel réel |
| Communication Watch↔Phone | Data Layer API (MessageClient), abstraite derrière `WatchTransport` | Fonctionnelle et validée (Wear OS) ; `GarminTransport` (Connect IQ) écrit et compilé, non validé sur montre physique |
| TV Engine | Découverte, appairage, contrôle Android TV | Appairage et contrôle en production déjà fonctionnels (protobuf généré) ; du code legacy orphelin et une double identité TLS restent à nettoyer (Phase 4) |
| Sleep Engine / Fox Brain | Scoring pondéré de probabilité de sommeil | Moteur à règles fonctionnel, seuils centralisés dans `SleepScoringConfig`, 43 tests unitaires (Phase 5), calibration BPM de repos via Health Connect |
| Automatisation | Service d'orchestration, fiabilité background | Foreground service + notification + heartbeat en place ; robustesse Doze/OEM (Phase 6) pas encore formalisée |
| UI | Onboarding + Dashboard Compose | Réglages, onglet Santé et onglet Historique (NightLog) fonctionnels ; graphiques/historique multi-nuits encore à faire (Phase 7) |

**Dépôt sous git, CI GitHub Actions et outillage de test (JUnit + MockK +
kotlinx-coroutines-test + Turbine) en place depuis le 2026-08-06** — voir
Phase 0 ci-dessous, clôturée.

## Principes directeurs

1. **La boucle réelle avant l'architecture.** Watch → Phone → Brain doit tourner avec de vraies données (Phase 1) avant toute migration architecturale massive. Refactorer une boucle qu'on n'a jamais vue fonctionner en vrai, c'est refactorer à l'aveugle.
2. **Stabiliser, puis seulement refactorer.** La Phase "architecture" d'origine est scindée en deux : une stabilisation fonctionnelle minimale (corrections ciblées, pas de refonte) puis un refactor architectural progressif, module par module, jamais en un seul gros commit.
3. **Chaque phase se termine sur une app qui compile et tourne** — pas de refonte "big bang".
4. **Réutiliser le code validé** : `TvPairingManager`/`PoloProto` (pairing), `WeightedSleepAnalyzer`/`SleepScoringConfig` (scoring), le thème Compose, l'onboarding existant.
5. **Portée initiale volontairement restreinte** : une seule TV fiable, pas de ML embarqué, pas de découpe Gradle multi-module avancée tant que ce n'est pas justifié par un besoin réel (voir "Décisions repoussées").
6. **Definition of Done uniforme** : aucune phase n'est "terminée" sans satisfaire le DoD ci-dessous, en plus de son critère de sortie spécifique.

### Definition of Done (DoD) — obligatoire pour **toute** phase

Une phase n'est close que si, en plus de son critère de sortie fonctionnel propre :

- [ ] **Build debug** réussi
- [ ] **Build release** réussi (signé ou non, mais le build passe)
- [ ] **Tests verts** (unitaires + instrumentés concernés par la phase)
- [ ] **Parcours principal testé manuellement** sur appareil réel
- [ ] **Absence de régression connue** sur les fonctionnalités des phases précédentes
- [ ] **Documentation mise à jour** (ce fichier + doc du module concerné)
- [ ] **Commit Git dédié** à la phase (message clair, diff revue avant merge)

---

## Matrice de dépendances entre phases

| Phase | Dépend de | Débloque | Remarque |
|---|---|---|---|
| 0 — Socle projet | — | 1 | Rien ne commence sans git/CI/tests |
| 1 — Boucle Watch→Phone→Brain | 0 | 2, 5 | Le cœur fonctionnel du produit |
| 2 — Stabilisation fonctionnelle minimale | 1 | 3 | Corrige ce que la Phase 1 a révélé, avant de refactorer |
| 3 — Refactor architectural progressif | 2 | 4, 5, 6 | Jamais avant une base stabilisée |
| 4 — TV Engine durci | 3 | 7 | Bâti sur les fondations de la Phase 3 |
| 5 — Sleep Engine / Fox Brain V2 | 1, 3 | 7, 8 | Besoin de vraies données (Phase 1) + de la base testable (Phase 3) |
| 6 — Automatisation & fiabilité runtime | 3 | 7 | Orchestrateur définitif, construit sur la Phase 3 |
| 7 — UI historique & réglages | 4, 5, 6 | 8, 9 | Agrège TV + Brain + Automatisation |
| 8 — Personnalisation avancée / IA | 5, 7 | — | **Ne bloque pas** la Phase 9 — peut courir en parallèle ou après |
| 9 — Préparation Play Store | 7 | — | Ne nécessite **pas** la Phase 8 pour un premier envoi |

**Lecture pratique** : une fois la Phase 7 terminée, la Phase 8 (IA/personnalisation)
et la Phase 9 (Play Store) sont indépendantes l'une de l'autre et peuvent être
menées en parallèle, ou la 9 avant la 8 si l'objectif est de publier vite.

---

## Phase 0 — Socle projet (fondations non-négociables)

> ✅ **CLOSE (2026-08-06).**

**Objectif** : rendre le projet gouvernable avant d'y toucher.

- ✅ Git initialisé, dépôt distant privé configuré (`origin` →
  [github.com/lionel78490-beep/FoxOFFV2](https://github.com/lionel78490-beep/FoxOFFV2)).
- ✅ CI GitHub Actions (`ci: ajoute le workflow GitHub Actions (build, tests, lint)`, commit `46e3fa8`).
- ✅ Outillage de test en place : MockK, `kotlinx-coroutines-test`, Turbine
  (commit `40e7ed2`), validé par un smoke test dédié (commit `46a7446`).
- ✅ `README.md` racine (présentation, schéma des modules, commandes de
  build — commit `53fcc93`).
- ✅ Partage `.idea/` configuré pour le dépôt (config projet uniquement,
  commit `2d24203`).

**Sortie (spécifique)** : `git log` non vide, CI verte sur un commit trivial, `./gradlew test` exécute (même vide) sans erreur. — **Atteinte.**

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 2-3 jours.

---

## Phase 1 — Valider la boucle réelle Watch → Phone → Brain

> ⏸️ **EN PAUSE (depuis le 2026-08-06)** — voir "Chantier prioritaire
> temporaire" juste après cette phase pour le contexte complet. Reprise
> prévue dès que l'application est fonctionnelle de bout en bout
> (navigation, écrans, réglages, TV, montre).
>
> **Confirmé sur matériel réel avant la pause** : Task 1.2 (démarrage
> moteur montre), Task 1.3 (pairing Data Layer), Task 1.1 (test unitaire de
> symétrie du format d'échange BPM).
>
> **Correctif appliqué et conservé, à ne pas retirer** : `FoxBrain.kt` —
> ajout du cas `SleepDetected -> tvIsPaused = true`, qui fermait une boucle
> infinie de décision découverte lors des tests de la Task 1.4 (le guard
> `!brainState.tvIsPaused` de `FoxCore` ne se refermait jamais).
>
> **Reporté, pas invalidé** : reconfirmation sur matériel réel de la
> condition `ASLEEP` avec BPM au repos, occurrence unique de
> `Brain decision` (absence de boucle), puis Task 1.6 (stabilité 30
> minutes, écran éteint) — critère de sortie de la phase.

**Objectif** : prouver que le produit fonctionne de bout en bout avec de vraies
données, **avant** toute migration architecturale. On travaille encore dans
`FoxCore` tel qu'il existe — on ne le refactore pas ici, on le fait mentir le
moins possible.

Constat du code actuel : `PhoneWearListenerService` attend des messages sur
`/foxoff/hr` et `/foxoff/watch_info`, mais le module `wear` (`MainActivity.kt`,
`WearHomeScreen`) ne fait **que de la vérification de permissions** — aucun
capteur n'est lu, aucun message n'est envoyé. `androidx.health.health-services-client`
est déjà en dépendance mais inutilisé. Résultat : sans détection d'erreur
visible, FoxBrain ne reçoit jamais de vrai BPM.

- Côté Wear : implémenter la collecte BPM via **Health Services** (`PassiveMonitoringClient` ou `MeasureClient`), fallback `SensorManager`/`BODY_SENSORS` si nécessaire.
- ✅ **Fait (2026-08-07)** : détection de mouvement côté montre —
  `MovementEngine` (accéléromètre `TYPE_LINEAR_ACCELERATION`, RMS agrégé
  sur 30s) → `WearCommunicationManager.sendMovement()` → nouveau path
  `/foxoff/movement` → `PhoneWearListenerService` → `SensorEvent.MovementDetected`
  → `FoxCore` → `FoxBrainEvent.MovementDetected` → `WeightedSleepAnalyzer`
  (déjà géré, pénalité si mouvement significatif). Seul `MovementDetected`
  est câblé pour l'instant — `StillnessDetected`/`WalkingDetected` restent
  non publiés (non nécessaires pour corriger le faux positif du
  2026-08-07 : `sustainedBpmDropDuration` couvrait déjà l'essentiel, ce
  câblage ajoute une seconde couche de protection). **`movementThreshold`
  (0.5, voir SleepScoringConfig) n'est PAS calibré sur matériel réel** —
  aucune donnée de mouvement réelle n'existait avant ce correctif ; à
  ajuster après un premier test si trop/pas assez sensible.
- Envoi périodique via `MessageClient` (`/foxoff/hr`, `/foxoff/watch_info`, nouveaux paths pour mouvement).
- Un **keep-alive minimal** (service de test, pas encore l'orchestrateur définitif de la Phase 6) pour que la boucle tienne le temps d'un test nocturne réel — la robustesse Doze/App Standby complète est traitée en Phase 6, pas ici.
- Tests instrumentés du round-trip Data Layer (émulateur téléphone + émulateur montre appairés).

**Sortie (spécifique)** : un vrai BPM mesuré sur la montre modifie l'état `FoxBrain` sur le téléphone, sur une nuit de test réelle, sans refactor architectural préalable.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 2 semaines.

---

## Chantier prioritaire temporaire — Application complète et utilisable

> **Ouvert le 2026-08-06, CLOS le 2026-08-07.** Tous les points de la liste
> "Manque ou cassé" ci-dessous ont été corrigés et vérifiés (build debug +
> release + tests unitaires + parcours manuel) : écran Réglages, onglet
> Santé (historique de détection), persistance de l'onboarding, format de
> PIN unifié, test de compatibilité TV réel, `minBpmToday`/`maxBpmToday`
> recalculés, batterie/version Wear réelles. Section conservée telle quelle
> ci-dessous comme trace de l'audit d'origine. Voir [DECISIONS.md](DECISIONS.md) ADR-010 pour
> le contexte de cette dérogation temporaire aux Principes directeurs 1 et
> 2 ci-dessus. Ce chantier n'est **pas** une nouvelle phase numérotée :
> c'est une priorité transverse, à cheval sur plusieurs phases futures (1
> partiellement, 4, 6, 7), ouverte pour obtenir une application utilisable
> de bout en bout avant de reprendre la séquence normale des phases.

**Objectif** : que FoxOFF soit une application complète et cohérente pour un
utilisateur réel — navigation, écrans, réglages, connexion montre/téléphone,
association TV/Freebox, fonctionnement général — même si la rigueur complète
de chaque phase future (tests exhaustifs, architecture cible, DoD complet)
n'est pas encore atteinte partout. On répare et complète ce qui manque
concrètement, sans anticiper le refactor architectural de la Phase 3.

Constats de l'audit du code du 2026-08-06 (cartographie complète de la
navigation, des écrans, du pairing TV et de la connexion montre) :

**Fonctionne déjà** : navigation onboarding complète (8 écrans enchaînés),
permissions runtime réelles, détection montre réelle (Wearable API),
découverte TV réelle (mDNS/NSD), appairage TV réel (protocole Android TV
Remote v2, TLS, PIN), dashboard "Accueil" temps réel connecté à
`FoxCore.brain.state`, onglet "TV" avec contrôle Play/Pause réel,
orchestration `FoxCore` (BPM → Brain → décision → pause TV automatique).

**Manque ou cassé :**
- Écran Réglages : totalement absent, placeholder "Écran en cours de
  développement" (`DashboardScreen.kt`).
- Onglet Santé du dashboard : même placeholder, aucun historique/détail.
- Onboarding relancé à chaque démarrage de l'app — aucune persistance de
  l'état "déjà configuré" (`MainActivity.kt`, état local non sauvegardé).
- Incohérence de format de PIN entre `RemoteScreen` (dashboard, 4 chiffres
  numériques) et `TvPairingManager`/`TvPairingScreen` (onboarding, 6
  caractères hexadécimaux) — l'appairage depuis le dashboard est cassé en
  pratique pour un PIN hexadécimal.
- Test de compatibilité TV dans l'onboarding simulé (`delay(2000)` puis
  succès codé en dur), aucune vraie vérification.
- `minBpmToday`/`maxBpmToday` figés à des valeurs par défaut, jamais
  recalculés par `DashboardViewModel`.
- Batterie/version affichées en dur côté Wear (`WearHomeScreen.kt`).

**Orphelin / code mort identifié en passant :** `TvLabActivity` (+
dépendances) non relié à la navigation principale — outil de diagnostic
manuel, à conserver tel quel, pas un défaut ; `tv/pairing/PairingSession.kt`
supprimé (confirmé mort, aucune référence) ; `HexPinKeyboard.kt` non branché
aux écrans réels d'appairage.

**Sortie (spécifique)** : un utilisateur peut installer l'app, terminer
l'onboarding une seule fois, appairer sa TV depuis n'importe lequel des deux
points d'entrée sans bug de format de PIN, consulter et modifier ses
réglages de base, et retrouver l'app dans l'état où il l'a laissée au
prochain lancement.

**DoD adapté** (allégé par rapport au DoD standard, le temps de ce
chantier) : build debug/release · parcours principal testé manuellement sur
appareil réel · zéro régression sur ce qui fonctionne déjà (notamment le
correctif `FoxBrain.kt`, conservé sans modification) · documentation mise à
jour · commit dédié par étape. Pas d'exigence de couverture de tests
exhaustive ici — elle revient dès la Phase 5 (Brain) et la reprise formelle
des phases numérotées.

**À la clôture de ce chantier** : reprise de la Phase 1 (validation
`ASLEEP` sur matériel réel + Task 1.6), puis retour à la séquence normale
des phases (2 → 3 → ...).

---

## Phase 2 — Stabilisation fonctionnelle minimale

**Objectif** : corriger ce que la Phase 1 a révélé et nettoyer les points
fragiles évidents, **sans** toucher à l'architecture globale. `FoxCore` reste
le point d'orchestration unique ici — la découpe en couches, c'est la Phase 3.

- ✅ **Fait (2026-08-07)** : seuils de décision déplacés de `FoxCore.startOrchestration()` (codés en dur : `0.70`, `0.80`) vers `SleepScoringConfig` (`highPrecisionThreshold`, `autoPauseConfidenceThreshold`), instance unique partagée entre `WeightedSleepAnalyzer` et la Brain Decision Loop.
- ✅ **Fait (2026-08-07)** : `FoxModule`/`ModuleDescriptor` (`core/module/FoxModule.kt`) supprimé — interface jamais implémentée ni enregistrée (`registerModule` n'était appelé nulle part). `FoxCore.startModules()`/`shutdown()` retirés avec leurs appelants (`FoxApplication.kt`). Hilt (Phase 3) couvrira ce besoin proprement le moment venu.
- Corriger les incohérences ou bugs mineurs mis au jour pendant les tests nocturnes de la Phase 1.
- ✅ **Fait (2026-08-08)** : `FoxDiagnostic.dumpCommunication()` supprimé —
  ce n'était pas un vrai diagnostic mais une liste de 9 lignes "✔ ...
  confirmed" codées en dur, jamais vérifiées à l'exécution (donc
  potentiellement fausses — par ex. "ExerciseEngine : Initialized &
  Running" ne l'est plus depuis le passage à Passive par défaut).
  Appelée deux fois à chaque démarrage à froid (`FoxCore.initialize()` ET
  `DashboardViewModel.init`), doublant ces 9 lignes trompeuses dans
  Logcat sans aucune valeur ajoutée. Fichier et les deux appels retirés ;
  import `FoxLogger` inutilisé dans `DashboardViewModel.kt` retiré au
  passage. `FoxLogger` lui-même (API `i/w/e`) inchangé.

**Sortie (spécifique)** : mêmes fonctionnalités que la fin de Phase 1, code mort en moins, magic numbers en moins — comportement observable inchangé.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 3-5 jours.

---

## Phase 3 — Refactor architectural progressif

**Objectif** : remplacer le God Object `FoxCore` par une architecture en
couches, **incrémentalement** — chaque commit compile et l'app tourne, jamais
de branche géante qui casse tout pendant deux semaines.

- ✅ **Étape 1 faite (2026-08-08) : fondations Hilt + Room posées, zéro
  changement de comportement.** `FoxCore` reste un `object` intact — cette
  étape a délibérément été bornée à ne toucher AUCUN code qui tourne
  pendant les nuits de test en cours (voir décision de séquencement
  ci-dessous). Détail :
  - Hilt (`com.google.dagger.hilt.android`) et KSP ajoutés à `:app`
    uniquement (pas `:wear`, conforme à DECISIONS.md ADR-003).
    `FoxApplication` porte `@HiltAndroidApp` — le graphe Hilt existe, rien
    n'y est encore injecté.
  - Room (fondation, conforme à ADR-004) : nouveau package `data/local/`
    (`SleepSessionEntity`, `SleepSessionDao`, `AppDatabase`) + module Hilt
    `di/DatabaseModule.kt`. Une seule entité, choisie parce qu'elle reflète
    un concept déjà manipulé partout (session de sommeil), pas un schéma
    complet inventé à l'avance. **Non branché à l'UI** — `NightLog`/
    `SleepDetectionHistory` (SharedPreferences/JSON) continuent de servir
    l'onglet Historique sans changement.
  - Trois vrais problèmes de compatibilité outillage rencontrés et résolus
    (versions très récentes du projet : Kotlin 2.2.10, AGP 9.3.1, Gradle
    9.5.0) : (1) Hilt 2.57.1 incompatible avec AGP 9 ("Android BaseExtension
    not found") — résolu en passant à Hilt 2.60.1 (premier à supporter
    AGP 9) ; (2) KSP enregistre encore ses sources générées via l'ancienne
    DSL `kotlin.sourceSets`, refusée par le "built-in Kotlin" d'AGP 9 (bug
    amont ouvert, [google/ksp#2729](https://github.com/google/ksp/issues/2729))
    — contournement officiel `android.disallowKotlinSourceSets=false` posé
    dans `gradle.properties`, à retirer dès qu'une version de KSP corrige
    ça nativement ; (3) `androidx.room:room-runtime-android` 2.8.4 n'expose
    QUE le builder basé sur `Context` (confirmé par décompilation directe
    de l'AAR réel) — pas de variante JVM sans Context hors d'un module
    Kotlin Multiplatform. Le test du DAO (`SleepSessionDaoTest`) a donc été
    écrit en `androidTest` (instrumenté) plutôt qu'en test JVM pur —
    **non exécuté cette session, faute d'émulateur/appareil connecté** ; à
    lancer via `./gradlew :app:connectedDebugAndroidTest` dès qu'un
    appareil est disponible. Vérification réelle obtenue autrement : build
    complet (`assembleDebug`/`assembleRelease` app+wear, `testDebugUnitTest`,
    `lint`) BUILD SUCCESSFUL — preuve que Hilt/Room/KSP compilent et
    s'empaquettent correctement pour de vrai, pas seulement "ça devrait
    marcher".
  - **Décision de séquencement** : le reste de la Phase 3 (voir ci-dessous)
    touche directement `FoxCore`, qui orchestre la boucle sommeil→TV
    activement testée nuit après nuit en ce moment (2026-08-08). Reporté
    tant que la détection de sommeil n'est pas validée sur plusieurs nuits
    réelles, pour ne pas risquer une régression dans du code qui vient
    d'être stabilisé.
- Introduire **Hilt** pour l'injection de dépendances, progressivement, dans le module `:app` existant (pas de découpe Gradle multi-module ici — voir "Décisions repoussées").
- Découper chaque sous-système (`tv`, `brain`, `sensors`) en `domain` (use cases, interfaces de repository — indépendant d'Android), `data` (implémentations : `TvConnectionManager`, `PhoneWearListenerService`...), `presentation` (ViewModels, Compose).
- Extraire la boucle de décision de `FoxCore` (aujourd'hui : event bus + état + orchestration + règles mélangés) en un `SleepAutomationUseCase` testable indépendamment d'Android.
- Introduire **Room** : schéma pour sessions de sommeil, historique BPM, TV appairée, préférences. Pas encore branché à l'UI (c'est la Phase 7) — juste la fondation de persistance.

**Sortie (spécifique)** : app identique du point de vue utilisateur, mais bâtie sur Hilt + repositories + Room (tables créées, pas encore consommées par l'UI). Tests unitaires sur `SleepAutomationUseCase`.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 1-2 semaines.

---

## Phase 4 — Durcir le TV Engine (une TV, fiable)

**Objectif** : un canal de contrôle aussi solide que le pairing — pour **une
seule TV principale**, pas pour une flotte. Le multi-TV est explicitement
repoussé (voir "Décisions repoussées") : la V2 initiale n'en a pas besoin.

Constat (vérifié à la lecture du code, cf. [AUDIT.md](AUDIT.md) §1.1-1.3) :
`TvPairingManager` est déjà bien fait — il utilise les classes protobuf
**générées** (`PoloProto`) issues de `polo.proto`, avec vérification de PIN
par hash SHA-256 conforme au protocole Android TV Remote v2. Le canal de
contrôle réellement utilisé en production
(`FoxTvEngine` → `FoxTvController` → `tv/remote/TvRemoteClient.kt`) utilise
lui aussi correctement les classes générées de `remotemessage.proto` — **ce
n'est pas un point à corriger**. En revanche, trois écarts subsistent :

1. `tv/TvConnectionManager.kt` (encodage manuel octet par octet) et son
   enveloppe `tv/TvCommandSender.kt`, ainsi que le stub vide
   `tv/protobuf/RemoteFactory.kt`, sont du **code legacy orphelin** — aucun
   chemin de production ne les appelle.
2. Deux identités TLS coexistent : `tv/TvKeyStore.kt` (ancienne, encore
   gardée par `FoxTvEngine` "temporairement... pour conserver la
   compatibilité") et `tv/pairing/TvIdentity.kt` (nouvelle, réellement
   utilisée par le pairing et le contrôle actifs).
3. Le plugin Gradle `com.google.protobuf` (déclaré `apply false` à la
   racine) n'est appliqué sur aucun module : les classes générées
   (`PoloProto.java`, `Remotemessage.java`) sont versionnées manuellement
   dans `src/main/java` plutôt que produites depuis les `.proto` à chaque
   build.

- **Supprimer progressivement le legacy TV** (`TvConnectionManager`,
  `TvCommandSender`, `RemoteFactory`) une fois vérifié qu'aucun chemin ne
  les utilise.
- **Ne conserver que la chaîne de production validée** :
  `TvPairingManager` → `TvIdentity` → `TvRemoteClient` /
  `FoxTvController` / `FoxTvEngine`.
- **Terminer l'extraction propre** de cette chaîne vers l'architecture
  cible posée en Phase 3 (le sous-système TV devient un repository/use
  case testable derrière une interface `domain`, plus une simple façade
  appelée depuis `FoxCore`).
- Trancher la coexistence `TvKeyStore` / `TvIdentity` : **une seule
  implémentation TLS** au final. Migrer son stockage vers l'**Android
  Keystore** (au lieu de `SharedPreferences` en Base64 clair) et retirer
  `TvKeyStore.exportCertificate()` (fonction de debug marquée `TEMPORARY`
  dans le code).
- **Intégrer le plugin Gradle Protobuf sur `:app`** : les classes générées
  doivent être produites automatiquement depuis `polo.proto` /
  `remotemessage.proto` à chaque build, plutôt que versionnées à la main —
  élimine le risque de dérive silencieuse entre le schéma et le code
  exécuté.
- Reconnexion automatique de la TV principale (perte Wi-Fi, TV
  éteinte/rallumée, changement d'IP).
- Élargir `TvLabActivity` en véritable écran de diagnostic (déjà un bon
  point de départ pour les tests manuels).

**Sortie (spécifique)** : un seul chemin de code TV en production (plus de
legacy orphelin), une seule identité TLS stockée dans l'Android Keystore,
classes protobuf régénérées automatiquement par Gradle, contrôle fiable et
reconnexion validée sur la TV principale. Pas de sélection multi-appareils
— ce n'est pas dans le périmètre.

✅ **Fait (2026-08-12)**, hors séquence (demande explicite, suppression
rapide n'attendant pas la fin de Phase 4) : icône corbeille à côté de
chaque TV dans "Mes TV" (`TvPairedCard`, `RemoteScreen.kt`), avec boîte de
dialogue de confirmation avant suppression (action destructive). Réutilise
`FoxTvEngine.removeDevice(id)`, déjà existant et testé (gère notamment la
promotion d'une autre TV en "utilisée" si celle supprimée l'était).

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 2 semaines (périmètre élargi par le nettoyage legacy identifié en audit).

---

## Phase 5 — Sleep Engine / Fox Brain V2

**Objectif** : un moteur de décision fiable, testé, calibrable avec les
données réelles obtenues en Phase 1 — c'est le cœur métier du produit.
**Pas de ML embarqué ici** : TensorFlow Lite est explicitement repoussé à la
Phase 8 (voir "Décisions repoussées").

- `WeightedSleepAnalyzer` + `SleepScoringConfig` sont une bonne base (Strategy pattern via `FoxBrainAnalyzer`, config centralisée). ✅ **Fait (2026-08-07)** : `BasicBrainAnalyzer` (V1) confirmé obsolète — zéro usage nulle part dans le code, entièrement superseded par `WeightedSleepAnalyzer` — supprimé.
- ✅ **Fait (2026-08-07)** : **Tests unitaires exhaustifs** du moteur de scoring — 43 tests (`WeightedSleepAnalyzerTest`, `FoxBrainTest`), couvrant chaque règle de `SleepScoringConfig`, les bornes de clamp [0,1], le mapping des seuils `SleepState`, et le dispatch d'état de `FoxBrain` (isolé du scoring via un analyseur factice). Parti de zéro test, comme identifié dans l'audit.
- ✅ **Fait (2026-08-07)** : calibration personnalisée du BPM de repos.
  `FoxBrainState.restingBpmBaseline` (70 bpm, sourcé Mayo Clinic/AHA — plage
  normale 60-100) sert de valeur de départ tant qu'aucune donnée réelle
  n'existe. Deux sources viennent la remplacer, par ordre de préférence :
  (1) **Health Connect** (`HealthConnectBaselineProvider`) — lecture de
  l'historique Samsung Health existant (`RestingHeartRateRecord`, ou repli
  10e percentile de `HeartRateRecord` brut), calculée à la demande depuis
  Réglages ("Calibrer avec Samsung Health"), persistée (`RestingBpmSettings`)
  et appliquée immédiatement (`FoxBrain.setRestingBpmBaseline`) ; (2) à
  défaut, `minBpmToday` (le minimum observé par FoxOFF lui-même) prend le
  relais dès la première vraie mesure de la session. **Reste hors périmètre**
  : une moyenne glissante inter-nuits persistée en continu (Room, Phase 3) —
  la calibration Health Connect actuelle est un calcul ponctuel déclenché
  manuellement, pas une mise à jour automatique nuit après nuit.
- ✅ **Corrigé (2026-08-07) — faux positif réel observé** : la règle de
  baisse de BPM ajoutait son bonus à CHAQUE échantillon tant que le BPM
  restait sous le seuil, sans jamais redescendre — assis immobile devant la
  TV (repas), le score grimpait jusqu'à ASLEEP en quelques minutes sans
  endormissement réel, faute de tout signal de mouvement en production
  (`MovementDetected`/`StillnessDetected`/`TimeChanged`/`ScreenUnlocked` ne
  sont publiés nulle part aujourd'hui — écart déjà identifié en Phase 1,
  toujours vrai). Corrigé par `SleepScoringConfig.sustainedBpmDropDuration`
  (5 min) : le bonus n'est accordé qu'une fois par épisode, après une baisse
  SOUTENUE, pas à la première mesure. ✅ **Complété (2026-08-07)** par
  l'implémentation de la détection de mouvement côté montre (voir Phase 1
  ci-dessus, `MovementEngine`) — les deux corrections combinées (durée
  soutenue + vrai contre-signal de mouvement) couvrent le scénario
  rapporté.
- ✅ **Bug réel trouvé et corrigé (2026-08-08)** : le statut de connexion TV
  restait bloqué à CONNECTED indéfiniment même après extinction physique de
  la TV. Repéré car l'historique d'une nuit entière ne contenait AUCUN
  événement TV_ON/TV_OFF alors que la TV avait bien été éteinte pendant la
  nuit (confirmé par l'utilisateur). **Premier correctif fait par erreur
  sur `TvConnectionManager.kt`** — fichier confirmé mort (voir Phase 4,
  "code legacy orphelin", jamais appelé par `FoxTvEngine`), corrigé pour
  rien puis reverté. **Vraie cause** : `TvRemoteClient` (chemin réellement
  utilisé) ouvre une connexion, l'utilise, puis la referme à chaque usage —
  il n'y a donc aucune connexion persistante dont la coupure pourrait être
  détectée passivement. Le statut n'était revalidé que lors d'une tentative
  explicite (démarrage de l'app, bouton "Réessayer"), jamais périodiquement.
  Corrigé : `FoxForegroundService.startHeartbeat()` (déjà utilisé toutes les
  15 min pour la reconnexion montre, voir Tâche #13) appelle maintenant
  aussi `FoxTvEngine.refreshActiveDevice()` — un test de connexion en
  lecture seule, jamais de commande.
- ✅ **Batterie montre vidée en une nuit (2026-08-08)** : rapporté par
  l'utilisateur (77% au coucher, 0% au réveil ~9h plus tard). Cause
  confirmée par la documentation officielle Wear OS/Health Services
  ([developer.android.com/health-and-fitness/health-services](https://developer.android.com/health-and-fitness/health-services)) :
  `FoxWearCore` faisait tourner DEUX moteurs BPM en parallèle en continu —
  `ExerciseClient` (conçu pour un entraînement ponctuel, bien plus coûteux
  en énergie) ET `PassiveMonitoringClient` (l'API officiellement
  recommandée pour une surveillance longue durée en arrière-plan, agrégée
  par lots) — pour la MÊME donnée, remontée en double au téléphone.
  Corrigé : Passive devient le SEUL moteur actif quand l'appareil le
  supporte ; Exercise/HealthServices ne servent plus que de repli si
  Passive n'est pas disponible sur le matériel (voir
  `FoxWearCore.initialize()`). **Statut : corrigé mais pas encore validé
  sur une vraie nuit** — à confirmer que le BPM continue d'arriver de façon
  fiable (Passive livrait déjà des données toute la nuit du test précédent,
  en parallèle d'Exercise, donc c'est un canal déjà éprouvé, mais jamais
  utilisé seul jusqu'ici).
- ⚠️→✅ **Régression et correctif (2026-08-08), première vraie nuit de
  test** : les deux garde-fous ci-dessus, combinés, ont sur-corrigé —
  score resté à 0-5% (AWAKE) TOUTE la nuit malgré un sommeil réel confirmé
  (BPM stable 47-54 pendant des heures), aucun déclenchement de pause. Deux
  causes cumulées : (1) `bpmDropBonus` n'était accordé qu'UNE SEULE fois
  par épisode continu sous le seuil, plafonnant le score gagnable via le
  BPM à ~0,08 (avec le bonus TV) — bien trop bas pour ASLEEP (0,90).
  Corrigé : octroi PÉRIODIQUE (toutes les `sustainedBpmDropDuration`, pas
  une fois pour tout l'épisode) — voir `FoxBrainState.lastBpmDropBonusAt`
  (remplace `bpmDropBonusGranted`). (2) `movementThreshold` (0.5, jamais
  calibré faute de données réelles avant cette nuit) déclenchait des
  dizaines de "mouvements significatifs" par nuit — remis à zéro le score
  avant qu'il ait une chance de monter. Recalibré à **2.0** à partir de la
  distribution réelle observée cette nuit-là (ramène les événements
  "significatifs" à une poignée par nuit, cohérent avec des changements de
  position occasionnels plutôt que du bruit de capteur). Tests mis à jour
  en conséquence. **Statut : corrigé mais pas encore validé sur une
  nouvelle nuit réelle** — à confirmer.
- ✅ **Régression auto-infligée trouvée et corrigée (2026-08-09), deuxième
  nuit de test** : score resté quasi plat (0-14%) TOUTE la nuit malgré un
  BPM stable très bas (jusqu'à 45), preuve d'un vrai sommeil. Cause :
  `FoxCore` (bloc "Observe TV Engine State") déclenchait
  `FoxBrainEvent.TVTurnedOff` (→ `currentProb *= 0.5f`) pour TOUT statut TV
  différent de `CONNECTED`, y compris `CONNECTING` — l'état transitoire que
  prend justement `FoxTvEngine.refreshActiveDevice()` (le heartbeat 15 min
  ajouté le 2026-08-08 pour corriger le bug "statut TV figé", voir
  Phase 4). Conséquence : le score était divisé par deux toutes les 15
  minutes, TOUTE LA NUIT, indépendamment de tout mouvement ou BPM réel —
  bien plus dommageable que les dynamiques mouvement/BPM elles-mêmes.
  Aggravant : l'événement partait à CHAQUE réémission du flux (pas
  seulement au changement), donc le halving pouvait se répéter en silence
  sans même apparaître dans NightLog (texte de raison identique d'une fois
  à l'autre, non re-loggé). Corrigé : `CONNECTING` est maintenant ignoré
  (ni TVTurnedOn ni TVTurnedOff), et l'événement Brain ne part que sur un
  vrai changement d'état (même garde que celle déjà utilisée pour
  NightLog). **Statut : validé (2026-08-10)** — plus aucun flapping TV
  observé la nuit suivante.
- ✅ **Troisième nuit de test (2026-08-09 au 10) : score toujours plafonné
  (~15% max) malgré un BPM stable et bas (45-52) pendant des heures.**
  Cause identifiée : `bpmDropThreshold` (5%) est bien trop serré appliqué à
  `minBpmToday`, qui est le minimum ABSOLU du jour et continue de baisser
  toute la nuit à mesure que le sommeil s'approfondit — la moindre
  variation naturelle du rythme cardiaque (quelques bpm de respiration/
  micro-mouvement) suffisait à repasser au-dessus du seuil mouvant et
  remettait `bpmBelowBaselineSince` à zéro avant que 5 minutes continues ne
  soient jamais atteintes. Corrigé (2026-08-10) : `bpmDropThreshold` relevé
  à 12% (voir SleepScoringConfig.kt pour le détail du raisonnement). Deux
  tests unitaires ajustés en conséquence (valeurs de BPM recalculées pour
  le nouveau seuil, même intention de test). **Problème de fond identifié
  mais pas encore traité** : `minBpmToday` reste une référence qui rétrécit
  sans cesse, quel que soit le seuil de tolérance — la vraie correction
  attend que la calibration Health Connect (bug en cours d'investigation,
  voir ci-dessus) soit fiable, pour utiliser `restingBpmBaseline` calibré
  comme référence stable à la place. **Statut : corrigé mais pas encore
  validé sur une nouvelle nuit réelle** — à confirmer.
- ✅ **Calibration Health Connect enfin effective (2026-08-10).** Diagnostic
  complet mené avec l'utilisateur (captures d'écran à l'appui) : le code
  FoxOFF a toujours été correct — `computeRestingBaseline()` lisait
  correctement Health Connect, qui renvoyait réellement zéro donnée (confirmé
  directement dans l'écran "Données et accès" de Health Connect lui-même,
  "Aucune donnée"). Cause réelle, hors du périmètre FoxOFF : Samsung Health
  avait bien la permission d'écrire dans Health Connect (vérifié dans
  Samsung Health > Santé Connect > Accès de l'appli — "Tout autoriser" ON,
  toutes catégories) mais n'avait tout simplement **jamais synchronisé**
  ses données existantes — permission accordée ≠ synchronisation
  effectuée. S'est résolu de lui-même (optimisation batterie et/ou délai de
  synchronisation Samsung Health). Au passage : bug UI réel trouvé et
  corrigé — les deux boutons "Calibrer avec Samsung Health"/"Ouvrir Health
  Connect" étaient côte à côte dans un `Row` qui débordait hors de l'écran
  sur téléphone, rendant le second bouton invisible et inatteignable ;
  empilés verticalement depuis (`SettingsScreen.kt`). BPM de repos
  désormais calibré depuis les vraies données de l'utilisateur.
- ✅ **Fait (2026-08-10)** : calibration affinée avec les sessions de
  sommeil réelles. `computeRestingBaseline()` ne lisait que la fréquence
  cardiaque de repos générale ; ajout d'une source prioritaire — lecture de
  `SleepSessionRecord` (Health Connect) croisée par horodatage avec les
  échantillons `HeartRateRecord` tombant DANS ces sessions, pour une
  référence "BPM pendant le sommeil confirmé" plus précise qu'"au repos" en
  général (qui inclut des moments calmes éveillé). Repli inchangé sur
  `RestingHeartRateRecord` puis le 10e percentile des échantillons bruts si
  aucune session de sommeil n'est disponible. Nouvelle permission
  `android.permission.health.READ_SLEEP`. `PRIVACY.md` et
  `docs/PLAY_STORE_DATA_SAFETY.md` mis à jour en conséquence
  (`HealthConnectBaselineProvider.kt`).
- ⚠️→✅ **Généralisée à Garmin (2026-08-10)** : la carte "BPM de repos" avait
  d'abord été masquée pour les utilisateurs Garmin (voir ci-dessus), en
  supposant Health Connect propre à Samsung Health. Recherche faite à la
  demande de l'utilisateur ("utiliser les données du logiciel Garmin pour
  faire comme avec Samsung") : **Garmin Connect a lui aussi une intégration
  Health Connect native** (réglage propre à l'app Garmin Connect, même
  principe que Samsung Health — permission Android + activation interne à
  l'app). `HealthConnectBaselineProvider` lit déjà Health Connect de façon
  générique, sans se soucier de quelle app y a écrit les données — aucun
  nouveau code de lecture nécessaire. Corrigé : la carte est réaffichée
  pour les deux marques, avec un libellé adapté ("Calibrer avec Samsung
  Health" vs "Calibrer avec Garmin Connect", `SettingsScreen.kt`) plutôt
  que masquée. **Non vérifié sur matériel Garmin réel** (pas de montre
  Garmin physique, comme le reste du transport Garmin cette session) — la
  lecture Health Connect elle-même est déjà validée (fonctionne côté
  Samsung Health), seul le changement de libellé n'est pas testé en
  conditions réelles côté Garmin.

  Sources : [FitMesh — sync Garmin/Samsung Health/Health Connect](https://www.fitmesh.fit/en/blog/sync-garmin-samsung-health-guide),
  [forums.garmin.com — Health Connect sync](https://forums.garmin.com/apps-software/mobile-apps-web/f/connect-iq-store-android/339240/let-s-sync-garmin-data-to-health-connect-and-google-fit-apps).
- ✅ **Fait (2026-08-13)** : `garmin/manifest.xml` déclare désormais 136
  montres (au lieu de 3 : vivoactive4s/5/6 uniquement) — demande explicite
  ("rajoute toute les montres compatible avec notre application"). Liste
  construite à partir des 166 appareils installés dans le SDK Manager
  (Connect IQ 9.2.0), en excluant délibérément les appareils Connect IQ
  non pertinents pour une app portée au poignet la nuit : Edge (ordinateurs
  de vélo, montés sur guidon, aucun capteur cardiaque intégré) et les GPS
  de randonnée/moto portatifs (etrex, gpsmap, montana, oregon, rino) —
  techniquement compatibles Connect IQ mais jamais portés au poignet ni au
  coucher. Deux exclusions supplémentaires détectées directement par le
  compilateur (`fr45`, `garminswim2` : pas de support réel du type
  d'application "watch-app" malgré une apparence de montre classique).
  Compilé avec succès en un seul package multi-appareils
  (`monkeyc -e`, `garmin/build/FoxOffGarmin.iq`, 185 variantes internes
  construites, aucune erreur) — SDK Connect IQ 9.2.0 installé et utilisé en
  ligne de commande (`monkeyc.bat` via le JRE d'Android Studio, `java`
  n'étant pas dans le PATH système). Avertissements cosmétiques uniquement
  (icône de lancement 40×40 mise à l'échelle sur des appareils attendant
  60-70×70 — pas bloquant, juste moins net visuellement sur certains
  modèles). **Statut : compilé avec succès, non testé sur aucun matériel
  Garmin réel** (comme le reste du transport Garmin cette session) — les
  136 appareils n'ont donc pas de garantie de bon fonctionnement à l'usage,
  seulement de compilation réussie.
- ✅ **Détection accélérée (2026-08-10), décision produit** : au rythme
  précédent (`bpmDropBonus` 5% toutes les `sustainedBpmDropDuration` = 5
  min), atteindre ASLEEP (90%) depuis zéro demandait ~90 minutes de BPM bas
  ininterrompu — bien trop long pour l'objectif réel (couper la TV avant
  que l'utilisateur ait raté une grande partie de ce qu'il regardait, pas
  après). Remonté à `bpmDropBonus` = 18% / `sustainedBpmDropDuration` = 3
  min → ~15 min jusqu'à ASLEEP dans le meilleur des cas (BPM bas
  ininterrompu ; un mouvement remet toujours le compteur à zéro, donc le
  temps réel sera souvent plus long). Rendu acceptable par le filet de
  sécurité déjà en place (confirmation "toujours là ?" sur la montre avant
  la pause réelle) — une détection plus rapide mais parfois trop optimiste
  n'est plus aussi risquée qu'avant l'existence de ce filet. Première
  estimation, à ajuster sur de vraies nuits de test (`SleepScoringConfig.kt`).
- ⚠️ **Drain batterie plus élevé cette même nuit** (71% → 6% sur ~11h17,
  ≈5,75%/h, contre ≈3,5%/h la nuit précédente avec le même moteur Passive)
  — observé, pas encore expliqué. Le score n'ayant jamais atteint ASLEEP,
  le heartbeat de reconnexion a tourné toute la nuit (comportement attendu,
  pas un bug) ; la nuit précédente était dans le même cas et draine moins,
  donc ce n'est probablement pas la cause principale. À surveiller sur la
  prochaine nuit — pourrait n'être qu'une variance normale, pas encore
  d'investigation approfondie.
- ✅✅ **Première nuit pleinement validée (2026-08-10 au 11).** Coucher
  23h58 → score monte régulièrement (18% → 39% → 57% DROWSY → 75%
  PRE_SLEEP → 93%) → compte à rebours démarré à 00h10:14 → **pause
  automatique exécutée à 00h10:25** (11s après, conforme aux 10s de
  `COUNTDOWN_DURATION`). **Validation externe** : Samsung Health (mesure
  indépendante) estime l'endormissement à 00h08 — 2 minutes d'écart avec le
  déclenchement FoxOFF, confirmé aussi par l'utilisateur. Réveil 6h47 des
  deux côtés, cohérent avec le pic de mouvements détecté sur le journal
  (06h41-06h57). BPM stable 45-50°C toute la nuit une fois endormi.
  Batterie 77% → 52% sur ~6h49 (≈3,7%/h), cohérent avec les bonnes nuits
  précédentes et probablement aidé par l'arrêt du heartbeat après la pause
  (dès 00h10, pour tout le reste de la nuit). Aucune régression détectée
  (pas de flapping TV, seuil de mouvement toujours bien calibré). Première
  nuit où la chaîne complète (score → confirmation montre → pause → BPM
  stable jusqu'au réveil) fonctionne de bout en bout sans intervention.
- ✅ **Fait (2026-08-11)** : micro-mouvements ignorés une fois la TV en
  pause. Demande explicite de l'utilisateur — une fois le sommeil confirmé
  et la pause exécutée (`tvIsPaused`), les mouvements NON significatifs
  (sous `movementThreshold`) ne sont plus transmis au Brain ni journalisés
  dans NightLog : ils ne changent plus aucune décision (déjà prise) et ne
  servaient qu'à noyer l'Historique sous des centaines d'entrées sans
  intérêt (visible dans les journaux de nuit analysés cette session). Seuls
  les vrais changements de position restent suivis après la pause — utile
  notamment pour repérer un réveil (`FoxCore.kt`, souscription
  `MovementDetected`). Sans effet sur la détection de sommeil elle-même
  (le filtre ne s'active qu'APRÈS la pause).
- ✅ **Fait (2026-08-12)** : pénalité de mouvement rééquilibrée
  (`significantMovementPenalty` : 15% → 8%). Diagnostiqué à partir d'une
  vraie nuit de test : endormissement réel vers 23h25-23h30 (confirmé par
  Samsung Health à 5 min près et par le ressenti de l'utilisateur), mais
  FoxOFF ne l'a confirmé qu'à 00h45 — 1h15 de retard alors que le BPM était
  déjà bas et stable dès 23h24. Cause : deux mouvements "importants"
  ordinaires en s'endormant (se retourner, ajuster l'oreiller) ont chacun
  fait retomber le score à AWAKE, obligeant à reconstituer plusieurs cycles
  entiers de `bpmDropBonus` (18% / 3 min) pour un coût de pénalité (15%)
  presque équivalent à un cycle de bonus complet — quasi-annulation du
  gain de vitesse de detection ajouté le 2026-08-10. 8% reste un vrai
  signal sans effacer la quasi-totalité du dernier cycle de bonus à lui
  seul. Vérifié : 43 tests `brain/` toujours verts (aucune valeur codée en
  dur ne dépendait de l'ancienne pénalité). **Non revérifié sur une nuit
  réelle** — à confirmer sur les prochaines nuits de test.
- 🐛 **Correctif (2026-08-13)** : faux positif de pause réel constaté cette
  nuit-là (23h23, utilisateur encore éveillé) — score passé de 76% à 94%
  en 8 secondes avec un BPM en HAUSSE (71→79), franchissant le seuil
  ASLEEP (90%). Cause : `minBpmToday` (le minimum BPM observé depuis le
  début de la soirée) se fige dès la toute première lecture — cette
  nuit-là 71 bpm, bien au-dessus du BPM de sommeil réel de l'utilisateur
  (45-52 bpm sur les nuits validées) — et ne peut ensuite que baisser,
  jamais remonter. Avec 12% de tolérance, un BPM de 79 restait "sous le
  seuil" (71 × 1,12 ≈ 79,5) alors qu'il n'a rien d'un BPM de sommeil.
  Défaut déjà documenté dans `SleepScoringConfig.bpmDropThreshold` comme
  palliatif en attente d'un calibrage Health Connect fiable — devenu
  possible depuis sa validation le 2026-08-10. Corrigé dans
  `WeightedSleepAnalyzer`/`FoxBrain` : `minBpmToday`, une fois disponible,
  ne peut plus qu'ABAISSER le seuil par rapport à `restingBpmBaseline`
  calibré, jamais l'élever au-dessus. Le filet de sécurité "toujours là ?"
  (montre + téléphone, voir Phase 6) s'est déclenché comme prévu au bon
  moment mais n'a laissé que 10s pour répondre — insuffisant cette
  nuit-là. Nouveau test unitaire dédié (`WeightedSleepAnalyzerTest`)
  reproduisant exactement ce cas. **Statut : compilé, tests verts
  (44 tests `brain/`), non revérifié sur une nuit réelle** — à confirmer.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 1-2 semaines.

---

## Phase 6 — Automatisation & fiabilité runtime

**Objectif** : que la détection tourne vraiment toute la nuit, sans
intervention — en épuisant d'abord les solutions standard, **avant** de
demander quoi que ce soit d'exceptionnel à l'utilisateur.

- Service d'orchestration unique en foreground (remplace le keep-alive minimal de la Phase 1), avec notification informative (état de surveillance, dernière action).
- `WorkManager` pour les tâches différées/retry (reconnexion TV, resynchronisation montre).
- Canal de notification correctement déclaré (permission `POST_NOTIFICATIONS` déjà présente mais aucun canal créé à ce jour).
- **L'exemption d'optimisation batterie n'est pas une solution par défaut.** Elle n'est proposée à l'utilisateur qu'en tout dernier recours, explicitement optionnelle, avec explication claire de ce qu'elle change — jamais demandée automatiquement à l'installation ou au premier lancement. Priorité absolue au foreground service + `WorkManager` bien implémentés, qui doivent suffire sur la majorité des appareils.
- Étendre `FoxDiagnostic`/`FoxLogger` (déjà présents) vers des logs persistés consultables in-app pour le support/diagnostic utilisateur.
- ✅ **Fait (2026-08-09)** : confirmation "toujours là ?" sur la montre
  avant la pause auto. Le compte à rebours annulable (10s) existait déjà
  côté téléphone (`SleepPauseCoordinator`, notification "Annuler") mais
  n'était vu par personne pendant le sommeil. Ajout d'un canal
  supplémentaire (jamais un remplacement — best-effort, la notification
  téléphone reste le filet de sécurité complet si la montre est
  injoignable) : `SleepPauseCoordinator` envoie `/foxoff/confirm_awake` à
  la montre au démarrage du compte à rebours (`FoxCore.sendToWatch()`,
  rendu réellement fonctionnel — c'était un stub qui ne faisait que
  logger) ; `WatchTransport.sendCommand()` ajouté à l'interface
  (implémenté dans `WearOsTransport` et `GarminTransport`, ce dernier non
  vérifié sur matériel comme le reste du transport Garmin). Côté montre,
  `ConfirmAwakeNotifier` affiche une **simple notification actionnable**
  ("Toujours là ?" + bouton "Je suis là", `setTimeoutAfter(10s)`) —
  **volontairement pas une alerte plein-écran/vibration forcée** (décision
  prise avec l'utilisateur : ne pas risquer de réveiller quelqu'un qui
  dort vraiment ; quelqu'un encore éveillé la remarquera, quelqu'un
  endormi ne sera pas dérangé). Une réponse envoie
  `/foxoff/confirm_response` au téléphone, qui annule le compte à rebours
  via `SleepPauseCoordinator.cancel()` (exactement le même chemin que le
  bouton "Annuler" téléphone — aucune logique d'annulation dupliquée).
  Envisagé puis écarté : afficher un message directement sur l'écran de la
  TV — techniquement impossible avec le protocole Android TV Remote v2
  utilisé ici (simulation de télécommande uniquement, aucune capacité
  d'affichage/notification côté TV). **Statut : compilé et vérifié par
  build complet, mais non testé sur matériel réel** — comme pour Garmin,
  aucun émulateur/appareil connecté dans l'environnement de développement.
- ✅ **Fait (2026-08-09)** : économie de batterie une fois le sommeil
  confirmé. `FoxForegroundService.startHeartbeat()` (reconnexion
  montre + revalidation TV toutes les 15 min, voir Phase 4) s'arrête
  désormais définitivement pour le reste de la nuit dès que
  `FoxBrainState.tvIsPaused` devient vrai (pause auto exécutée) — plus
  aucune reconnexion n'a d'utilité une fois la TV en pause et le sommeil
  confirmé. Demande explicite de l'utilisateur pour limiter le drain
  batterie résiduel en fin de nuit. **Reprise automatique** (même demande,
  suite immédiate) : dès que l'utilisateur rouvre l'application le matin
  (`ProcessLifecycleOwner.onStart`, déjà utilisé par
  `FoxServiceReconciliation` dans `FoxApplication.kt`), le heartbeat
  reprend — vérification immédiate puis retour au cycle normal de 15 min —
  sans qu'il soit nécessaire de tuer puis relancer le processus.
  `FoxForegroundService.restartHeartbeatIfStopped()` (référence statique à
  l'instance du service, nettoyée dans `onDestroy()`).
- ✅ **Fait (2026-08-11)** : filtrage des mouvements non significatifs côté
  téléphone une fois la TV en pause (`FoxCore.kt`, abonnement
  `MovementDetected`) — les micro-mouvements ne changent plus aucune
  décision une fois le sommeil confirmé, ils ne servaient qu'à noyer
  l'Historique. Seuls les mouvements dépassant `movementThreshold` restent
  suivis après la pause.
- ✅ **Fait (2026-08-11)** : vraie économie de batterie **montre**
  post-endormissement (le filtrage ci-dessus est côté téléphone seul et ne
  réduisait rien côté montre — clarifié explicitement avec l'utilisateur,
  qui a confirmé vouloir l'économie réelle). Nouveaux chemins
  téléphone↔montre : `/foxoff/movement_low_power` (envoyé par
  `SleepPauseCoordinator.executePause()`, best-effort comme
  `/foxoff/confirm_awake`) et `/foxoff/movement_normal_power` (envoyé par
  `FoxCore` sur une vraie transition `TVTurnedOn` — TV rallumée, écran
  visionné à nouveau). Côté montre, `MovementEngine.setLowPowerMode()`
  élargit la fenêtre d'agrégation RMS de 30s à **10 min** (choix de
  l'utilisateur, arbitrage entre économie de batterie et délai de
  repérage d'un réveil dans l'Historique) — le capteur accéléromètre reste
  enregistré à la même cadence matérielle, seule la fréquence d'émission
  (et donc de transmission Bluetooth vers le téléphone via
  `sendMovement()`, le vrai poste de consommation) diminue. Le BPM
  (`PassiveMonitoringEngine`) n'est **pas** concerné : il continue de
  tourner toute la nuit, seul signal encore utile après la pause pour
  l'historique de fréquence cardiaque. **Statut : compilé et vérifié par
  build complet, non testé sur matériel réel** (même limite que Garmin et
  la confirmation "toujours là").
- ✅ **Fait (2026-08-11)** : créneaux horaires de fonctionnement. Demande
  explicite : la surveillance tournait "du matin au soir" alors qu'elle
  n'a d'utilité que pour l'endormissement. `ActiveHoursSettings.kt`
  (nouveau) — 4 créneaux préréglés (8h-12h, 12h-15h, 15h-20h, 20h-8h, ce
  dernier traversant minuit), sélection **multiple** persistée (ensemble
  vide = aucune restriction, comportement historique conservé, zéro
  régression pour un utilisateur existant qui n'a jamais configuré ce
  réglage). Combiné à l'intention principale
  (`BackgroundServiceSettings`) dans `FoxServiceReconciliation
  .reconcileNow()` : hors créneau sélectionné, traité exactement comme une
  intention désactivée (même chemin STOP testé, aucune logique dupliquée),
  sans jamais toucher au réglage principal — repart tout seul au prochain
  créneau. Nouveau : `FoxActiveHoursWorker` (WorkManager, dépendance
  ajoutée — `androidx.work:work-runtime-ktx`), vérification périodique
  toutes les 15 min (planifiée une fois dans `FoxApplication.onCreate()`,
  idempotent via `enqueueUniquePeriodicWork(..., KEEP)`) — seul
  déclencheur capable de démarrer/arrêter la surveillance pile à l'heure
  du créneau sans que l'utilisateur ait besoin de rouvrir l'app. UI : page
  dédiée à l'installation (`ActiveHoursScreen`, nouvelle étape entre
  Autorisations et Détection montre, "20h – 8h" pré-coché par défaut) +
  section équivalente (`FilterChip` multi-sélection) dans Réglages pour
  modifier le choix plus tard. **Statut : compilé et vérifié par build
  complet** (app + wear, debug/release, tests, lint) — la précision à 15
  min près du démarrage/arrêt automatique reste à confirmer sur plusieurs
  jours d'usage réel. Complément (même jour) : carte "Créneaux actifs"
  ajoutée en haut de l'Accueil (`ActiveHoursInfoCard`, sous le statut
  principal) pour voir le choix en cours d'un coup d'œil sans passer par
  Réglages.
- ✅ **Fait (2026-08-12)** : arrêt du "visibility watchdog" après la pause
  TV. Diagnostiqué à partir d'une vraie nuit de test (batterie du
  téléphone : 50% au coucher → 10% au réveil, ≈4,9%/h, plus élevé
  qu'attendu malgré les optimisations déjà en place). Trouvé dans
  `FoxForegroundService.kt` : `startVisibilityWatchdog()` (vérifie que la
  notification de surveillance reste visible) tournait toutes les 60s
  **sans jamais s'arrêter**, contrairement au heartbeat (15 min, stoppé
  dès `tvIsPaused`) — environ 480 réveils/nuit contre ~32. Corrigé pour
  s'arrêter selon la même logique que le heartbeat une fois la TV en
  pause, et reprendre avec lui à la réouverture de l'app
  (`restartHeartbeatIfStopped()` couvre maintenant les deux boucles,
  indépendamment via deux flags `Volatile` séparés). Compromis assumé et
  choisi explicitement avec l'utilisateur : si la notification est
  révoquée après la pause, la surveillance BPM continue silencieusement
  jusqu'au matin plutôt que de s'arrêter en moins d'une minute — acceptable
  une fois l'objectif principal de la nuit (pause TV) déjà atteint.
  **Statut : compilé et vérifié par build complet, non revérifié sur une
  nuit réelle** — à confirmer sur les prochaines nuits de test.
- ✅ **Fait (2026-08-12)** : première étape vers la pause automatique
  au-delà de la seule TV — demande explicite de l'utilisateur ("à terme
  l'application mettra pause au vidéo du téléphone ainsi que les
  applications de musique"). `PhoneMediaPauseController` met en pause
  toute lecture active sur le téléphone lui-même (YouTube, Spotify,
  Netflix...) **en plus** de la TV, via `MediaSessionManager
  .getActiveSessions()` — la seule API Android pour découvrir et
  contrôler la lecture média d'autres apps, qui exige l'accès spécial
  "Accès aux notifications" (`FoxNotificationListenerService`, un
  `NotificationListenerService` sans logique propre, uniquement présent
  comme point d'ancrage pour cette permission — FoxOFF ne lit jamais le
  contenu des notifications). Best-effort comme le reste des interactions
  montre/TV : ne fait rien si l'accès n'est pas accordé. Demandée dès
  l'installation (nouvelle étape d'onboarding `PhoneMediaPauseScreen`,
  entre Créneaux horaires et Détection montre) mais **non bloquante** —
  "Continuer" reste toujours actif ; rappel disponible dans Réglages pour
  l'activer plus tard. Câblé dans `SleepPauseCoordinator.executePause()`,
  journalisé dans l'Historique (nombre de lectures effectivement mises en
  pause). **Statut : compilé et vérifié par build complet, non testé sur
  matériel réel** (même limite que Garmin/confirmation "toujours là" —
  nécessite un vrai téléphone avec une app média en cours de lecture pour
  valider).
- 🐛 **Correctif (2026-08-13)** : le heartbeat de reconnexion montre/TV
  (15 min) s'arrêtait complètement dès `tvIsPaused` (sommeil confirmé),
  comme le visibility watchdog ci-dessus — mais pour lui, une régression
  réelle : plus aucune revérification de `TvConnectionStatus` ne se
  produisait ensuite, donc FoxOFF ne pouvait jamais détecter qu'une TV
  remise en Play manuellement par l'utilisateur avait repris, tant que
  l'app n'était pas rouverte (constaté : "j'ai remis play ça n'a pas
  repris la surveillance"). Corrigé pour RALENTIR (15 → 30 min) plutôt que
  s'arrêter — décision explicite de l'utilisateur, compromis batterie
  assumé pour rester capable de détecter une reprise dans un délai
  raisonnable. Le visibility watchdog, lui, garde son comportement d'arrêt
  complet (décision distincte, non remise en cause).
  `restartHeartbeatIfStopped()` simplifié en conséquence (ne couvre plus
  que le watchdog). **Statut : compilé et vérifié par build complet, non
  revérifié sur une nuit réelle** — à confirmer.
- 🐛 **Raffinement (2026-08-13, même jour)** : demande explicite de
  l'utilisateur — le heartbeat ralenti ne doit pas tourner à 30 min toute
  la nuit. Impossible de détecter une vraie reprise pour s'arrêter au bon
  moment (la télécommande Freebox envoie une simple touche Lecture/Pause à
  l'aveugle, voir `TvCommandSender` — aucune API d'état TV branchée, champs
  `TvDevice.isPlaying`/`TvEvent.PlaybackStateChanged` déclarés mais jamais
  alimentés). Option choisie par l'utilisateur parmi 3 proposées : plafond
  de temps simple. `POST_PAUSE_HEARTBEAT_MAX_DURATION` — passé ce délai
  sans signal de reprise, le heartbeat s'arrête complètement
  (`heartbeatStopped` réintroduit), comme avant le correctif ci-dessus,
  mais après une fenêtre de rattrapage au lieu d'immédiatement. Ramené de
  2h à 30 min le jour même (choix explicite de l'utilisateur) : avec
  `POST_PAUSE_HEARTBEAT_INTERVAL_MS` = 30 min aussi, ça revient à UNE
  seule reconnexion de rattrapage après la pause, puis arrêt complet.
  `restartHeartbeatIfStopped()` couvre à nouveau les deux boucles.
  **Statut : compilé et vérifié par build complet, non revérifié sur une
  nuit réelle** — à confirmer.

- ✅ **Fait (2026-08-13)** : la montre coupe désormais RÉELLEMENT ses
  capteurs (BPM actif, BPM passif, mouvement) en dehors des plages
  horaires choisies par l'utilisateur (`ActiveHoursSettings`), pas
  seulement le téléphone. Jusqu'ici, `FoxServiceReconciliation` arrêtait
  bien `FoxForegroundService` hors créneau, mais la montre continuait de
  mesurer et d'envoyer en continu — aucun lien entre les deux. Nouveaux
  chemins téléphone↔montre `/foxoff/monitoring_stop` /
  `/foxoff/monitoring_start` (même pattern que
  `/foxoff/movement_low_power`), envoyés uniquement lors d'une vraie
  transition (`RealActions.start()/stop()/disableAndStop()`), jamais à
  chaque vérification périodique de `FoxActiveHoursWorker` (15 min).
  Côté montre, `FoxWearCore.setMonitoringActive()` appelle
  `startMonitoring()`/`stopMonitoring()` (déjà existantes mais jamais
  déclenchées après le lancement initial de l'app) — rendues idempotentes
  (flag `monitoringActive`) pour rester sûres en cas de messages
  redondants. Le service premier plan montre (notification + wake lock)
  n'est volontairement PAS arrêté : seuls les capteurs le sont, pour ne
  pas risquer que le processus montre soit tué par le système et devienne
  injoignable pour le message de reprise. **Statut : compilé (app + wear)
  et vérifié par build complet, non testé sur matériel réel** — à
  confirmer sur une prochaine nuit/journée de test.

- 🔎 **Diagnostic ajouté (2026-08-14)** : écart réel constaté sur une nuit
  de test — endormissement détecté par Samsung Health à 00h23 contre
  02h31 par FoxOFF, alors que le BPM était déjà bas et stable dès 00h07.
  Analyse du journal : `bpmDropBonus` (`FoxBrain.kt`) ne peut s'accorder
  qu'une seule fois par échantillon BPM RÉELLEMENT reçu (aucune minuterie
  indépendante), et rien ne garantit la cadence de livraison du BPM
  passif côté Health Services (`PassiveMonitoringEngine.kt`, aucun
  intervalle configurable sur `PassiveMonitoringClient` — entièrement
  décidé par l'OS/le matériel de la montre). Le score de cette nuit-là a
  d'ailleurs bondi de 38% à 92% en 25 min une fois les mouvements
  calmés, ce qui confirme que la formule fonctionne bien quand elle reçoit
  des échantillons à un rythme correct — le vrai frein pendant les 2
  premières heures est très probablement la fréquence d'arrivée du BPM,
  pas les seuils de score déjà réglés cette session.
  `NightLogType.HEART_RATE_TREND` existait déjà dans le code (déclaré,
  affiché dans `HistoryScreen.kt`, jamais réellement enregistré) — branché
  dans `FoxCore.kt` sur chaque `HeartRateReceived`, pour mesurer
  précisément l'écart réel entre deux échantillons BPM consécutifs sur une
  prochaine nuit, plutôt que de le déduire indirectement des changements
  d'état déjà loggés (`SLEEP_STATE_CHANGE`, qui ne se déclenche que sur un
  changement de catégorie ou de raison de score, pas à chaque BPM).
  **Statut : compilé et vérifié par build complet — diagnostic pur, aucun
  changement de comportement de détection.** Prochaine étape : analyser le
  nouveau journal "BPM" après une nuit de test pour confirmer ou infirmer
  l'hypothèse avant de décider d'un correctif (ex: renforcer le bonus
  d'immobilité `stationaryDurationBonus`, qui lui dépend du mouvement —
  échantillonné de façon fiable toutes les 30s — plutôt que du BPM).

**Sortie (spécifique)** : cycle veille→endormissement→pause TV validé sur appareil réel, écran éteint, sur une nuit complète, sans relance manuelle et **sans** exemption batterie activée par défaut.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 1 semaine.

---

## Phase 7 — UI historique & réglages

**Objectif** : exploiter la persistance (Phase 3) et la fiabilité (Phase 6)
pour donner de la valeur visible à l'utilisateur.

- Écran d'historique de sommeil (sessions, durée, BPM min/max — données déjà présentes dans `DashboardUiState`/`FoxBrainState`, à agréger et persister).
- Graphiques BPM (nuit en cours + historique).
- Écran de réglages : sensibilité de détection (exposer `SleepScoringConfig`), gestion de la TV appairée, préférences de notification.
- Wear UI (`WearHomeScreen`) : affichage BPM live, statut de connexion au téléphone.

✅ **Anticipé (2026-08-07)**, hors séquence normale (besoin immédiat de
diagnostic, pas d'attendre la Phase 7) : onglet "Historique" (`HistoryScreen.kt`)
+ `NightLog` (`core/automation/`) — journal chronologique de tout ce que
FoxOFF détecte/fait pendant la nuit (changements de score/état de sommeil
avec BPM et raison, mouvements, TV on/off, montre connectée/déconnectée,
compte à rebours démarré/exécuté/annulé), avec un bouton "Copier" pour
coller directement le texte dans une conversation. Stockage JSON borné à
2000 entrées (SharedPreferences, même pattern que SleepDetectionHistory).
Ne remplace pas les graphiques/historique multi-nuits prévus ici — c'est un
outil de diagnostic ponctuel, pas une vue d'ensemble sur plusieurs nuits.

✅ **Fait (2026-08-12)**, refonte de `WearHomeScreen` — demande explicite :
"juste le renard qui affiche que l'application surveille l'endormissement",
plus le tableau de bord technique brut (BPM, connexion, backend, diagnostic
moteur passif) qui s'affichait par défaut jusque-là. Nouvel écran principal
minimaliste : illustration du renard (`fox_sleeping_hero.png`, réutilise
l'artwork généré pour l'icône/l'image de présentation Play Store — même
fond `#00000A` que le reste de l'identité de marque) + un texte reflétant
`isMonitoring` en direct. Le tableau de bord technique n'est pas supprimé
(dépannage réel, notamment la note permanente Samsung sur le BPM écran
éteint) — déplacé dans `WearDiagnosticsView`, sur un second onglet
"Réglages" accessible par glissement horizontal (`HorizontalPager` de
Compose Foundation + `HorizontalPageIndicator` de Wear Compose Material,
motif standard sur Wear OS — nécessite un petit adaptateur
`PagerIndicatorState` pour relier les deux API, incompatibles nativement).
Nouvelle dépendance `androidx.compose.foundation:foundation` ajoutée
(absente jusque-là du module wear). **Statut : compilé et vérifié par
build complet, rendu visuel et geste de glissement non vérifiés sur
matériel réel** (même limite que les autres écrans Wear cette session —
aucun émulateur/appareil connecté dans cet environnement).

✅ **Fait puis remplacé (2026-08-12, même jour)** : première version de
l'indicateur actif/inactif sur l'accueil montre — renard teinté en vert
(`ColorFilter.tint`) quand actif, couleurs d'origine sinon. L'utilisateur a
demandé une autre représentation juste après ; voir l'entrée suivante pour
la version retenue.

✅ **Fait (2026-08-12, même jour)** : seconde version, retenue —
"il veille" vs "il dort" plutôt qu'un changement de couleur — d'abord
simulé par un halo doré pulsant + désaturation (aucun nouvel asset
disponible à ce moment). Statut initial : compilé, non vérifié sur
matériel réel.

✅ **Fait (2026-08-12, même jour)** : remplacé par un vrai second asset —
l'utilisateur a généré `fox_watching_hero.png` (yeux ouverts, même style
exact que l'icône existante : médaillon circulaire, anneau bleu/orange,
fond nuit, lune, étoiles) via un outil externe de génération d'image, à
partir d'un prompt fourni. Recadré/détouré en cercle transparent 768×768
avec PowerShell/System.Drawing (même méthode que les autres traitements
d'image cette session — scan de la bounding box du contenu non blanc,
masque elliptique avec léger inset pour couper le liseret d'anti-aliasing
résiduel). `WearIdleScreen` bascule maintenant entre les deux vraies
illustrations (`fox_watching_hero` actif / `fox_sleeping_hero` inactif)
selon `isMonitoring`, avec juste une légère respiration (plus vive si
actif) conservée en plus — le halo/la désaturation de code ne sont plus
nécessaires. Statut : compilé et vérifié par build complet, rendu visuel
non vérifié sur matériel réel.

✅ **Fait (2026-08-12, même jour)** : les deux illustrations passent en
plein écran (`ContentScale.Crop`, `Modifier.fillMaxSize()`) au lieu d'une
petite icône centrée — demande explicite. Le cercle du badge déborde
naturellement jusqu'aux bords d'un cadran rond puisque son fond sombre est
déjà assorti à `FoxWatchBackground`. Le texte ("Fox veille sur vous" /
"Fox dort") reste incrusté par-dessus, en bas de l'écran, sur un fond noir
semi-opaque pour rester lisible quel que soit le contenu de l'image
derrière. Statut : compilé, non vérifié sur matériel réel.

✅ **Fait (2026-08-12)** : renard animé sur `FoxAnalysisCard` (Accueil
téléphone) — demande explicite de l'utilisateur ("rendre l'appli
interactive"), sans nouvel asset (aucun outil de génération d'image
disponible ici) : anime l'illustration existante
(`ic_launcher_foreground`, même artwork que l'icône de l'app) par
transformation Compose (`graphicsLayer` — échelle + rotation) plutôt que
par de nouveaux dessins. Deux comportements superposés dans
`AnimatedFoxAvatar` (`DashboardComponents.kt`) : une "respiration"
continue dont le rythme ralentit et l'amplitude diminue à mesure que
`SleepState` s'approfondit (AWAKE rapide/ample → ASLEEP lent/quasi
immobile, sans balancement), pour refléter l'état de surveillance en
direct ; et un petit rebond ludique au tap, indépendant de l'état — le
côté purement interactif demandé en plus. **Statut : compilé et vérifié
par build complet, rendu visuel non vérifié en conditions réelles**
(aucun émulateur/appareil connecté dans cet environnement).

✅ **Fait (2026-08-14)** : refonte de l'Accueil téléphone en "centre de
contrôle" à deux états dynamiques (demande explicite, gabarit précis
fourni). Nouveau composant `FoxHomeHero` (`ui/dashboard/components/
FoxHomeHero.kt`) inséré en haut de l'onglet Accueil existant, au-dessus
des cartes déjà en place (`MainStatusCard`, `ActiveHoursInfoCard`,
`FoxAnalysisCard`, `DeviceCard`, `TvDashboardCard` — aucune supprimée,
toutes conservées telles quelles plus bas dans le scroll). Piloté par
`BackgroundServiceSettings.enabledState` (même source de vérité que
Réglages, pas un nouvel état parallèle) : grande illustration + halo/texte
bleu ("Surveillance active") ou orange ("Surveillance inactive"), bouton
"Activer/Désactiver la surveillance" (même logique de permission
POST_NOTIFICATIONS que `SettingsScreen`, dupliquée volontairement plutôt
que factorisée pour ne prendre aucun risque de régression sur l'écran
Réglages déjà fonctionnel), et deux puces discrètes "Montre connectée" /
"TV connectée". Illustrations réutilisées telles quelles depuis le module
montre (`fox_watching_hero`/`fox_sleeping_hero`, copiées dans
`app/src/main/res/drawable/` — les modules Android ne partagent pas leurs
ressources) : même mascotte que la montre et le Character Bible
(`docs/FOX_CHARACTER_BIBLE.md`), pas un nouveau design. Orange réutilisé
de la palette déjà existante (`Color(0xFFFFA000)`, déjà utilisé partout
ailleurs pour les états "attention/inactif") plutôt qu'inventé. **Statut :
compilé et vérifié par build complet, rendu visuel non vérifié en
conditions réelles** (aucun émulateur/appareil connecté dans cet
environnement) — à confirmer en lançant l'app.

Révisé le même jour (2026-08-14), toujours à la demande de l'utilisateur :
- **Épuration** : seulement 3 infos sous le héro (montre, TV, plage
  horaire) — `MainStatusCard`/`FoxAnalysisCard` retirés de cet écran
  (restent définis dans `DashboardComponents.kt`, réutilisables ailleurs).
- **Image en fond plein cadre, tout sur une seule page** : `FoxHomeHero`
  occupe désormais toute la zone de contenu de l'onglet (`Box` plein
  écran, plus de `verticalScroll` ni de cartes séparées empilées en
  dessous) — l'illustration remplit tout le fond, texte/bouton/infos
  superposés par-dessus avec un dégradé sombre pour rester lisibles,
  panneau "verre" compact pour les 3 infos plutôt que des cartes pleines.
  Fonctionnalité vérifiée non perdue au passage : le bouton "Reconnecter"
  la montre (auparavant sur `DeviceCard`, seul point d'entrée pour ce
  geste, absent de `HealthScreen.kt`) déplacé sur la ligne montre du
  panneau compact, cliquable uniquement si déconnectée.

  ✅✅ **Première vérification visuelle réelle de la session (2026-08-14)** :
  AVD Pixel 8 créé et piloté par ligne de commande (`emulator.exe` +
  `adb`, SDK/plateforme déjà installés via Android Studio) — installation
  de l'app, captures d'écran, taps simulés, lecture directe des images.
  Les deux états (actif/inactif, illustrations, couleurs, panneau compact)
  confirmés visuellement conformes à la demande.

  🐛→✅ **Vrai crash trouvé et corrigé au passage** : `Context
  .startForegroundService()` engage Android à ce que le service appelle
  `Service.startForeground()` sous quelques secondes.
  `FoxForegroundService.prerequisitesMet()` vérifie déjà `BLUETOOTH_CONNECT`
  en plus de la visibilité de la notification, mais si cette permission
  manquait au moment de l'appel, le service s'arrêtait via `failAndStop()`
  SANS jamais avoir appelé `startForeground()` — Android tue alors TOUTE
  l'application (`ForegroundServiceDidNotStartInTimeException`), pas
  seulement ce service. Reproduit en direct sur l'émulateur (permission
  révoquée). Touchait aussi bien `FoxHomeHero` que
  `SettingsScreen.enableBackgroundSurveillance()`, qui avait exactement la
  même faille depuis le début — corrigé aux deux endroits : vérification
  de `BLUETOOTH_CONNECT` (avec re-demande si besoin, même mécanisme que
  `POST_NOTIFICATIONS`) AVANT tout appel à `startForegroundService()`,
  jamais après. **Statut : corrigé et reproduit/re-vérifié sur émulateur
  réel avec le correctif (permission révoquée -> demande propre, plus de
  crash) — le seul correctif de toute la session validé sur un appareil
  réel plutôt que par simple compilation.**

  🔁 **Re-refonte le même jour (2026-08-14)**, sur une nouvelle maquette de
  référence fournie par l'utilisateur, différente de la précédente et avec
  consigne explicite de la reproduire fidèlement — remplace le panneau "3
  infos" (montre/TV/plage horaire) ci-dessus, qui ne correspondait plus à
  la demande. `FoxHomeHero` simplifié en `(surveillanceActive, modifier)` :
  header centré "Fox⏻FF" (icône power à la place du "O") + tagline,
  illustration contenue (coins arrondis, plus de plein-cadre), statut et
  description sous l'image (plus par-dessus), bouton rond avec halo coloré
  (`Modifier.shadow` teinté plutôt que `blur`, pour rester compatible sans
  gestion spécifique API 31+) et légende séparée en dessous. Deux écarts
  délibérés signalés à l'utilisateur : navigation à 5 onglets conservée
  (la maquette en montre 4) et bouton "Reconnecter la montre" retiré (plus
  de panneau infos pour l'accueillir).

  ✅✅ **Vérifié sur l'émulateur Pixel 8** : build vert
  (`BUILD SUCCESSFUL`), app installée et relancée, capture d'écran de
  l'état "Surveillance active" conforme à la maquette (halo bleu, renard
  éveillé, bouton bleu). Bascule testée par tap réel sur le bouton (bornes
  cliquables lues via `uiautomator dump` pour viser juste — le bouton rond
  de 76dp est une cible bien plus petite que l'ancienne barre pleine
  largeur) : état "Surveillance inactive" confirmé (halo orange, renard
  endormi, bouton orange, texte "Appuyez pour activer"). Les deux états
  dynamiques et le changement immédiat au tap sont donc vérifiés en
  conditions réelles, pas seulement compilés.

  🎨 **Refonte du tout premier écran (2026-08-14)** : `WelcomeScreen`
  (`ui/onboarding/screens/OnboardingScreens.kt`), premier écran affiché à
  l'installation, entièrement revu à la demande de l'utilisateur avec
  plusieurs allers-retours d'assets/ajustements :
  - **Fond** : `bg_welcome_night.jpg` (ciel étoilé + forêt, fourni par
    l'utilisateur) en plein cadre, léger voile noir pour la lisibilité.
  - **Illustration** : `fox_on_moon_blended.png`, dérivée d'une image
    fournie (renard endormi sur un croissant de lune) par un script
    (`System.Drawing` en PowerShell, pas d'outil d'édition d'image
    disponible ici) qui recadre l'image au plus près du sujet (511×700,
    deux passes de recadrage) et applique un dégradé alpha elliptique
    (opaque au centre → transparent sur les bords) pour qu'elle se fonde
    dans le fond plutôt que d'apparaître comme un rectangle collé dessus.
  - **Wordmark "Fox⏻FF"** (`FoxComponents.kt`, composant `FoxWordmark`
    réutilisable) : police "Outfit" (Google Fonts, licence SIL OFL,
    `res/font/outfit_variable.ttf` + `docs/licenses/OUTFIT_FONT_OFL.txt`)
    chargée via `FontVariation.weight(...)` — "Fox" en graisse Black
    (900), "FF" en Bold (700), toutes deux blanches (le "FF" était
    auparavant en bleu, changement explicite). Symbole power dessiné à la
    main via `Canvas` (arc + trait, `PowerGlyph`) plutôt que l'icône
    Material `PowerSettingsNew` — dont l'épaisseur de trait fixe ne
    pouvait pas être ajustée — avec dégradé métallique clair→sombre et
    ombre portée décalée pour un rendu "en relief".
  - **Message** : reformulé pour mettre le sommeil en sujet principal
    plutôt que la télévision : "FoxOFF veille sur votre sommeil grâce à
    votre montre connectée, et met en pause ce que vous regardez dès que
    vous vous endormez."
  - **Écran "splash" supprimé** du flux de navigation
    (`OnboardingNavigation.kt` : `startDestination` passe de `"splash"` à
    `"welcome"`) — l'ancienne animation "FOXOFF" avec pause noire avant
    n'apparaît plus, `WelcomeScreen` est désormais le tout premier écran.
  - **Flash blanc au démarrage froid corrigé** : `android:windowBackground`
    (hérité de `Theme.Material.Light`, blanc par défaut) aligné sur la
    couleur exacte du fond nocturne (`#000519`) dans
    `res/values/themes.xml`, plus un override `res/values-v31/themes.xml`
    pour le splash screen système d'Android 12+ (`windowSplashScreenBackground`
    + `windowSplashScreenAnimatedIcon`) — ce splash système est imposé par
    l'OS au démarrage froid et ne peut pas être supprimé, seulement
    thématisé pour ne plus jurer avec le reste de l'app.

  ✅✅ **Chaque itération vérifiée sur l'émulateur Pixel 8** (reset du
  flag onboarding via `adb shell run-as ... rm shared_prefs`, réinstall,
  capture d'écran, lecture directe des images) — pas seulement compilé.

**Sortie (spécifique)** : un utilisateur peut consulter ses 7 dernières nuits et ajuster la sensibilité sans redémarrer l'app.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 1-2 semaines.

---

## Phase 8 — Personnalisation avancée / IA

**Objectif** : différenciation produit, une fois le socle solide et
l'historique de données disponible (Phase 5 + Phase 7). Ne bloque **pas** la
publication (Phase 9) — peut être menée en parallèle ou après.

- Détection de patterns (heure de coucher habituelle, jours de semaine vs week-end) à partir de l'historique Room.
- Suggestions personnalisées (ajustement automatique de seuils selon l'historique de faux positifs/négatifs signalés par l'utilisateur).
- **C'est ici, et seulement ici**, qu'un modèle on-device léger (TensorFlow Lite) peut être évalué, en complément du moteur à règles existant (jamais en remplacement) — uniquement une fois qu'il y a assez de données historisées pour que ça ait un sens.
- Piste V2.x (hors périmètre, à ne pas complexifier cette phase) : automatisations connexes (lumières, autres appareils domotiques).

**Sortie (spécifique)** : à définir selon retours utilisateurs de la Phase 7 — cette phase est volontairement ouverte. Si TFLite est retenu, il reste optionnel et le moteur à règles reste le fallback par défaut.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

---

## Phase 9 — Préparation Play Store

**Objectif** : publication conforme et sereine. Ne nécessite **pas** que la
Phase 8 soit terminée.

> **Démarrée le 2026-08-10**, dans le but précis de faire tester l'app à
> quelques proches via le track **Test interne** (100 testeurs max, sur
> invitation, jamais visible/téléchargeable publiquement) — pas une
> publication grand public.

- ✅ **Fait (2026-08-10)** : config de signing release. Clé "upload"
  générée (`keystore/foxoff-upload.jks`, PKCS12, validité 10 000 jours),
  `keystore/keystore.properties` (mots de passe) — dossier entier exclu du
  dépôt (`.gitignore`). `app/build.gradle.kts` charge ce fichier
  optionnellement (absent sur une autre machine/CI → build release non
  signé plutôt qu'échec). `assembleRelease` et `bundleRelease` vérifiés :
  APK et AAB signés générés avec succès. Recommandé côté utilisateur lors
  de la création de l'app dans Play Console : activer **Play App
  Signing** (Google gère la vraie clé de signature ; cette clé "upload"
  reste remplaçable en cas de perte, contrairement à l'ancien modèle).
- ✅ **Fait (2026-08-10)** : brouillon de politique de confidentialité
  ([PRIVACY.md](PRIVACY.md)) — reflète le fonctionnement réel de l'app
  (aucun serveur, aucune donnée transmise hors de l'appareil, Health
  Connect en lecture seule, historique local effaçable). **Pas encore
  poussé sur le dépôt distant** — nécessaire pour obtenir une URL publique
  à renseigner dans Play Console.
- ✅ **Fait (2026-08-10)** : aide-mémoire du formulaire "Sécurité des
  données" ([docs/PLAY_STORE_DATA_SAFETY.md](docs/PLAY_STORE_DATA_SAFETY.md))
  — réponses précises catégorie par catégorie, à recopier dans Play
  Console (formulaire non remplissable automatiquement, nécessite le
  compte développeur de l'utilisateur).
- ⏳ **Reste à faire, côté utilisateur** (nécessite son propre compte/
  paiement — hors de portée d'un agent) : créer le compte développeur
  Google Play (25$ US, paiement unique), créer l'app dans Play Console,
  pousser `PRIVACY.md` sur GitHub pour obtenir son URL publique, remplir le
  questionnaire de classification de contenu et le formulaire Data Safety
  (voir aide-mémoire ci-dessus), créer le track Test interne, y uploader
  `app-release.aab`, inviter les testeurs par email.
- ✅ **Fait (2026-08-12)** : durcissement de la protection des données
  locales, demande explicite de l'utilisateur ("j'aimerai que les données
  des utilisateurs soit extrêmement protégé"). Deux correctifs :
  1. **Sauvegarde automatique désactivée** (`android:allowBackup="false"`) —
     activée sans aucune règle d'exclusion, elle sauvegardait TOUTES les
     données de l'app (BPM, sommeil, historique) vers Android Auto Backup,
     donc vers le Google Drive de l'utilisateur : une contradiction directe
     avec PRIVACY.md ("aucune donnée ne quitte votre appareil"), repérée en
     creusant le sujet. Fichiers `data_extraction_rules.xml`/
     `backup_rules.xml` devenus inutiles, supprimés.
  2. **Chiffrement au repos de toutes les SharedPreferences** — nouveau
     `FoxEncryptedPrefs` (Jetpack Security `EncryptedSharedPreferences`,
     clé maître dans l'Android Keystore matériel, jamais exportable) ;
     remplace `context.getSharedPreferences()` dans les 11 fichiers qui
     l'utilisaient, y compris **la clé privée TLS d'identité TV**
     (`TvKeyStore`/`TvIdentity` — la donnée la plus sensible de l'app,
     jusque-là stockée en clair). Choix délibéré de tout chiffrer
     uniformément plutôt que de maintenir une liste "sensible/pas
     sensible" (source d'oubli future). Point d'injection de test ajouté
     (`FoxEncryptedPrefs.setTestFactory()`) car l'Android Keystore réel
     n'existe pas dans les tests JVM — `BackgroundServiceSettingsTest`,
     `SleepDetectionHistoryTest`, `FoxTvSettingsTest` adaptés en
     conséquence (mockaient auparavant `Context.getSharedPreferences()`
     directement). PRIVACY.md mis à jour avec une nouvelle section
     "Chiffrement des données stockées". **Statut : compilé et vérifié par
     build complet (169 tests verts), non vérifié sur matériel réel** — la
     génération de clé Keystore n'est possible qu'avec un vrai
     KeyStore Android (émulateur/appareil), absent de cet environnement.
- 🐛 **Régression + correctif (2026-08-12, même jour)** : blocage de 3-4
  minutes au premier lancement après réinstallation, remonté par
  l'utilisateur ("initialisation Fox Brain" bloquée). Cause trouvée : la
  génération de la clé maître Android Keystore (première utilisation de
  `FoxEncryptedPrefs`, opération cryptographique réelle, pas instantanée)
  se produisait de façon SYNCHRONE sur le thread principal — notamment
  `OnboardingSettings.isCompleted()`, lue directement dans le `remember{}`
  de la toute première composition de `MainActivity`, et
  `BackgroundServiceSettings.isEnabled()` / `FoxCore.initialize()` /
  `FoxServiceReconciliation.reconcileNow()` appelés sur le dispatcher
  `Main` depuis `FoxApplication`. Corrigé : (1) `OnboardingSettings`
  repassé en SharedPreferences en clair délibérément — un simple booléen
  "onboarding terminé", jamais sensible, lu de façon synchrone avant même
  que le cache chiffré ait pu s'amorcer, ne justifiait pas ce coût ; (2)
  `appScope` de `FoxApplication` passé de `Dispatchers.Main` à
  `Dispatchers.IO` ; (3) `FoxServiceReconciliation.reconcileNow()`
  dispatche maintenant son corps sur un scope IO interne plutôt que de
  bloquer l'appelant (appelé depuis un callback de cycle de vie sur Main) ;
  (4) amorçage (`warm-up`) de tous les fichiers `FoxEncryptedPrefs` connus
  en arrière-plan dès `FoxApplication.onCreate()`, pour que les écrans
  suivants (Réglages, détection montre...) qui les lisent encore de façon
  synchrone bénéficient d'un cache déjà prêt. Les données réellement
  sensibles (BPM, sommeil, clé TLS TV) restent chiffrées — seul le flag
  onboarding en est sorti. **Statut : compilé et vérifié par build
  complet, correctif non revérifié sur le device où le blocage a été
  observé** — à confirmer.
- Vérifier/compléter les règles ProGuard/R8 (`rules.keep` existe déjà — à valider avec un build `release` réel).
- Monitoring crash en production (Crashlytics ou équivalent).
- Tests sur la TV principale ciblée + versions Wear OS (5/6) supportées.
- Fiche store : captures d'écran, description, icône (déjà présente).

**Sortie (spécifique)** : build release installable via track interne Play Console, formulaire Data Safety validé.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 1 semaine (hors délais de revue Google).

---

## Décisions repoussées

Ces sujets reviennent facilement trop tôt dans une roadmap technique. Ils sont
volontairement écartés de la V2 initiale — à ne réintroduire que sur besoin
avéré, pas par anticipation.

| Décision | Repoussée à | Pourquoi |
|---|---|---|
| **TensorFlow Lite / ML on-device** | Phase 8, si justifié | Le moteur à règles (`WeightedSleepAnalyzer`) suffit tant qu'il n'y a pas d'historique de données pour entraîner/valider un modèle. Introduire du ML avant d'avoir des données réelles, c'est optimiser dans le vide. |
| **Support multi-TV** | Non planifié — sur demande utilisateur avérée | La V2 initiale cible une TV principale fiable (Phase 4). Le multi-TV multiplie la complexité de reconnexion, de sélection et de test sans valeur prouvée à ce stade. |
| **Hilt avancé / modularisation Gradle** | Non planifié — sur besoin réel | La Phase 3 introduit Hilt de façon minimale dans `:app` existant. Découper en modules Gradle séparés (`:feature-tv`, `:feature-sleep`...) n'apporte rien tant qu'une seule personne travaille sur le projet et qu'il n'y a pas de temps de build à optimiser. |
| **Exemption d'optimisation batterie par défaut** | Jamais par défaut — dernier recours documenté | Demander cette exemption au premier lancement est une mauvaise pratique Android (friction, méfiance utilisateur) et une béquille qui masque un foreground service mal implémenté. Phase 6 doit d'abord rendre le service standard fiable. |

---

## Dette technique identifiée

| Constat | Localisation | Traité en |
|---|---|---|
| Pas de git / pas de CI / pas de tests | racine du projet | Phase 0 |
| Wear OS ne collecte ni n'envoie rien (dépendance Health Services inutilisée) | module `wear` | Phase 1 |
| Pas de foreground service → boucle tuable par Doze | `PhoneWearListenerService` | Phase 1 (minimal) → Phase 6 (définitif) |
| ~~Seuils de décision codés en dur (0.70, 0.80)~~ | `FoxCore.startOrchestration()` | ✅ Corrigé 2026-08-07 |
| ~~`FoxModule`/`registerModule` déclaré mais jamais utilisé~~ | `core/module/FoxModule.kt` | ✅ Supprimé 2026-08-07 |
| `FoxCore` God Object (event bus + état + orchestration + règles) | `core/application/FoxCore.kt` | Phase 3 |
| Code TV legacy orphelin, coexiste avec la chaîne de production validée (`TvRemoteClient`) | `tv/TvConnectionManager.kt`, `tv/TvCommandSender.kt`, `tv/protobuf/RemoteFactory.kt` | Phase 4 |
| Coexistence de deux identités TLS (`TvKeyStore` ancien, `TvIdentity` actif) — cible : une seule implémentation TLS, stockée dans l'Android Keystore | `tv/TvKeyStore.kt`, `tv/pairing/TvIdentity.kt` | Phase 4 |
| Absence du plugin Gradle Protobuf sur `:app` — classes générées versionnées manuellement au lieu d'être produites depuis les `.proto` | `app/build.gradle.kts`, `src/main/proto/*.proto` | Phase 4 |
| ~~Aucun test sur le moteur de scoring (logique métier critique)~~ | `brain/` | ✅ 43 tests ajoutés 2026-08-07 |
| Pas de notification channel malgré permission déclarée | `AndroidManifest.xml` | Phase 6 |

---

## Vue d'ensemble

```
Phase 0  Socle projet (git, CI, tests)                — non négociable, avant tout
Phase 1  Boucle réelle Watch → Phone → Brain           — valider AVANT de refactorer
Phase 2  Stabilisation fonctionnelle minimale          — corrige, ne refond pas
Phase 3  Refactor architectural progressif             — Hilt + Room + use cases, incrémental
Phase 4  TV Engine durci (une TV fiable)               — legacy retiré, TLS unifié, protobuf généré par Gradle
Phase 5  Sleep Engine V2 + tests                        — cœur métier fiable, 100% règles
Phase 6  Automatisation & fiabilité runtime             — foreground service avant exemption batterie
Phase 7  UI historique & réglages                       — valeur visible
Phase 8  Personnalisation / IA (TFLite ici, pas avant)  — ne bloque pas la Phase 9
Phase 9  Préparation Play Store                         — ne dépend pas de la Phase 8
```

Chaque phase produit une app **compilable et fonctionnelle**, satisfait le
**Definition of Done** universel en plus de son critère de sortie spécifique,
et respecte la matrice de dépendances ci-dessus : on ne démarre une phase que
si toutes celles dont elle dépend sont closes et validées sur appareil réel.
