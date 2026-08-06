# FoxOFF V2 — Feuille de route technique

> Document de référence du projet, à tenir à jour à chaque phase franchie.
> Dernière mise à jour : 2026-08-06 (v3 — Phase 4 corrigée après audit de gouvernance : plus de legacy TV, TLS unifié, plugin Protobuf. Voir [AUDIT.md](AUDIT.md) §1).

## Vision

FoxOFF détecte l'endormissement de l'utilisateur via une montre Wear OS (fréquence
cardiaque + mouvement) et coupe automatiquement la TV Android (via le protocole
Android TV Remote v2). V2 fait passer le projet d'un prototype fonctionnel par
morceaux à une application **professionnelle, modulaire, testée et
publiable sur le Play Store**.

Le projet couvre 7 sous-systèmes :

| Sous-système | Rôle | État actuel |
|---|---|---|
| App Android (téléphone) | UI, orchestration, réception capteurs | Prototype fonctionnel |
| Wear OS | Capture BPM/mouvement, envoi au téléphone | Squelette (permissions only, **aucune collecte réelle**) |
| Communication Watch↔Phone | Data Layer API (MessageClient) | Récepteur prêt côté phone, **rien n'émet côté montre** |
| TV Engine | Découverte, appairage, contrôle Android TV | Appairage et contrôle en production déjà fonctionnels (protobuf généré) ; du code legacy orphelin et une double identité TLS restent à nettoyer |
| Sleep Engine / Fox Brain | Scoring pondéré de probabilité de sommeil | Moteur à règles fonctionnel, non testé, non calibré par utilisateur |
| Automatisation | Service d'orchestration, fiabilité background | Pas de foreground service, app tuable par Doze |
| UI | Onboarding + Dashboard Compose | Écrans en place, pas d'historique ni réglages |

**Aucun test, aucune CI, et le dépôt n'est pas encore sous git** — c'est le
premier chantier avant tout le reste.

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

**Objectif** : rendre le projet gouvernable avant d'y toucher.

- Initialiser git (`git init`), premier commit, dépôt distant privé (GitHub/GitLab).
- CI (GitHub Actions) : `./gradlew build lint testDebugUnitTest` sur chaque push/PR.
- Introduire l'outillage de test : JUnit 5 ou JUnit4+MockK, `kotlinx-coroutines-test`, Turbine (assertions sur `Flow`).
- `README.md` racine : présentation du projet, schéma des modules, commandes de build.
- Convention de commit / branches minimales (peu importe laquelle, mais une seule, documentée).

**Sortie (spécifique)** : `git log` non vide, CI verte sur un commit trivial, `./gradlew test` exécute (même vide) sans erreur.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 2-3 jours.

---

