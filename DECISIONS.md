# FoxOFF — Registre des décisions d'architecture (ADR)

> Chaque décision structurante est enregistrée ici, qu'elle soit déjà
> adoptée ou seulement planifiée. Une décision n'est jamais supprimée du
> registre : si elle change, on ajoute un nouvel ADR qui **remplace** le
> précédent (voir convention en bas de page).

| # | Titre | Statut |
|---|---|---|
| [ADR-001](#adr-001--pourquoi-kotlin) | Pourquoi Kotlin | Adopté |
| [ADR-002](#adr-002--pourquoi-jetpack-compose) | Pourquoi Jetpack Compose | Adopté |
| [ADR-003](#adr-003--pourquoi-hilt) | Pourquoi Hilt | Proposé — Phase 3 |
| [ADR-004](#adr-004--pourquoi-room) | Pourquoi Room | Proposé — Phase 3 |
| [ADR-005](#adr-005--pourquoi-android-tv-remote-v2) | Pourquoi Android TV Remote v2 | Adopté |
| [ADR-006](#adr-006--pourquoi-protobuf) | Pourquoi Protocol Buffers | Adopté (outillage à corriger) |
| [ADR-007](#adr-007--pourquoi-wear-health-services) | Pourquoi Wear Health Services | Adopté (non implémenté) |
| [ADR-008](#adr-008--pourquoi-messageclient--data-layer-api) | Pourquoi MessageClient / Data Layer API | Adopté |
| [ADR-009](#adr-009--confiance-tls-tofu-sans-épinglage-post-pairing) | Confiance TLS TOFU sans épinglage post-pairing | Documenté a posteriori — à revoir Phase 4 |
| [ADR-010](#adr-010--priorisation-temporaire-dune-application-complète-avant-reprise-de-la-phase-1) | Priorisation temporaire d'une application complète avant reprise de la Phase 1 | Adopté |
| [ADR-011](#adr-011--abstraction-watchtransport-et-support-garmin-connect-iq) | Abstraction WatchTransport et support Garmin Connect IQ | Adopté (Android) — validation matérielle en attente |

---

## ADR-001 — Pourquoi Kotlin

**Contexte** — FoxOFF cible exclusivement Android (téléphone + Wear OS).
Java et Kotlin sont tous deux des premiers choix officiels sur cette
plateforme.

**Décision** — Kotlin pour l'intégralité du code applicatif (`:app` et
`:wear`). Le Java n'apparaît que dans du code protobuf généré/vendored
(`PoloProto.java`, `Remotemessage.java`), jamais dans du code écrit à la
main.

**Alternatives étudiées**
- *Java* — écarté : verbosité, absence de coroutines natives (essentielles
  pour orchestrer capteurs asynchrones, sockets TLS et Data Layer API sans
  callback hell), pas de `sealed class`/`data class` pour modéliser
  `FoxBrainEvent`/`SleepState` proprement.
- *Kotlin Multiplatform* — écarté pour la V2 : aucun besoin de partager de
  la logique hors Android à ce stade (pas de cible iOS/desktop/serveur).

**Conséquences**
- Coroutines (`kotlinx.coroutines`) comme mécanisme de concurrence par
  défaut dans tout le projet.
- `sealed class`/`data class` utilisés pour modéliser les événements
  (`FoxBrainEvent`, `SensorEvent`, `TvEvent`) — pattern matching exhaustif
  vérifié à la compilation.
- Interop Java nécessaire uniquement à la frontière avec le code protobuf
  généré.

---

## ADR-002 — Pourquoi Jetpack Compose

**Contexte** — L'UI doit couvrir un onboarding multi-écrans animé, un
dashboard temps réel réactif à des `StateFlow`, et une UI Wear OS distincte
(cadran rond, interactions montre).

**Décision** — Jetpack Compose (+ `androidx.wear.compose` côté montre) pour
toute l'UI, sur les deux modules.

**Alternatives étudiées**
- *Vues XML classiques* — écarté : plus verbeux pour l'animation
  d'onboarding (`AnimatedContentTransitionScope`) et pour la réactivité aux
  `StateFlow` du Dashboard, qui est un besoin central du produit (l'UI
  reflète en direct l'état du `FoxBrain`).
- *Vues XML pour Wear OS spécifiquement* — écarté : `androidx.wear.compose`
  est le chemin recommandé par Google pour Wear OS moderne, et partage les
  concepts (state hoisting, recomposition) avec le Compose téléphone —
  réduit la charge cognitive à maintenir deux paradigmes.

**Conséquences**
- Une seule compétence UI (Compose) à maintenir sur les deux modules.
- Navigation via `navigation-compose` côté téléphone.
- Dépendance à `composeBom` pour aligner les versions de tous les artefacts
  Compose.

---

## ADR-003 — Pourquoi Hilt

**Statut** — Fondation posée (2026-08-08), consommation différée. Hilt
2.60.1 + KSP sont dans `libs.versions.toml` et appliqués à `:app`,
`FoxApplication` porte `@HiltAndroidApp`. `FoxCore` reste pour l'instant un
`object` auto-suffisant, non injecté — la conséquence attendue ci-dessous
("FoxCore cesse d'être un object...") n'est PAS encore faite,
délibérément : elle touche le code activement testé nuit après nuit en ce
moment (voir ROADMAP.md Phase 3). Note d'implémentation : Hilt 2.57.1
(dernière version stable au moment de la recherche initiale) échoue avec
AGP 9 ("Android BaseExtension not found") — Hilt 2.59+ est la première
version à supporter AGP 9 (nécessite aussi Gradle 9.1+, déjà le cas ici
avec Gradle 9.5.0) ; 2.60.1 a été retenu.

**Contexte** — `FoxCore` est aujourd'hui un `object` singleton qui
instancie lui-même ses dépendances (`FoxTvEngine`, `RealTvController`,
`FoxBrain`), ce qui rend le test unitaire de la logique de décision
difficile (impossible d'injecter un `TvController` factice sans modifier
`FoxCore`).

**Décision** — Introduire Hilt, progressivement, pour l'injection de
dépendances dans `:app`, à partir de la Phase 3 de la Roadmap.

**Alternatives étudiées**
- *Koin* — service locator plus léger, mais résolution des dépendances à
  l'exécution plutôt qu'à la compilation : moins sûr pour un projet où les
  erreurs de configuration DI doivent être détectées avant le build release,
  pas en usage nocturne réel.
- *Injection manuelle (constructeurs + factories à la main)* — viable pour
  un petit projet, mais Hilt reste préférable ici car l'intégration
  `ViewModel`/`Application`/`Service` (annotations `@HiltViewModel`,
  `@AndroidEntryPoint` sur `PhoneWearListenerService`) est du code
  Android-spécifique répétitif que Hilt génère.
- *Continuer sans DI (statu quo)* — écarté : c'est la cause directe du
  caractère non testable de `FoxCore`, identifié comme dette technique
  prioritaire.

**Conséquences (attendues)**
- `FoxCore` cesse d'être un `object` qui construit ses dépendances ; elles
  sont injectées.
- Un seul module Hilt dans `:app` au départ (pas de multi-module Hilt tant
  que le projet reste mono-module — voir décisions repoussées de la
  Roadmap).
- Le module `:wear` n'a pas de besoin DI identifié pour l'instant (peu de
  classes, pas de graphe de dépendances complexe) — Hilt n'y est pas
  introduit avant qu'un besoin réel apparaisse.

---

## ADR-004 — Pourquoi Room

**Statut** — Fondation posée (2026-08-08), consommation différée à la
Phase 7. Room 2.8.4 + KSP dans `libs.versions.toml`, appliqué à `:app`.
Une seule entité pour l'instant (`SleepSessionEntity`/`SleepSessionDao`/
`AppDatabase`, package `data/local/`), fournie via Hilt
(`di/DatabaseModule.kt`) — pas les quatre domaines listés plus bas
("sessions de sommeil, historique BPM, TV appairée, préférences") : le
schéma complet attendra les vrais besoins de la Phase 7 plutôt que d'être
deviné à l'avance. `NightLog`/`SleepDetectionHistory` (SharedPreferences/
JSON) restent la source de vérité de l'onglet Historique, inchangés. Note
d'implémentation : `androidx.room:room-runtime-android` n'expose que le
builder basé sur `Context` (confirmé par décompilation de l'AAR) — le test
du DAO est un test instrumenté (`androidTest`), pas un test JVM pur ; non
exécuté faute d'appareil connecté pendant cette session.

**Contexte** — La Phase 7 de la Roadmap (historique de sommeil, graphiques
BPM) nécessite une source de vérité locale, structurée et interrogeable
(agrégations par nuit, min/max BPM).

**Décision** — Room comme couche de persistance locale.

**Alternatives étudiées**
- *`SharedPreferences` étendu* — écarté : adapté à des paires clé/valeur
  simples (déjà utilisé pour les identités TLS et les réglages TV), pas à
  des séries temporelles de sessions de sommeil interrogeables.
- *Fichiers plats (JSON/CSV)* — écarté : pas de requêtes, pas de migration
  de schéma, sérialisation/désérialisation manuelle à chaque lecture.
- *Base distante (backend cloud)* — hors périmètre : FoxOFF est
  volontairement local-first (voir [VISION.md](VISION.md), "sobre en
  données"), aucun besoin de synchronisation multi-appareils identifié pour
  la V2.

**Conséquences (attendues)**
- Nouvelle dépendance (`androidx.room`) + processeur d'annotations (KSP).
- Migrations de schéma à gérer dès la première release qui modifie une
  entité.
- Room s'intègre naturellement avec Hilt (ADR-003) pour l'injection du
  `RoomDatabase`/DAO dans les repositories de la couche `data`.

---

## ADR-005 — Pourquoi Android TV Remote v2

**Contexte** — FoxOFF doit envoyer une commande pause à la TV sans
intervention de l'utilisateur (qui dort). Il faut un protocole qui
fonctionne sur le réseau local, sans dépendre d'un service cloud tiers, et
compatible avec les TV Android/Google TV du marché.

**Décision** — Réimplémenter le protocole Android TV Remote v2 (le même
protocole que l'application officielle "Android TV Remote Control" de
Google et que le composant `androidtvremote2` d'Home Assistant) :
découverte réseau, pairing par certificat + PIN affiché sur la TV, puis
canal de contrôle TLS mutuellement authentifié.

**Alternatives étudiées**
- *HDMI-CEC* — écarté : dépend du câblage HDMI, du support CEC de chaque
  appareil intermédiaire (barre de son, ampli), notoirement peu fiable en
  pratique.
- *Google Cast (Chromecast)* — écarté : conçu pour lancer/contrôler une
  session de lecture cast depuis une app, pas pour piloter la TV
  elle-même comme une télécommande générique (play/pause du flux HDMI en
  cours, quelle que soit sa source).
- *IR (infrarouge)* — écarté : nécessite un émetteur IR dédié, absent des
  téléphones modernes.
- *Intégrations propriétaires par marque (Samsung, LG, Sony...)* — écarté
  pour la V2 : multiplie les protocoles à maintenir pour un gain de
  compatibilité qui ne concerne que les TV non-Android — hors cible produit
  (voir [VISION.md](VISION.md), utilisateurs visés).

**Conséquences**
- Cible exclusivement les TV Android TV / Google TV (cohérent avec le
  choix produit, pas une limitation technique subie).
- Nécessite de gérer soi-même la génération de certificats et le protocole
  TLS mutuel — complexité assumée, documentée dans
  [ARCHITECTURE.md](ARCHITECTURE.md) §6 et [RISKS.md](RISKS.md).
- Le protocole n'étant pas officiellement documenté par Google, la
  réimplémentation s'appuie sur des projets de référence open source
  (observable dans les noms de packages vendored : `com.google.polo.wire`).

---

## ADR-006 — Pourquoi Protocol Buffers

**Contexte** — Le protocole Android TV Remote v2 (ADR-005) échange des
messages binaires structurés (pairing, configuration, injection de touche)
dont le format est lui-même défini en Protocol Buffers par l'implémentation
de référence.

**Décision** — Utiliser Protocol Buffers (`protobuf-javalite`), avec les
schémas `polo.proto` (pairing) et `remotemessage.proto` (contrôle),
compilés en classes Java (`PoloProto`, `Remotemessage`).

**Alternatives étudiées**
- *Réimplémenter le format binaire à la main* (ce qui a été fait dans
  `TvConnectionManager.kt`, aujourd'hui orphelin — voir
  [ARCHITECTURE.md](ARCHITECTURE.md) §6) — écarté comme approche
  définitive : fragile, aucune garantie de compatibilité avec le format
  réel attendu par la TV, code illisible (offsets d'octets magiques).
- *JSON* — impossible : le protocole cible attend strictement du protobuf
  binaire, ce n'est pas un choix ouvert.

**Conséquences**
- Dépendance à des fichiers `.proto` comme source de vérité du format
  d'échange.
- **Point d'attention actif** : le plugin Gradle `com.google.protobuf` est
  déclaré au niveau racine mais **non appliqué sur `:app`** (cf.
  [ARCHITECTURE.md](ARCHITECTURE.md) §6) — les classes générées sont
  vendored à la main aujourd'hui. Cet ADR restera "Adopté (outillage à
  corriger)" tant que ce n'est pas aligné.

---

## ADR-007 — Pourquoi Wear Health Services

**Statut** — Dépendance adoptée (`androidx.health:health-services-client`
présente dans `:wear`), **implémentation non commencée** — c'est l'objet de
la Phase 1 de [ROADMAP.md](ROADMAP.md).

**Contexte** — FoxOFF a besoin d'un flux de fréquence cardiaque continu et
économe en énergie depuis la montre, sur des versions Wear OS récentes
(Wear OS 5/6, cf. `wear/MainActivity.kt` qui distingue déjà les permissions
`health.READ_HEART_RATE` API 36+ des anciennes `BODY_SENSORS`).

**Décision** — Utiliser Health Services (`PassiveMonitoringClient` /
`MeasureClient`) comme API de capture, plutôt que l'API `SensorManager`
générique.

**Alternatives étudiées**
- *`SensorManager` + `Sensor.TYPE_HEART_RATE` direct* — reste en fallback
  documenté pour compatibilité, mais écarté comme choix principal : Health
  Services est l'API recommandée par Google sur Wear OS pour l'accès
  capteur en arrière-plan avec gestion d'énergie optimisée (moins de
  réveils CPU qu'un listener `SensorManager` classique).
- *Health Connect (API de partage de données santé inter-apps)* — écarté :
  conçu pour l'agrégation de données de santé entre applications, pas pour
  la lecture temps réel d'un flux BPM continu pendant une session active.

**Conséquences (attendues)**
- Gestion de deux jeux de permissions selon la version d'API (déjà anticipé
  dans le code du module `:wear`).
- Nécessite un service en avant-plan côté montre pour une collecte fiable
  toute la nuit (cf. Phase 1 et Phase 6 de la Roadmap).

---

## ADR-008 — Pourquoi MessageClient / Data Layer API

**Contexte** — La montre (mesure) et le téléphone (décision + action)
doivent échanger de petits messages fréquents (BPM, infos montre) alors que
les deux appareils ne sont pas nécessairement dans le même réseau local
direct (Bluetooth, Wi-Fi relayé selon la configuration Wear OS).

**Décision** — Utiliser la Data Layer API de Google Play Services
(`Wearable.getMessageClient`, `Wearable.getNodeClient`), avec des chemins
applicatifs dédiés (`/foxoff/hr`, `/foxoff/watch_info`) plutôt que
`DataClient` (synchronisation d'objets) ou une connexion réseau directe.

**Alternatives étudiées**
- *`DataClient` (DataItems synchronisés)* — écarté pour le flux BPM : pensé
  pour de la synchronisation d'état persistant à faible fréquence, pas pour
  un flux temps réel à cadence régulière.
- *Socket réseau direct montre↔téléphone* — écarté : suppose que les deux
  appareils soient sur le même réseau Wi-Fi, ce que la Data Layer API
  abstrait déjà correctement (elle route via Bluetooth ou Wi-Fi selon
  disponibilité, de façon transparente).
- *Firebase Cloud Messaging ou autre relais cloud* — écarté : introduit une
  dépendance réseau externe et une latence pour une communication qui doit
  rester locale par principe (cf. [VISION.md](VISION.md)).

**Conséquences**
- Couplage à l'écosystème Google Play Services des deux côtés (déjà le cas
  via `play-services-wearable`).
- `PhoneWearListenerService` doit être un service capable de réveiller le
  processus à la réception d'un message — actuellement pas en foreground,
  ce qui limite sa fiabilité en arrière-plan (cf. dette technique de
  [ROADMAP.md](ROADMAP.md), traité Phase 1/6).

---

## ADR-009 — Confiance TLS "Trust On First Use" sans épinglage post-pairing

**Statut** — Décision implicite déjà présente dans le code
(`tv/pairing/TvIdentity.kt`), documentée ici a posteriori pour la rendre
visible et traçable. À revoir explicitement en Phase 4 de
[ROADMAP.md](ROADMAP.md).

**Contexte** — Le protocole Android TV Remote v2 (ADR-005) authentifie le
*client* (le téléphone) auprès de la TV via un certificat + vérification de
PIN pendant le pairing. Mais le `TrustManager` utilisé pour établir la
connexion TLS (aussi bien pendant le pairing que pendant les reconnexions
ultérieures via `TvRemoteClient`) accepte **tout** certificat présenté par
la TV, sans vérification (`checkServerTrusted` ne fait rien). Le code
documente lui-même cette limite comme temporaire.

**Décision (de fait)** — Confiance "Trust On First Use" côté serveur : le
téléphone ne vérifie pas l'identité de la TV au niveau TLS ; l'authenticité
de l'échange repose entièrement sur le secret dérivé du PIN pendant le
pairing initial (`TvPairingManager.verifyPin`).

**Alternatives étudiées** (au moment de cette documentation a posteriori,
à évaluer réellement en Phase 4)
- *Épinglage du certificat serveur après le premier pairing réussi*
  (mémoriser l'empreinte SHA-256 vue pendant le pairing, la comparer à
  chaque reconnexion) — c'est l'intention documentée dans le commentaire du
  code (`TvIdentity.kt`) mais **non implémentée**.
- *Validation par chaîne de certification classique* — inapplicable : la TV
  utilise un certificat auto-signé par design du protocole (ADR-005), il
  n'existe pas d'autorité de certification à vérifier.

**Conséquences**
- Fenêtre d'attaque théorique : un attaquant sur le même réseau local
  pourrait usurper l'adresse IP de la TV entre deux sessions et intercepter
  une commande pause (impact limité : FoxOFF n'envoie que play/pause, aucune
  donnée sensible ne transite sur ce canal après le pairing).
- Documenté comme risque explicite dans [RISKS.md](RISKS.md) ("Certificats
  TLS") plutôt que laissé comme angle mort.
- Ne bloque pas la Phase 4 (durcissement du TV Engine), mais l'épinglage
  d'empreinte post-pairing doit être évalué à ce moment-là.

---

## ADR-010 — Priorisation temporaire d'une application complète avant reprise de la Phase 1

**Statut** — Adopté, 2026-08-06.

**Contexte** — Les Principes directeurs 1 et 2 de [ROADMAP.md](ROADMAP.md)
imposaient de valider la boucle réelle Watch → Phone → Brain (Phase 1)
avant toute autre priorité. Une fois le bug de boucle infinie corrigé dans
`FoxBrain.kt` (ajout du cas `SleepDetected -> tvIsPaused = true`), il
restait à reconfirmer la condition `ASLEEP` en conditions réelles (BPM au
repos) puis un test de stabilité de 30 minutes — étape qui dépend
entièrement de la disponibilité physique de l'utilisateur avec la montre au
poignet, et ne peut pas être avancée sans lui. Pendant ce temps, un audit
complet de l'application (navigation, écrans, réglages, pairing TV,
connexion montre) a révélé plusieurs lacunes concrètes qui empêchent l'app
d'être utilisable de bout en bout par un utilisateur réel : écran Réglages
absent, onboarding qui se relance à chaque lancement, format de PIN
d'appairage incohérent entre deux écrans.

**Décision** — Mettre la Phase 1 en pause documentée (le correctif
`FoxBrain.kt` est conservé, rien n'est perdu ni annulé, seul le critère de
sortie matériel reste à reconfirmer) et ouvrir un chantier prioritaire
temporaire, transverse à plusieurs phases futures (1 partiellement, 4, 6,
7), visant une application complète et cohérente pour un utilisateur réel.
La Phase 1 reprend formellement dès que ce chantier est terminé.

**Alternatives étudiées**
- *Attendre la disponibilité de l'utilisateur avec la montre avant de
  continuer* — écarté : bloquerait toute progression sur des lacunes UI
  déjà identifiées et totalement indépendantes du test matériel restant.
- *Sauter directement à la Phase 3 (refactor architectural)* — écarté :
  contredirait le Principe directeur 2 ("stabiliser puis seulement
  refactorer") plus profondément que cette dérogation ; refactorer
  l'architecture sur une base encore incomplète fonctionnellement serait un
  refactor à l'aveugle sur des écrans qui vont encore changer.
- *Renuméroter les phases pour intégrer ce chantier proprement* — écarté
  pour l'instant : la portée exacte des lacunes à combler n'était pas
  connue avant l'audit ; une renumérotation prématurée risquerait de devoir
  être refaite. Le chantier reste donc volontairement hors séquence
  numérotée, documenté comme exception explicite plutôt que dissimulé.

**Conséquences**
- La Phase 1 n'est ni annulée ni invalidée : son critère de sortie
  spécifique et son DoD complet restent dus, seulement reportés.
- Le chantier prioritaire suit un DoD allégé (pas de couverture de tests
  exhaustive exigée) — assumé explicitement pour ne pas ralentir la
  correction de bugs UI/UX bloquants pendant que le socle Brain n'est pas
  encore dans sa forme finale (Phase 5).
- Toute modification pendant ce chantier doit préserver le correctif
  `FoxBrain.kt` (`SleepDetected -> tvIsPaused = true`) et ne doit pas
  réintroduire la boucle infinie qu'il corrige.

---

## ADR-011 — Abstraction WatchTransport et support Garmin Connect IQ

**Statut** — Adopté côté Android, 2026-08-07. Validation matérielle en
attente (l'utilisateur n'a pas encore de montre Garmin).

**Contexte** — L'utilisateur a exprimé l'objectif de connecter FoxOFF à
n'importe quelle marque de montre (voir mémoire de session), en conflit
avec ADR-001 ("FoxOFF cible exclusivement Android"). Après avoir écarté un
premier chantier Apple Watch/iOS (bloqué : Xcode ne tourne que sur macOS,
aucun Mac disponible), l'utilisateur a choisi de commencer par Garmin — le
SDK Connect IQ tourne nativement sur Windows, contrairement à Xcode,
rendant le développement réellement possible dans cet environnement.

Toute la logique de présence/reconnexion (`WatchPresenceCoordinator`,
`FoxCore.discoverWatch()`) appelait directement l'API GMS Wearable (Data
Layer), spécifique à Wear OS. `PhoneWearListenerService` (point d'entrée
GMS `BIND_LISTENER`) publiait déjà des `SensorEvent` agnostiques de la
marque — la frontière brand-agnostique existait donc déjà en aval, mais pas
en amont.

**Décision** — Introduire une interface `WatchTransport`
(`connectedDevices()`, `requestInfo(deviceId)`) dans un nouveau package
`core/watch/`, avec deux implémentations :
- `WearOsTransport` : extraction directe des appels GMS existants, **zéro
  changement de comportement** (build + suite de tests complète vérifiés
  sans régression).
- `GarminTransport` : basé sur le Connect IQ Mobile SDK
  (`com.garmin.connectiq:ciq-companion-app-sdk`, Maven Central, gratuit —
  **pas** le "Companion SDK"/Health API, celui-ci commercial et écarté).
  Signatures d'API vérifiées par décompilation du AAR réel (`javap`), pas
  devinées.

`WatchSettings` mémorise la marque active (`WatchBrand`, `WEAR_OS` par
défaut — rétrocompatible avec les installations existantes).
`FoxCore.initialize()` choisit le transport en fonction de ce réglage. Un
nouvel écran "Changer de montre" dans Réglages permet de rebasculer entre
marques (efface l'appareil connu, redémarre l'app pour appliquer le
changement — même pattern que `resetOnboarding()`).

Côté montre, un projet Connect IQ séparé (`garmin/`, hors build Gradle —
toolchain `monkeyc` distincte) implémente la lecture BPM
(`Activity.getActivityInfo().currentHeartRate`, pas de session capteur à
gérer) et l'envoi périodique en arrière-plan via
`Background.ServiceDelegate.onTemporalEvent()` (5 minutes — le minimum
autorisé par l'API Garmin, même granularité que le mode passif Wear OS déjà
en place). Compilé et vérifié en typecheck strict contre 3 appareils réels
(vivoactive4s/5/6), pas seulement écrit.

**Alternatives étudiées**
- *Companion SDK / Health API Garmin* — écarté : accès commercial,
  nécessite un accord partenaire avec Garmin, incompatible avec un projet
  personnel.
- *Reporter tout le chantier multi-marques à plus tard* — écarté pour la
  partie Garmin spécifiquement : contrairement à iOS, aucun blocage
  matériel/logiciel ne l'empêchait (SDK gratuit, Windows, pas de Mac
  requis) ; seule l'absence de montre physique limite la validation finale.
- *Étendre `WatchPresenceEngine`/`WatchReconnectionEngine` directement* —
  écarté : ces classes sont déjà pures et agnostiques (elles ne
  connaissent que des lambdas d'effets), aucune modification n'y était
  nécessaire — seul le câblage GMS en amont (`WatchPresenceCoordinator`,
  `FoxCore.discoverWatch()`) devait être abstrait.

**Conséquences**
- ADR-001 est partiellement superseded pour le téléphone/montre (pas pour
  la TV, voir ADR-005 — Android TV Remote v2 reste la seule cible TV) :
  FoxOFF vise désormais plusieurs marques de montre sur téléphone Android,
  pas plusieurs plateformes téléphone (iOS reste en pause, voir mémoire de
  session).
- Le format exact du message BPM reçu côté Android depuis Garmin
  (`List<Object>` — Dictionary Monkey C en position 0) est une hypothèse
  documentée dans `GarminTransport.kt`, **non vérifiée sur matériel réel**
  — à confirmer/ajuster à la première nuit de test avec une vraie montre.
- Nouvelle dépendance `com.garmin.connectiq:ciq-companion-app-sdk` sur
  `:app`, et nouveau dossier `garmin/` à la racine du dépôt, hors du build
  Gradle (toolchain Connect IQ distincte — SDK Manager + `monkeyc`/`monkeydo`
  en CLI, installés localement).

---

## Convention de gestion des ADR

- Un ADR n'est **jamais modifié rétroactivement** dans son contenu de
  décision une fois adopté — s'il devient obsolète, on écrit un nouvel ADR
  qui le remplace et on met à jour son statut ici en
  "Remplacé par ADR-0XX".
- Statuts possibles : **Adopté**, **Proposé** (planifié, non implémenté),
  **Documenté a posteriori** (décision déjà présente dans le code avant
  d'être formalisée), **Remplacé**.
- Toute décision qui change le choix d'une brique déjà cochée dans
  [ROADMAP.md](ROADMAP.md) doit être reflétée dans les deux documents.
