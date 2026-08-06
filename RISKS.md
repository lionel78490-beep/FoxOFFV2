# FoxOFF — Registre des risques techniques

> Échelles utilisées : **Impact** = Faible / Modéré / Élevé / Critique.
> **Probabilité** = Faible / Modérée / Élevée. Un risque n'est pas retiré du
> registre quand il est mitigé — son statut passe à "Sous contrôle" avec la
> date/phase de mitigation.

## Vue d'ensemble

| Risque | Impact | Probabilité | Phase de mitigation |
|---|---|---|---|
| Android Doze / App Standby | Élevé | Élevée | Phase 1 (minimal) → 6 (définitif) |
| Battery optimization constructeurs (Samsung, Xiaomi...) | Élevé | Élevée | Phase 6 |
| Wear Health Services | Élevé | Modérée | Phase 1 |
| Android TV Remote v2 (protocole non officiel) | Élevé | Modérée | Phase 4 (continu) |
| Certificats TLS | Modéré | Faible | Phase 4 |
| Wi-Fi (réseau local) | Élevé | Modérée | Phase 4 |
| Bluetooth (portée/fiabilité) | Modéré | Modérée | Phase 1 |
| Wear OS (autonomie batterie, fragmentation) | Élevé | Élevée | Phase 1 (continu) |
| Play Store Policy (données de santé) | Critique | Modérée | Phase 9 |
| Dérive du schéma protobuf vendored | Modéré | Modérée | Phase 3-4 |

---

## Android Doze / App Standby

**Impact** — Élevé. C'est le mécanisme même du produit (surveillance
continue pendant le sommeil) qui est en conflit direct avec le
comportement par défaut d'Android : si le processus est suspendu, la
détection s'arrête silencieusement, sans que l'utilisateur ne le sache.

**Probabilité** — Élevée. C'est le comportement standard de tout appareil
Android moderne sans intervention, dès que l'écran est éteint et l'app en
arrière-plan.

**Plan de mitigation**
- Phase 1 : keep-alive minimal pour valider la boucle sur une nuit de test
  réelle (ne résout pas le problème, permet de le mesurer).
- Phase 6 : service d'orchestration en foreground avec notification
  persistante + `WorkManager` pour les reprises — la solution standard et
  recommandée par Android, à privilégier avant toute autre option.
- **Explicitement exclu par défaut** : demander l'exemption d'optimisation
  batterie à l'utilisateur — voir décisions repoussées de
  [ROADMAP.md](ROADMAP.md). Ce risque doit être résolu par une
  implémentation standard correcte, pas par une dérogation système.

---

## Battery optimization spécifique aux constructeurs (Samsung, Xiaomi, Huawei, OnePlus...)

**Impact** — Élevé. Les surcouches (One UI, MIUI, ColorOS...) implémentent
des politiques de gestion d'énergie **plus agressives** que l'AOSP standard
et peuvent tuer un foreground service que Doze standard aurait épargné.

**Probabilité** — Élevée. Une part significative du marché Android grand
public tourne sur ces surcouches ; c'est un problème documenté et
récurrent pour toute app nécessitant un fonctionnement fiable en
arrière-plan.

**Plan de mitigation**
- Phase 6 : foreground service + notification visible (réduit fortement le
  risque de kill sur la plupart des surcouches, qui épargnent en priorité
  les apps avec notification active).
- Détection applicative d'une interruption anormale (le service ne s'est
  pas arrêté proprement) et affichage d'un message explicatif au
  lancement suivant, avec lien vers les réglages constructeur concernés —
  **information à l'utilisateur, jamais action automatique sur les
  réglages système**.
- Tests manuels sur au moins un appareil Samsung avant publication (Phase
  9), le constructeur le plus susceptible de générer des retours
  utilisateurs sur ce sujet en France/Europe.

---

## Wear Health Services

**Impact** — Élevé. C'est la source unique du signal physiologique — sans
BPM fiable, le Fox Brain n'a plus de donnée d'entrée principale.

**Probabilité** — Modérée. L'API est stable, mais la disponibilité réelle
d'un flux BPM passif continu varie selon le modèle de montre, le firmware
et l'usure du capteur optique.

**Plan de mitigation**
- ADR-007 : fallback documenté vers `SensorManager`/`BODY_SENSORS` si
  Health Services indisponible.
- Dégradation explicite plutôt que silencieuse : si le signal BPM devient
  indisponible, le Fox Brain et le Dashboard doivent l'afficher clairement
  (cf. `DashboardUiState` déjà prévu pour un état "Recherche montre...").
- Tests de la Phase 1 sur le matériel réel du développeur avant toute
  généralisation.

---

## Android TV Remote v2 (protocole non officiel)

**Impact** — Élevé. C'est la seconde moitié du produit (l'action sur la
TV) ; sans elle, FoxOFF détecte mais n'agit pas.

**Probabilité** — Modérée. Le protocole est utilisé et maintenu par des
projets tiers matures (Home Assistant `androidtvremote2`, l'app officielle
Google) depuis plusieurs années, ce qui témoigne d'une certaine stabilité —
mais ce n'est **pas** une API publique garantie par contrat : Google peut
faire évoluer le firmware Android TV sans préavis de compatibilité
descendante.

**Plan de mitigation**
- Phase 4 : une seule TV principale ciblée en V2 initiale, ce qui réduit la
  surface de compatibilité à tester et maintenir (voir décisions repoussées
  de la Roadmap sur le multi-TV).
- `TvLabActivity` conservé comme outil de diagnostic rapide en cas de
  rupture du protocole après une mise à jour de la TV.
- Veille sur les implémentations de référence pour détecter tôt un
  changement de comportement.

---

## Certificats TLS

