# FoxOFF

FoxOFF détecte l'endormissement de l'utilisateur devant la TV — via une
montre Wear OS (fréquence cardiaque + mouvement) — et coupe automatiquement
la TV (protocole Android TV Remote v2), sans intervention manuelle.

> Vision produit complète, philosophie et périmètre : [VISION.md](VISION.md).

## État du projet

FoxOFF V2 suit une feuille de route par phases, chacune produisant une
application compilable et fonctionnelle avant de passer à la suivante.
**État actuel et prochaines étapes : [ROADMAP.md](ROADMAP.md).**

## Documents de référence

| Document | Contenu |
|---|---|
| [VISION.md](VISION.md) | Produit, utilisateurs, philosophie, principe fondamental |
| [ROADMAP.md](ROADMAP.md) | Feuille de route par phases, Definition of Done, dépendances |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, packages, couches, diagrammes, conventions |
| [DECISIONS.md](DECISIONS.md) | Registre des décisions d'architecture (ADR) |
| [CHANGELOG.md](CHANGELOG.md) | Historique et versions prévues |
| [RISKS.md](RISKS.md) | Registre des risques techniques |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Conventions Kotlin/Git, branches, revue, DoD |
| [AUDIT.md](AUDIT.md) | État de santé du projet, améliorations prioritaires |

## Modules

```
FoxOFFV2
├── :app   — application téléphone (UI, orchestration, TV Engine, Fox Brain)
└── :wear  — application Wear OS compagnon (capteurs, communication vers le téléphone)
```

Détail des packages et des couches : [ARCHITECTURE.md](ARCHITECTURE.md).

## Prise en main

- JDK 11 requis pour la compilation (voir `compileOptions` des modules).
  Sous Windows, le JBR d'Android Studio convient
  (`<install Android Studio>\jbr`) — définir `JAVA_HOME` en conséquence si
  aucun JDK n'est autrement disponible sur le `PATH`.
- Android SDK requis : créer un fichier `local.properties` à la racine
  (non versionné) contenant `sdk.dir=<chemin vers le SDK Android>`.
- `compileSdk`/`targetSdk` 37, `minSdk` 30.

### Commandes de build

```bash
# Build complet (debug + release) des deux modules
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease

# Tests unitaires
./gradlew.bat test

# Lint
./gradlew.bat lint

# Nettoyage
./gradlew.bat clean

# Un seul module
./gradlew.bat :app:assembleDebug
./gradlew.bat :wear:assembleDebug
```

## Contribuer

Conventions de code, de commits, de branches et Definition of Done :
[CONTRIBUTING.md](CONTRIBUTING.md).
