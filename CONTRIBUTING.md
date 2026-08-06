# Contribuer à FoxOFF

> Écrit pour un développeur solo aujourd'hui, pensé pour qu'un futur
> contributeur puisse rejoindre le projet sans reconstituer les règles à la
> main. Les règles ci-dessous s'appliquent **dès maintenant**, pas
> seulement à partir du moment où une deuxième personne rejoint le projet —
> c'est ce qui les rend crédibles.

## 1. Avant de coder

- Lire [VISION.md](VISION.md) (pourquoi le projet existe, ce qui est hors
  périmètre) et le principe fondamental : **la fiabilité passe toujours
  avant les nouvelles fonctionnalités**.
- Vérifier où on en est dans [ROADMAP.md](ROADMAP.md) — ne pas démarrer une
  phase dont les dépendances (matrice de dépendances) ne sont pas closes.
- Toute décision structurante (nouvelle dépendance, changement de
  protocole, choix d'architecture) donne lieu à un ADR dans
  [DECISIONS.md](DECISIONS.md), même bref — voir le format des ADR
  existants.

## 2. Conventions Kotlin

Le style de base est le [Kotlin coding style officiel](https://kotlinlang.org/docs/coding-conventions.html)
(indentation 4 espaces, `PascalCase` pour les classes, `camelCase` pour
fonctions/propriétés, `UPPER_SNAKE_CASE` pour les constantes de compagnon).
En plus de ça, les conventions **spécifiques à FoxOFF**, déjà en usage dans
le code et documentées en détail dans
[ARCHITECTURE.md §7](ARCHITECTURE.md#7-conventions-actuelles), s'appliquent :

- Préfixe `Fox` réservé aux composants transverses du cœur applicatif
  (`FoxCore`, `FoxBrain`, `FoxLogger`...) — ne pas l'utiliser pour un
  composant propre à un sous-système.
- Suffixes porteurs de sens : `Manager` (cycle de vie + I/O), `Engine`
  (composition root d'un sous-système), `Repository` (état observable en
  `StateFlow`), `Controller` (façade d'action).
- Un fichier = une classe/objet public. (Une exception historique existe
  dans le code actuel — voir [ARCHITECTURE.md §6](ARCHITECTURE.md#6-écarts-constatés-entre-le-code-et-son-intention) —
  elle ne doit pas être reproduite.)
- Tout log passe par `FoxLogger`, préfixé par zone (`FOX-TV`, `FOX-CORE`,
  `FOX-WATCH`, `FOX-PHONE`...). Ne pas utiliser `Log.*` ou `println`
  directement.
- `StateFlow` exposé en lecture seule (`val x: StateFlow<T> = _x.asStateFlow()`),
  jamais un `MutableStateFlow` public.
- Pas de `GlobalScope` : toute coroutine est lancée dans un scope structuré
  et annulable (`viewModelScope`, un `CoroutineScope(SupervisorJob() + ...)`
  possédé par une classe avec cycle de vie clair).
- Éviter `!!` — préférer `requireNotNull`/`checkNotNull` avec message
  explicite, ou une gestion explicite du cas null.
- Modéliser les états et événements finis avec `sealed class`/`sealed
  interface` (déjà le cas pour `FoxBrainEvent`, `SleepState`) plutôt
  qu'avec des booléens ou des chaînes de caractères.

À partir de la Phase 3 de la Roadmap (introduction du domain/data/presentation,
voir [ARCHITECTURE.md §4.2](ARCHITECTURE.md#42-cible-phase-3-de-la-roadmap--domain--data--presentation)) :
le code du package `domain` ne doit importer aucune classe `android.*`.
C'est une règle de revue, pas seulement une convention de style.

## 3. Structure des packages

Voir [ARCHITECTURE.md §3](ARCHITECTURE.md#3-packages--état-actuel-app)
pour l'arborescence complète et à jour. Règle pratique pour savoir où
ranger du nouveau code aujourd'hui (organisation par feature, pas encore
par couche) :

| Je code... | Ça va dans... |
|---|---|
| une règle de scoring, un état de sommeil | `brain/` |
| la découverte/pairing/contrôle TV | `tv/` (et ses sous-packages `pairing/`, `protobuf/`, `remote/`) |
| la réception/émission de messages montre↔téléphone | `core/service/`, `sensors/` |
| un écran ou composant Compose | `ui/<feature>/` |
| une classe transverse au cœur applicatif | `core/` |

Ne rien ajouter dans `tvlab/` qui soit un chemin de code de production — ce
package reste un outil de diagnostic manuel, isolé (règle formalisée en
[ARCHITECTURE.md §8](ARCHITECTURE.md#8-règles-de-dépendances-cible-à-partir-de-la-phase-3)).

## 4. Stratégie de branches

- `main` est la branche de référence, toujours dans un état qui compile et
  passe la CI (une fois [ROADMAP.md](ROADMAP.md) Phase 0 faite).
- Une branche par unité de travail, nommée `<type>/<sujet-court>` :
  - `phase/0-socle`, `phase/1-boucle-watch-phone` — pour le travail qui
    correspond directement à une phase de la Roadmap.
  - `feature/<sujet>`, `fix/<sujet>`, `chore/<sujet>` — pour tout le reste.
- Pas de branches longues vivantes en parallèle de `main` (pas de
  `develop`) — le projet est trop petit pour justifier un modèle Git Flow
  complet ; un modèle trunk-based avec branches courtes suffit.
- **Solo aujourd'hui** : commit direct sur `main` toléré, mais toujours en
  petits commits atomiques, jamais un commit qui casse la CI.
- **Dès qu'un deuxième développeur rejoint** : plus aucun commit direct sur
  `main`, tout passe par une Pull Request (voir §6).

## 5. Format des commits

[Conventional Commits](https://www.conventionalcommits.org/fr/) :

```
<type>(<scope>): <description au présent, en français ou anglais, cohérent avec le reste de l'historique>
```

Types utilisés dans ce projet : `feat`, `fix`, `refactor`, `docs`, `test`,
`chore`, `perf`. Scope = le sous-système concerné (`brain`, `tv`, `wear`,
`core`, `ui`, `docs`...).

Exemples cohérents avec le projet :

```
feat(wear): collecte BPM via Health Services PassiveMonitoringClient
fix(tv): reconnexion automatique après changement d'IP de la TV
refactor(core): extraction de SleepAutomationUseCase hors de FoxCore
docs(decisions): ajout ADR-010 sur le choix de WorkManager
test(brain): couverture WeightedSleepAnalyzer pour les seuils de calibration
```

Un commit qui clôt une phase de la Roadmap doit le mentionner explicitement
dans son corps de message (ex. `Closes ROADMAP Phase 1`), pour garder une
trace même sans dépendre d'un outil de suivi externe.

## 6. Règles de revue

**Aujourd'hui (solo)** — auto-revue obligatoire avant tout merge sur
`main` : relire le diff en entier (pas juste les fichiers qu'on pense avoir
modifiés), dérouler la checklist du Definition of Done (§7) consciemment,
pas de mémoire.

**Dès qu'un deuxième développeur rejoint** :
- Toute Pull Request nécessite **une approbation** avant merge.
- Personne ne merge sa propre PR.
- La CI doit être verte — aucune exception, y compris pour un correctif
  "évident".
- Une PR qui touche à `brain/` (le cœur métier) ou à l'identité
  TLS/pairing (`tv/pairing/`, `tv/TvKeyStore.kt`) nécessite une revue
  particulièrement attentive : ce sont les deux zones où un bug est le
  plus coûteux (mauvaise décision de sommeil silencieuse / rupture de
  compatibilité TV).

## 7. Definition of Done

FoxOFF applique la **même** Definition of Done à deux granularités :

- **Par commit/PR** (ce document) : le changement compile, les tests
  concernés passent, la documentation directement impactée est à jour.
- **Par phase de Roadmap** (voir
  [ROADMAP.md — Definition of Done](ROADMAP.md#definition-of-done-dod--obligatoire-pour-toute-phase)) :
  la version complète, plus lourde, qui inclut le build release et un test
  manuel du parcours principal sur appareil réel.

Une PR individuelle n'a pas à satisfaire la DoD de phase complète à elle
seule (ce serait souvent disproportionné pour un petit changement) — mais
aucune phase n'est déclarée close tant que sa DoD complète n'est pas
vérifiée.

## 8. Documentation à jour

Un changement qui modifie l'un des éléments suivants **doit** mettre à jour
le document correspondant dans le même commit/PR — ce n'est pas une tâche
à part :

| Si le changement touche... | Mettre à jour... |
|---|---|
| une dépendance, un choix technique structurant | [DECISIONS.md](DECISIONS.md) (nouvel ADR) |
| l'organisation des packages/couches | [ARCHITECTURE.md](ARCHITECTURE.md) |
| le périmètre produit | [VISION.md](VISION.md) |
| une version livrée/prête | [CHANGELOG.md](CHANGELOG.md) |
| un nouveau risque identifié ou mitigé | [RISKS.md](RISKS.md) |
| l'avancement d'une phase | [ROADMAP.md](ROADMAP.md) |

## 9. Prise en main technique

- JDK 11 (voir `compileOptions` des modules `:app`/`:wear`), `compileSdk`/
  `targetSdk` 37, `minSdk` 30.
- Build : `./gradlew build` (ou `gradlew.bat build` sous Windows sans Git
  Bash).
- Aucun secret/clé d'API n'est requis pour builder aujourd'hui — les
  identités TLS TV sont générées localement à la première exécution.
- Deux modules : `:app` (téléphone) et `:wear` (montre) — voir
  [ARCHITECTURE.md §2](ARCHITECTURE.md#2-modules-gradle-état-actuel).