## Phase 1 — Valider la boucle réelle Watch → Phone → Brain

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
- Ajouter la détection de mouvement/immobilité côté montre (accéléromètre) — `FoxBrainEvent.MovementDetected` / `StillnessDetected` / `WalkingDetected` existent déjà côté Brain mais **rien ne les publie actuellement**.
- Envoi périodique via `MessageClient` (`/foxoff/hr`, `/foxoff/watch_info`, nouveaux paths pour mouvement).
- Un **keep-alive minimal** (service de test, pas encore l'orchestrateur définitif de la Phase 6) pour que la boucle tienne le temps d'un test nocturne réel — la robustesse Doze/App Standby complète est traitée en Phase 6, pas ici.
- Tests instrumentés du round-trip Data Layer (émulateur téléphone + émulateur montre appairés).

**Sortie (spécifique)** : un vrai BPM mesuré sur la montre modifie l'état `FoxBrain` sur le téléphone, sur une nuit de test réelle, sans refactor architectural préalable.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 2 semaines.

---

## Phase 2 — Stabilisation fonctionnelle minimale

**Objectif** : corriger ce que la Phase 1 a révélé et nettoyer les points
fragiles évidents, **sans** toucher à l'architecture globale. `FoxCore` reste
le point d'orchestration unique ici — la découpe en couches, c'est la Phase 3.

- Déplacer les seuils de décision codés en dur (`0.70`, `0.80` dans `FoxCore.startOrchestration()`) vers `SleepScoringConfig`, qui est déjà le bon endroit pour ça.
- Trancher le sort de `FoxModule`/`ModuleDescriptor` (`core/module/FoxModule.kt`) : interface déclarée mais **jamais implémentée ni enregistrée** (`registerModule` n'est appelé nulle part). Décision : supprimer ce code mort maintenant plutôt que de le traîner jusqu'au refactor — Hilt (Phase 3) couvrira ce besoin proprement.
- Corriger les incohérences ou bugs mineurs mis au jour pendant les tests nocturnes de la Phase 1.
- Nettoyer les logs de diagnostic redondants (`FoxDiagnostic`, `FoxLogger`) sans changer leur API.

**Sortie (spécifique)** : mêmes fonctionnalités que la fin de Phase 1, code mort en moins, magic numbers en moins — comportement observable inchangé.

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 3-5 jours.

---

## Phase 3 — Refactor architectural progressif

**Objectif** : remplacer le God Object `FoxCore` par une architecture en
couches, **incrémentalement** — chaque commit compile et l'app tourne, jamais
de branche géante qui casse tout pendant deux semaines.

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

**DoD** : build debug/release · tests verts · parcours principal testé · zéro régression · doc à jour · commit dédié.

**Effort estimé** : 2 semaines (périmètre élargi par le nettoyage legacy identifié en audit).

---

## Phase 5 — Sleep Engine / Fox Brain V2

**Objectif** : un moteur de décision fiable, testé, calibrable avec les
données réelles obtenues en Phase 1 — c'est le cœur métier du produit.
**Pas de ML embarqué ici** : TensorFlow Lite est explicitement repoussé à la
Phase 8 (voir "Décisions repoussées").

- `WeightedSleepAnalyzer` + `SleepScoringConfig` sont une bonne base (Strategy pattern via `FoxBrainAnalyzer`, config centralisée). Clarifier le rôle de `BasicBrainAnalyzer` (V1 conservée en fallback ? à supprimer si obsolète).
- **Tests unitaires exhaustifs** du moteur de scoring : c'est la logique la plus critique du produit et la plus facile à tester unitairement (pure, sans Android) — aujourd'hui zéro test.
- Calibration personnalisée : BPM de repos individuel (moyenne glissante sur historique Room, cf. Phase 3) plutôt que seuils fixes identiques pour tous les utilisateurs — toujours à base de règles, pas de modèle appris ici.

**Sortie (spécifique)** : couverture de tests >80% sur le package `brain/`, seuils calibrables exposés en réglages utilisateur, moteur toujours 100% rule-based et explicable.

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

- Politique de confidentialité : les données de fréquence cardiaque relèvent de la catégorie **santé** dans Play Console (Data Safety form, justification des permissions `BODY_SENSORS`/`health.READ_HEART_RATE`).
- Vérifier/compléter les règles ProGuard/R8 (`rules.keep` existe déjà — à valider avec un build `release` réel).
- Config de signing release, stratégie `versionCode`/`versionName`.
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
| Seuils de décision codés en dur (0.70, 0.80) | `FoxCore.startOrchestration()` | Phase 2 |
| `FoxModule`/`registerModule` déclaré mais jamais utilisé | `core/module/FoxModule.kt` | Phase 2 |
| `FoxCore` God Object (event bus + état + orchestration + règles) | `core/application/FoxCore.kt` | Phase 3 |
| Code TV legacy orphelin, coexiste avec la chaîne de production validée (`TvRemoteClient`) | `tv/TvConnectionManager.kt`, `tv/TvCommandSender.kt`, `tv/protobuf/RemoteFactory.kt` | Phase 4 |
| Coexistence de deux identités TLS (`TvKeyStore` ancien, `TvIdentity` actif) — cible : une seule implémentation TLS, stockée dans l'Android Keystore | `tv/TvKeyStore.kt`, `tv/pairing/TvIdentity.kt` | Phase 4 |
| Absence du plugin Gradle Protobuf sur `:app` — classes générées versionnées manuellement au lieu d'être produites depuis les `.proto` | `app/build.gradle.kts`, `src/main/proto/*.proto` | Phase 4 |
| Aucun test sur le moteur de scoring (logique métier critique) | `brain/` | Phase 5 |
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
