# Changelog

Toutes les modifications notables de FoxOFF seront documentées dans ce
fichier.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/), et
ce projet adhère au [Semantic Versioning](https://semver.org/lang/fr/).

> **Note sur ce changelog** — le dépôt n'a pas encore d'historique git (voir
> [ROADMAP.md](ROADMAP.md) Phase 0). Aucune version n'a donc encore été
> réellement taguée. Les entrées ci-dessous sont de deux natures :
> - **`[Unreleased]`** décrit fidèlement l'état du code **tel qu'il existe
>   aujourd'hui**, avant tout tag.
> - Les entrées **`0.1.0` à `1.0.0`** sont des **versions prévues**,
>   directement dérivées des phases de [ROADMAP.md](ROADMAP.md). Chaque
>   entrée sera complétée avec une date réelle et un contenu vérifié au
>   moment du tag Git correspondant — jusque-là, elles servent de plan de
>   publication, pas de journal.

## [Unreleased] — état du code avant Phase 0

### Added

- Application téléphone (`:app`) avec onboarding Compose multi-écrans
  (splash, bienvenue, permissions, détection montre, détection/pairing TV,
  validation, fin de configuration).
- Dashboard temps réel (`DashboardScreen`) réactif à l'état du Fox Brain.
- Fox Brain : moteur de scoring de sommeil à règles pondérées
  (`WeightedSleepAnalyzer` + `SleepScoringConfig`), avec une implémentation
  V1 conservée (`BasicBrainAnalyzer`).
- TV Engine : découverte réseau, pairing Android TV Remote v2 par PIN
  (certificats X.509 générés via BouncyCastle), canal de contrôle
  play/pause fonctionnel (`TvRemoteClient`, protobuf généré).
- Réception téléphone des messages Wear Data Layer
  (`PhoneWearListenerService`, chemins `/foxoff/hr`, `/foxoff/watch_info`).
- Application Wear OS (`:wear`) : gestion des permissions capteurs
  (BPM, activité), adaptée aux différences de permissions Wear OS 5/6.
- Écran de diagnostic manuel `TvLabActivity` pour tester la connexion TV
  indépendamment du flux applicatif principal.

### Known Issues (constats de l'audit — voir [ROADMAP.md](ROADMAP.md))

- Aucun test, aucune CI, dépôt non versionné sous git.
- Le module `:wear` ne collecte ni n'envoie aucune donnée capteur — la
  boucle bout-en-bout n'est pas fonctionnelle malgré le récepteur prêt côté
  téléphone.
- `PhoneWearListenerService` n'est pas un foreground service — la boucle
  peut être interrompue par Doze en arrière-plan.
- Deux implémentations parallèles du canal de contrôle TV
  (`TvConnectionManager`/`TvCommandSender` orphelins vs `TvRemoteClient`
  actif) et deux implémentations parallèles de l'identité TLS
  (`TvKeyStore` vs `TvIdentity`) — voir [ARCHITECTURE.md](ARCHITECTURE.md)
  §6.
- Clés privées TLS stockées en clair (Base64) dans `SharedPreferences`, pas
  dans l'Android Keystore.
- Aucun test unitaire sur le moteur de scoring du Fox Brain.

---

## [1.0.0] — Publication Play Store · *prévu*

Correspond à [ROADMAP.md](ROADMAP.md) Phase 9.

### Prévu — Added
- Formulaire Data Safety validé, politique de confidentialité publiée.
- Monitoring crash en production.

### Prévu — Changed
- Build release signé, règles ProGuard/R8 validées.

## [0.7.0] — Personnalisation · *prévu*

Correspond à [ROADMAP.md](ROADMAP.md) Phase 8.

### Prévu — Added
- Détection de patterns d'usage (heure de coucher habituelle).
- Ajustement automatique des seuils selon les retours utilisateur.
- Évaluation d'un modèle TensorFlow Lite en complément du moteur à règles
  (jamais en remplacement — voir décisions repoussées de la Roadmap).

## [0.6.0] — Historique & réglages · *prévu*

Correspond à [ROADMAP.md](ROADMAP.md) Phase 7.

### Prévu — Added
- Écran d'historique de sommeil (durée, BPM min/max par nuit).
- Graphiques BPM (nuit en cours + historique).
- Écran de réglages (sensibilité de détection, gestion de la TV appairée).

## [0.5.0] — Automatisation & fiabilité runtime · *prévu*

Correspond à [ROADMAP.md](ROADMAP.md) Phase 6.

### Prévu — Added
- Service d'orchestration unique en foreground avec notification
  persistante.
- Canal de notification (`POST_NOTIFICATIONS` déjà déclaré, jamais utilisé
  jusque-là).

### Prévu — Changed
- `WorkManager` pour les reconnexions/resynchronisations différées.

## [0.4.0] — Brain · *prévu*

Correspond à [ROADMAP.md](ROADMAP.md) Phase 5.

### Prévu — Added
- Suite de tests unitaires exhaustive sur le moteur de scoring
  (couverture cible : >80% sur `brain/`).
- Calibration personnalisée du BPM de repos par utilisateur.

## [0.3.0] — TV · *prévu*

Correspond à [ROADMAP.md](ROADMAP.md) Phases 2-3 (stabilisation et refactor
interne, sans changement observable) puis Phase 4 (durcissement TV).

### Prévu — Changed (interne, Phases 2-3)
- Seuils de décision déplacés de `FoxCore` vers `SleepScoringConfig`.
- Suppression du code mort (`FoxModule`, et arbitrage
  `TvConnectionManager`/`TvCommandSender` vs `TvRemoteClient` — voir
  [ARCHITECTURE.md](ARCHITECTURE.md) §6).
- Introduction progressive de Hilt et fondation Room (voir
  [DECISIONS.md](DECISIONS.md) ADR-003, ADR-004).

### Prévu — Added (Phase 4)
- Reconnexion automatique de la TV principale.

### Prévu — Fixed (Phase 4)
- Arbitrage entre `TvKeyStore` et `TvIdentity` (une seule identité TLS
  active).

## [0.2.0] — Communication Wear · *prévu*

Correspond à [ROADMAP.md](ROADMAP.md) Phase 1.

### Prévu — Added
- Collecte BPM réelle côté Wear OS via Health Services.
- Détection de mouvement/immobilité côté montre.
- Boucle Watch → Phone → Brain validée avec de vraies données, sur une nuit
  de test complète.

## [0.1.0] — Prototype · *prévu (tag rétroactif à la clôture de la Phase 0)*

Correspond à l'état actuel du code, décrit dans `[Unreleased]` ci-dessus,
tagué une fois [ROADMAP.md](ROADMAP.md) Phase 0 (git, CI, tests) achevée.
