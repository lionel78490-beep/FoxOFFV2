# FoxOFF — Vision produit

> Document de référence. Toute décision de scope (Roadmap, ADR, priorisation)
> doit pouvoir se justifier par rapport à ce document.

## Qu'est-ce que FoxOFF

FoxOFF est une application Android qui **coupe automatiquement la TV quand
son utilisateur s'endort devant elle**. Une montre Wear OS mesure la
fréquence cardiaque et le mouvement pendant que l'utilisateur regarde la TV ;
un moteur de décision (Fox Brain) évalue en continu une probabilité
d'endormissement ; au-delà d'un seuil de confiance, le téléphone envoie une
commande pause à la TV via le protocole Android TV Remote v2 — sans aucune
action manuelle.

Le produit tient en une phrase : **s'endormir devant la TV ne doit plus
jamais vouloir dire se réveiller à 3h du matin devant un écran allumé.**

## Le problème résolu

S'endormir devant un film ou une série est un comportement humain ordinaire,
pas un échec de discipline. Les solutions existantes sont insatisfaisantes :

- Les **minuteries de veille intégrées aux TV** sont aveugles à l'état réel
  de l'utilisateur : elles coupent après un temps fixe, qu'il dorme déjà
  depuis 5 minutes ou qu'il soit encore parfaitement éveillé.
- Couper la TV **manuellement** suppose d'être éveillé pour le faire —
  contradiction du problème lui-même.
- Les applications de **suivi du sommeil** existantes mesurent et
  rapportent, mais n'agissent sur rien dans l'environnement de l'utilisateur.

FoxOFF ferme cette boucle : détection physiologique réelle → décision →
action, sans intervention humaine.

## Utilisateurs visés

- Possède **une montre Wear OS** (condition nécessaire : c'est la source du
  signal physiologique) et **une TV Android / Google TV** compatible avec le
  protocole Android TV Remote v2.
- Regarde régulièrement du contenu au lit ou sur un canapé, le soir.
- Est sensible à l'hygiène de sommeil (écran allumé toute la nuit,
  luminosité, bruit) sans vouloir changer son rituel du soir.
- Profil technophile raisonnable : à l'aise pour appairer une montre et une
  TV, pas nécessairement développeur.

FoxOFF ne vise **pas** un public grand public non équipé Wear OS — ce n'est
pas un produit "achetez un capteur dédié", c'est un produit qui exploite un
capteur que l'utilisateur porte déjà pour une autre raison.

## Philosophie du projet

- **Un capteur qu'on porte déjà, pas un gadget de plus.** FoxOFF n'ajoute
  aucun matériel : il tire parti d'une montre déjà portée et d'une TV déjà
  possédée.
- **Silencieux par défaut.** Le produit doit agir sans demander de
  validation nocturne à un utilisateur endormi — l'automatisation n'a de
  valeur que si elle n'exige rien de la personne qu'elle est censée aider.
- **Explicable, pas mystique.** La décision de couper la TV doit toujours
  pouvoir se justifier ("fréquence cardiaque en baisse depuis 12 minutes,
  immobilité totale, 23h47") — voir [ARCHITECTURE.md](ARCHITECTURE.md) et
  [DECISIONS.md](DECISIONS.md) sur le choix d'un moteur à règles avant tout
  modèle appris.
- **Sobre en permissions et en données.** FoxOFF ne collecte que ce qui sert
  directement la détection de sommeil ; pas de télémétrie comportementale
  au-delà de ce besoin.

## Principe fondamental

> **La fiabilité passe toujours avant les nouvelles fonctionnalités.**
>
> Une fonctionnalité instable n'est jamais considérée comme terminée.

Ce principe n'est pas une formule : il conditionne l'ordre de
[ROADMAP.md](ROADMAP.md) (la boucle Watch → Phone → Brain est validée en
Phase 1, avant toute réécriture architecturale), le
[Definition of Done](ROADMAP.md#definition-of-done-dod--obligatoire-pour-toute-phase)
appliqué à chaque phase, et l'arbitrage de toute demande de fonctionnalité
future : une fonctionnalité qui rend la détection moins fiable (fausse
alerte, TV coupée par erreur, boucle qui décroche en arrière-plan) est un
régression, quel que soit son intérêt par ailleurs.

## Objectifs à court terme (V2 initiale)

- La boucle complète (montre → téléphone → décision → coupure TV) fonctionne
  de façon fiable sur **une** TV principale, toute une nuit, sans
  intervention.
- Le moteur de décision (Fox Brain) est testé, calibrable, et explique
  toujours sa décision.
- L'application survit aux mécanismes d'économie d'énergie Android standards
  sans demander à l'utilisateur de désactiver des protections système par
  défaut.
- Historique minimal des nuits passées, consultable, pour donner à
  l'utilisateur une preuve tangible que le système fonctionne.
- Publication sur le Play Store dans le respect des règles applicables aux
  données de santé.

## Objectifs à long terme

- Calibration individuelle poussée (BPM de repos personnel, patterns de
  sommeil propres à l'utilisateur).
- Personnalisation assistée par des modèles légers on-device, en complément
  du moteur à règles — jamais en remplacement (voir "Décisions repoussées"
  dans [ROADMAP.md](ROADMAP.md)).
- Extension possible à d'autres actions liées au coucher (au-delà de la
  seule pause TV), si et seulement si la fiabilité du cœur du produit est
  acquise.

## Hors périmètre (volontairement)

- **Domotique généraliste** (lumières, chauffage, volets...) : FoxOFF n'est
  pas une plateforme domotique. Toute intégration de ce type resterait
  secondaire et postérieure à la maturité du produit central.
- **Suivi de sommeil quantifié complet** (phases de sommeil, score de
  qualité façon Oura/Whoop) : FoxOFF détecte un seul événement
  (endormissement devant la TV), il ne remplace pas un tracker de sommeil
  dédié.
- **Support multi-TV / multi-pièce** dans la V2 initiale : voir
  "Décisions repoussées" dans [ROADMAP.md](ROADMAP.md).
- **Matériel dédié** : pas de capteur propriétaire, pas de hub. FoxOFF
  s'appuie exclusivement sur du matériel Wear OS / Android TV déjà présent
  chez l'utilisateur.
- **Réseaux sociaux, partage, gamification** : hors sujet pour un produit
  dont l'usage se déroule, par définition, pendant que l'utilisateur
  s'endort.
