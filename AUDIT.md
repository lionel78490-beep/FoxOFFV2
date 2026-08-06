# FoxOFF — État de santé du projet

**Date de l'audit** : 2026-08-06
**Portée** : lecture complète du code source `:app` et `:wear`, croisée avec
[VISION.md](VISION.md), [ARCHITECTURE.md](ARCHITECTURE.md),
[DECISIONS.md](DECISIONS.md), [RISKS.md](RISKS.md),
[CONTRIBUTING.md](CONTRIBUTING.md) et [ROADMAP.md](ROADMAP.md).
**Aucun code, build.gradle ou manifest n'a été modifié pour produire cet
audit.**

---

## 1. Cohérence de ROADMAP.md — incohérences détectées, **corrigées après validation**

En documentant l'architecture réelle (§6 d'[ARCHITECTURE.md](ARCHITECTURE.md)),
l'audit a mis au jour des faits que [ROADMAP.md](ROADMAP.md) ne reflétait pas
encore correctement. **Statut : les trois points ci-dessous ont été validés
le 2026-08-06 et appliqués dans ROADMAP.md v3** (Phase 4 reformulée, dette
technique complétée). Conservé ici comme trace de la décision.

### 1.1 — Le constat "canal de contrôle TV encodé à la main" (Phase 4) est partiellement obsolète

**Ce que dit ROADMAP.md aujourd'hui** (Phase 4 et tableau de dette
technique) : `tv/TvConnectionManager.kt` serait le canal de contrôle réel,
à migrer vers le protobuf généré.

**Ce que l'audit a trouvé** : le chemin réellement actif en production est
`FoxTvEngine.pause()` → `FoxTvController.togglePlayPause()` →
`tv/remote/TvRemoteClient.kt`, qui **utilise déjà correctement** les
classes protobuf générées (`Remotemessage`). `TvConnectionManager` et son
enveloppe `TvCommandSender` ne sont référencés par **aucun** point d'entrée
actif — ce sont des orphelins d'une itération précédente, pas le code
exécuté.