**Impact** — Modéré. Voir [DECISIONS.md](DECISIONS.md) ADR-009 : le
`TrustManager` accepte tout certificat serveur sans vérification, et les
clés privées sont stockées en Base64 clair dans `SharedPreferences` (ni
Android Keystore, ni chiffrement au repos). L'impact reste modéré car le
canal ne transporte qu'une commande play/pause, aucune donnée personnelle
sensible.

**Probabilité** — Faible. Exploitation nécessite un accès local (même
réseau Wi-Fi, ou accès au stockage de l'appareil) — pas exploitable à
distance.

**Plan de mitigation**
- Phase 4 : trancher entre `TvKeyStore` (ancien) et `TvIdentity` (nouveau) —
  une seule implémentation d'identité TLS active (cf.
  [ARCHITECTURE.md](ARCHITECTURE.md) §6).
- Migrer le stockage de la clé privée vers l'Android Keystore (clé
  non extractible du matériel sécurisé) plutôt que `SharedPreferences`.
- Implémenter l'épinglage de l'empreinte du certificat serveur observée
  pendant le pairing, pour les connexions ultérieures (déjà annoncé en
  commentaire dans le code — non fait).

---

## Wi-Fi (réseau local)

**Impact** — Élevé. La découverte et le contrôle TV dépendent entièrement
du réseau local ; sans connectivité device-to-device fonctionnelle, le TV
Engine est inopérant.

**Probabilité** — Modérée. La plupart des foyers ont un réseau plat
unique, mais l'isolation de point d'accès (fréquente sur les réseaux
invités, certaines box opérateur), la séparation bandes 2,4/5 GHz, ou un
changement d'IP DHCP sont des cas réels et non marginaux.

**Plan de mitigation**
- Phase 4 : reconnexion automatique après coupure/changement d'IP,
  mémorisation de la dernière IP connue de la TV comme fallback à la
  découverte réseau.
- Message d'erreur explicite à l'utilisateur en cas d'échec de découverte,
  avec pistes de diagnostic (isolation Wi-Fi, réseau invité).

---

## Bluetooth (communication montre↔téléphone)

**Impact** — Modéré. Dégrade la fiabilité/latence du signal, mais la Data
Layer API (ADR-008) bascule elle-même vers Wi-Fi si le Bluetooth est
indisponible — le risque est partiellement absorbé par le choix
d'architecture déjà fait.

**Probabilité** — Modérée. Portée limitée (chambre ↔ salon selon la
disposition du logement), interférences possibles.

**Plan de mitigation**
- S'appuyer sur l'abstraction Data Layer API plutôt que gérer le Bluetooth
  directement (déjà le choix fait — ADR-008).
- Tester la portée réelle dans un scénario domestique représentatif
  pendant la Phase 1.

---

## Wear OS (fragmentation, autonomie batterie)

**Impact** — Élevé. Une montre à court de batterie en milieu de nuit
équivaut à une perte totale de signal pour le reste de la session.

**Probabilité** — Élevée. L'autonomie limitée des montres Wear OS avec
capteur BPM actif en continu est un problème connu et documenté du
secteur, indépendant de FoxOFF.

**Plan de mitigation**
- Le mécanisme de "mode haute précision" déjà esquissé dans
  `FoxCore.startOrchestration()` (activé seulement au-delà de 70% de
  probabilité de sommeil, plutôt qu'en continu) va dans le bon sens — à
  conserver et renforcer lors de l'implémentation réelle en Phase 1.
- Test d'autonomie sur une nuit complète en conditions réelles, avant de
  considérer la Phase 1 terminée (fait partie de son critère de sortie).
- Documenter dans l'app les modèles de montre testés/recommandés plutôt
  que de promettre une compatibilité universelle.

---

## Play Store Policy (données de santé)

**Impact** — Critique. Un rejet ou un retrait pour non-conformité bloque
purement et simplement la distribution du produit.

**Probabilité** — Modérée. Les règles Google sur les données de santé et
les permissions sensibles (`BODY_SENSORS`, `health.READ_HEART_RATE`)
évoluent et les applications de cette catégorie sont examinées plus
strictement que la moyenne.

**Plan de mitigation**
- Phase 9 dédiée entièrement à la conformité : formulaire Data Safety,
  politique de confidentialité, justification explicite de chaque
  permission sensible.
- Revue des règles Google Play en vigueur **avant** soumission plutôt
  qu'en réaction à un rejet.
- Conserver le principe de sobriété en données de [VISION.md](VISION.md) —
  moins de données collectées, moins de surface de non-conformité
  possible.

---

## Dérive du schéma protobuf vendored

**Impact** — Modéré. Si `polo.proto` ou `remotemessage.proto` est modifié
sans régénérer manuellement les classes Java correspondantes (le plugin
Gradle protobuf n'étant pas appliqué sur `:app` — voir
[ARCHITECTURE.md](ARCHITECTURE.md) §6), le code source de vérité et le
code réellement exécuté divergent silencieusement.

**Probabilité** — Modérée. Se déclenche uniquement si quelqu'un modifie un
fichier `.proto` en pensant que la régénération est automatique — probable
si un futur contributeur (cf. [CONTRIBUTING.md](CONTRIBUTING.md)) n'est pas
prévenu explicitement de cette particularité.

**Plan de mitigation**
- Phase 3-4 : appliquer le plugin Gradle `com.google.protobuf` sur `:app`
  et régénérer proprement les classes, ou documenter de façon très visible
  (README du dossier `proto/`) que la génération est manuelle tant que ce
  n'est pas fait.
- En attendant, ne jamais modifier `polo.proto`/`remotemessage.proto` sans
  régénérer et valider manuellement les classes vendored correspondantes.