**Impact sur la Roadmap** : la tâche de Phase 4 "migrer
`TvConnectionManager` vers `remotemessage.proto`" repose sur une prémisse
fausse. La vraie tâche est : (a) supprimer le code mort
(`TvConnectionManager`, `TvCommandSender`), (b) concentrer le durcissement
de la Phase 4 sur `TvRemoteClient` (déjà correct, mais à rendre robuste :
reconnexion, gestion d'erreurs).

**Action suggérée (à valider)** : reformuler le premier point de la Phase 4
dans ROADMAP.md.

### 1.2 — Une deuxième duplication non documentée : identité TLS

**Constat nouveau, absent de ROADMAP.md** : `tv/TvKeyStore.kt` (ancien) et
`tv/pairing/TvIdentity.kt` (nouveau) génèrent chacun une identité TLS
RSA 2048 séparée, stockée dans deux `SharedPreferences` différentes.
`FoxTvEngine` garde `TvKeyStore` "temporairement... pour conserver la
compatibilité" (commentaire du code lui-même) alors que le pairing et le
contrôle actifs utilisent `TvIdentity`.

**Impact sur la Roadmap** : la Phase 4 mentionne "vérifier que `TvKeyStore`
stocke les clés dans l'Android Keystore" — ce n'est pas le bon composant à
cibler en priorité, puisque `TvIdentity` est la version active. Voir aussi
[DECISIONS.md](DECISIONS.md) ADR-009 sur l'absence d'épinglage de
certificat, également absente de ROADMAP.md.

**Action suggérée (à valider)** : ajouter explicitement à la Phase 4 (a)
l'arbitrage `TvKeyStore` vs `TvIdentity`, (b) la migration vers Android
Keystore, (c) l'épinglage d'empreinte post-pairing.

### 1.3 — Dérive possible du schéma protobuf, non mentionnée

**Constat nouveau** : le plugin Gradle `com.google.protobuf` est déclaré au
niveau racine (`apply false`) mais n'est appliqué sur **aucun** module. Les
classes générées (`PoloProto.java`, `Remotemessage.java`) sont vendored à
la main dans `src/main/java`, pas régénérées par le build. ROADMAP.md ne
mentionne ce point nulle part alors qu'il conditionne toute évolution
future du protocole TV.

**Action suggérée (à valider)** : ajouter ce point à la Phase 3 (fondation)
ou 4 (TV) de ROADMAP.md.

### 1.4 — Le reste de ROADMAP.md est cohérent

Vérifié sans réserve avec les nouveaux documents :
- [VISION.md](VISION.md) (une seule TV, pas de ML avant historique, sobriété
  de données) s'aligne avec les décisions repoussées de la Roadmap.
- [DECISIONS.md](DECISIONS.md) ADR-003/004 (Hilt, Room = "Proposé, Phase 3")
  correspond exactement au contenu de la Phase 3.
- [RISKS.md](RISKS.md) ne contredit aucune priorité de phase ; il vient
  plutôt combler un angle mort (aucun registre de risques n'existait avant).
- [CHANGELOG.md](CHANGELOG.md) reprend le séquencement des phases sans le
  modifier — la correspondance version ↔ phase que j'ai proposée
  (`0.3.0 TV` regroupe les Phases 2-3-4) est une interprétation à valider
  avec toi, pas une vérité déjà actée par ROADMAP.md.

**Les trois points (1.1, 1.2, 1.3) sont appliqués dans ROADMAP.md v3** :
Phase 4 ne mentionne plus de migration de `TvConnectionManager` mais son
retrait progressif, la coexistence `TvKeyStore`/`TvIdentity` et l'absence
du plugin Gradle Protobuf sont désormais explicites dans la Phase 4 et la
table de dette technique.

---

## 2. Notes de santé (/10)

| Dimension | Note | Justification courte |
|---|---|---|
| Architecture | **4/10** | Séparation par feature cohérente (`brain/`, `tv/`, `sensors/`) et pattern Strategy déjà en place (`FoxBrainAnalyzer`), mais `FoxCore` est un God Object (bus + état + orchestration + règles), aucune injection de dépendances, deux implémentations parallèles coexistent côté TV (§1.1, §1.2). |
| Lisibilité | **6/10** | Conventions de nommage cohérentes et intentionnelles (`Fox*`, suffixes `Manager`/`Engine`/`Repository`), logs préfixés par zone. Contrepoids : style d'écriture incohérent d'un fichier à l'autre (ex. `TvPairingManager` en un argument par ligne), un nom de fichier non conventionnel, logs de diagnostic très verbeux laissés dans `TvKeyStore`. |
| Maintenabilité | **4/10** | Code mort non retiré (`TvConnectionManager`/`TvCommandSender`, `RemoteFactory` vide), duplication d'identité TLS, zéro test pour sécuriser un futur changement, pas de CI pour détecter une régression avant qu'elle n'atteigne `main`. |
| Évolutivité | **5/10** | La séparation par feature et le pattern Strategy du Brain donnent une base correcte pour ajouter des fonctionnalités. Le couplage fort au singleton `FoxCore` et l'absence de DI freinent l'ajout de nouveaux sous-systèmes sans y toucher directement. |
| Dette technique | **3/10** | Cumul concret : pas de git/CI/tests, God Object, seuils codés en dur, module `FoxModule` mort, deux implémentations TV orphelines, deux identités TLS, fonction de debug temporaire (`exportCertificate`) laissée en place, schéma protobuf vendored non régénéré automatiquement. |
| Testabilité | **2/10** | Zéro test unitaire sur la logique métier la plus critique (`brain/`). `FoxCore` (objet singleton qui construit lui-même ses dépendances) et le code réseau TLS/sockets ne sont pas conçus pour être testés sans matériel réel. Aucun outillage de test (MockK, Turbine) configuré. |
| Sécurité | **4/10** | Clés privées TLS stockées en Base64 clair dans `SharedPreferences` (ni Keystore, ni chiffrement au repos), confiance TLS TOFU sans épinglage post-pairing, export de certificat vers le stockage externe en fonction "temporaire" encore présente. Impact réel atténué : canal local uniquement, commande play/pause seule, pas de donnée personnelle transmise. |
| Publication Play Store | **2/10** | Aucune politique de confidentialité, aucun formulaire Data Safety, optimisation R8 désactivée en release (`:app`), aucune stratégie de signing visible, aucun monitoring crash. Les données BPM relèvent de la catégorie santé — le travail de conformité n'a pas commencé. |

**Note globale non pondérée : 3,75/10.** Cohérent avec un prototype
fonctionnel construit pour valider des idées rapidement (ce qu'il a bien
fait — le pairing TV et le moteur de scoring sont de vraies briques
solides), mais pas encore un produit gouverné. C'est précisément ce que
[ROADMAP.md](ROADMAP.md) est censé corriger, phase par phase.

---

## 3. Top 10 des améliorations prioritaires

Classées par urgence réelle, pas par facilité :

1. **Git + CI** (Phase 0) — rien n'est aujourd'hui protégé contre une
   régression silencieuse ou une perte de travail.
2. **Faire fonctionner réellement la boucle Watch → Phone → Brain**
   (Phase 1) — le produit ne fait pas encore ce pour quoi il existe ; le
   récepteur téléphone attend des messages que la montre n'envoie jamais.
3. **Trancher et nettoyer la duplication TV/TLS découverte dans cet audit**
   (§1.1, §1.2) — avant que la Phase 3/4 ne construise sur la mauvaise
   base par erreur.
4. **Tests unitaires sur le Fox Brain** (Phase 5, mais à ne pas repousser
   plus que nécessaire) — c'est la logique métier la plus critique et,
   paradoxalement, la plus simple à tester (pure, sans Android).
5. **Migrer les clés privées TLS vers l'Android Keystore + épinglage
   post-pairing** (Phase 4) — le seul vrai risque sécurité identifié avec
   un chemin de correction clair.
6. **Foreground service pour la boucle de surveillance** (Phase 6,
   amorcé minimalement en Phase 1) — sans lui, la fiabilité nocturne reste
   un vœu pieux face à Doze et aux surcouches constructeur.
7. **Réactiver la génération protobuf via le plugin Gradle** (Phase 3-4) —
   évite qu'une modification future du protocole ne diverge
   silencieusement du code exécuté.
8. **Retirer le code de debug oublié** (`TvKeyStore.exportCertificate()`,
   logs de diagnostic verbeux) — petit chantier, grand gain de lisibilité
   et de posture avant publication.
9. **Introduire Hilt et extraire la logique de décision de `FoxCore`**
   (Phase 3) — condition de tout le reste : sans ça, la testabilité et la
   maintenabilité resteront basses quoi qu'on ajoute par-dessus.
10. **Démarrer la conformité Play Store tôt, pas en fin de projet**
    (préparation continue avant la Phase 9) — les données de santé
    imposent une revue Google plus stricte ; mieux vaut concevoir avec
    cette contrainte en tête dès la Phase 6-7 que la découvrir à la
    soumission.

---

*Prochain audit recommandé : à la clôture de la Phase 3 (refactor
architectural), pour mesurer l'écart avant/après sur Architecture,
Maintenabilité et Testabilité.*
