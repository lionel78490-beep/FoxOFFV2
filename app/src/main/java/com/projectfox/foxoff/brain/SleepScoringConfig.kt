package com.projectfox.foxoff.brain

import java.time.Duration

/**
 * Configuration for the weighted sleep scoring system.
 * Values can be tuned to adjust the sensitivity of the detection.
 */
data class SleepScoringConfig(
    /**
     * Relevé de 5% à 18% le 2026-08-10, avec `sustainedBpmDropDuration`
     * ramenée de 5 à 3 min (voir plus bas) — au rythme précédent (5%/5min),
     * atteindre ASLEEP (90%) demandait ~90 minutes de BPM bas ininterrompu
     * en partant de zéro, bien trop long pour l'objectif produit (couper la
     * TV avant que l'utilisateur ait raté une grande partie du film/de la
     * série qu'il regardait, pas après). Nouveau rythme : 5 octrois de 18%
     * sur 15 min (3 min × 5) suffisent pour atteindre 90% dans le
     * meilleur des cas (BPM bas ininterrompu) — le temps réel sera souvent
     * un peu plus long dès qu'un mouvement remet le compteur à zéro, ce qui
     * reste voulu. Rendu acceptable par le filet de sécurité déjà en place
     * (confirmation "toujours là ?" sur la montre avant la pause réelle,
     * voir SleepPauseCoordinator) — une détection plus rapide mais parfois
     * trop optimiste n'est plus aussi risquée qu'avant son existence.
     * Première estimation, à ajuster sur de vraies nuits de test.
     *
     * **Changée le 2026-08-15** (18% -> 14,3%) : proposition du framework
     * d'optimisation à 10 000 nuits synthétiques (`OptimizeSleepScoringConfigTest`,
     * voir ROADMAP.md Phase 5), validée sur 3 échantillons indépendants
     * (1000/8000/2000 nuits) avec, ensemble avec les autres champs modifiés
     * ci-dessous, un délai moyen divisé par 2 (20,9 -> 10,0 min) ET moins de
     * faux positifs (1,55% -> 1,30%) ET moins de détections manquées (8,50%
     * -> 6,15%) — pas un compromis. Motivé par un retard réel de 6h08
     * constaté le 15 août (`RealNightReplayTest`).
     *
     * **Revenue à 14,3% -> 18% le 2026-08-16** : la vérification sur vraie
     * nuit annoncée ci-dessus a eu lieu (`RealNightReplayPostOptimizationTest`,
     * nuit du 15-16 août) et a révélé une régression réelle sévère — FoxOFF
     * n'a JAMAIS confirmé ASLEEP ni mis la TV en pause de toute la nuit
     * (score bloqué à 0% de 1h15 à 7h40), alors que Samsung Health détectait
     * l'endormissement en 26 min. Cause combinée avec `bpmDropThreshold` et
     * `significantMovementPenalty` (voir leurs commentaires) : le plancher
     * `minBpmToday` se resserre en continu pendant la nuit, et un seuil de
     * tolérance trop strict autour de ce plancher finit par exclure des
     * lectures de sommeil pourtant normales. Rejeu de cette même nuit avec
     * les 6 valeurs historiques : score final 76% contre 0% avec les
     * valeurs du 15 août — preuve concrète que le modèle synthétique
     * d'optimisation (BPM de sommeil stable autour d'une valeur) ne
     * reproduit pas ce phénomène de plancher glissant, angle mort de tout
     * le framework d'optimisation. Voir ROADMAP.md Phase 5 et mémoire
     * `project_sleep_detection_investigation`.
     *
     * **Changée le 2026-08-16 (même jour, deuxième révision)** (18% ->
     * 24,3%) : `OptimizeWithRealNightsTest`, recherche corrigeant l'angle
     * mort ci-dessus en ajoutant les DEUX vraies nuits capturées à ce jour
     * comme contrainte de validation (en plus du jeu synthétique, dual
     * contrainte FP/manqués habituelle). Validée : délai jusqu'à ASLEEP sur
     * la nuit du 15 août 360 -> 270 min (-25%), sur la nuit du 15-16 août
     * 406 -> 146 min (-64%) ; synthétique (8000 nuits) FP 1,71% -> 1,65%,
     * manqués 21,81% -> 21,33% ; confirmé sur 4000 nuits indépendantes
     * (seed différent) FP 1,83% -> 1,80%, manqués 20,72% -> 20,00%.
     * Amélioration sur tous les axes, pas un compromis — mais validée sur
     * seulement 2 vraies nuits (risque résiduel assumé, contrairement à la
     * tentative du matin même, purement synthétique).
     */
    val bpmDropBonus: Float = 0.243f,
    val stationaryDurationBonus: Float = 0.10f, // +10%
    val tvOnBonus: Float = 0.03f,        // +3%
    val lateNightBonus: Float = 0.05f,   // +5%
    val userInteractionPenalty: Float = 0.20f, // -20%
    /**
     * Abaissée de 15% à 8% le 2026-08-12, après une nuit de test où
     * l'endormissement réel (confirmé par Samsung Health à 5 min près, et
     * ressenti par l'utilisateur) a eu lieu vers 23h25-23h30, mais FoxOFF
     * ne l'a confirmé qu'à 00h45 — 1h15 de retard. Cause identifiée dans le
     * journal Historique : le BPM était déjà bas et stable dès 23h24 (le
     * signal utile pour la détection), mais deux mouvements "importants"
     * (>movementThreshold) ont fait retomber le score à AWAKE, forçant à
     * reconstituer plusieurs cycles de `bpmDropBonus` avant de retrouver le
     * terrain perdu — un mouvement normal en s'endormant (se retourner,
     * ajuster l'oreiller) coûtait quasiment autant qu'un cycle entier de
     * bonus (15% de pénalité contre 18% de bonus toutes les 3 min),
     * neutralisant presque tout le gain d'un `sustainedBpmDropDuration`.
     * 8% reste un vrai signal (un mouvement franc pénalise toujours) sans
     * effacer la quasi-totalité du dernier cycle de bonus à lui seul.
     *
     * **Changée le 2026-08-15** (8% -> 16,7%) : proposition du framework
     * d'optimisation à 10 000 nuits (voir commentaire de `bpmDropBonus` pour
     * les résultats validés). Plus sévère qu'avant, mais combinée à
     * `sustainedBpmDropDuration` ramenée à 1 min (recovery plus rapide entre
     * deux mouvements) — le compromis inverse de 2026-08-12, validé
     * différemment cette fois sur 10 000 nuits plutôt qu'une seule nuit
     * réelle.
     *
     * **Revenue à 16,7% -> 8% le 2026-08-16** : régression réelle constatée
     * sur vraie nuit, voir commentaire de `bpmDropBonus`.
     *
     * **Changée à nouveau le 2026-08-16 (même jour)** (8% -> 13,4%) :
     * `OptimizeWithRealNightsTest`, voir résultats complets dans le
     * commentaire de `bpmDropBonus`. Valeur intermédiaire entre l'historique
     * (8%) et la tentative ratée du matin (16,7%), combinée à
     * `movementThreshold` relevé à 3,25 (moins de mouvements comptent comme
     * "significatifs" en premier lieu).
     */
    val significantMovementPenalty: Float = 0.134f, // -13,4%

    // Thresholds
    /**
     * Tolérance autour de `minBpmToday` (voir WeightedSleepAnalyzer/FoxBrain)
     * pour juger le BPM "assez bas". Relevé de 5% à 12% le 2026-08-10 après
     * DEUX nuits de test consécutives où le score n'a jamais dépassé ~15%
     * malgré un BPM stable et bas pendant des heures (45-52 bpm). Cause :
     * `minBpmToday` est le minimum ABSOLU du jour, qui continue de baisser
     * toute la nuit à mesure que le sommeil s'approfondit — avec 5% de
     * tolérance (≈2-3 bpm sur une base de 45-50), la moindre variation
     * naturelle du rythme cardiaque (respiration, micro-mouvement) suffisait
     * à repasser au-dessus du seuil et remettait `bpmBelowBaselineSince` à
     * zéro avant que 5 minutes continues ne soient jamais atteintes. 12%
     * (≈6 bpm sur cette même base) absorbe cette variabilité normale.
     * Le problème de fond — minBpmToday qui se fige dès la première lecture
     * de la soirée, parfois encore élevée, et ne fait ensuite que baisser
     * sans jamais remonter — a causé un faux positif réel le 2026-08-12
     * (minBpmToday figé à 71, seuil 79,5, a laissé passer un BPM de 79 très
     * loin du sommeil réel de l'utilisateur (45-52 bpm)). Corrigé le
     * 2026-08-13 (voir WeightedSleepAnalyzer/FoxBrain) : minBpmToday ne peut
     * plus que RESSERRER le seuil par rapport à restingBpmBaseline calibré
     * (Health Connect), jamais l'élargir.
     *
     * **Changée le 2026-08-15** (12% -> 7%) : proposition du framework
     * d'optimisation à 10 000 nuits (voir commentaire de `bpmDropBonus`).
     * Plus strict qu'avant sur ce qui compte comme "assez bas" — compense
     * la fenêtre de confirmation raccourcie (`sustainedBpmDropDuration`
     * 1 min au lieu de 3).
     *
     * **Revenue à 7% -> 12% le 2026-08-16** : régression réelle constatée
     * sur vraie nuit, voir commentaire de `bpmDropBonus`. Cause précise ici :
     * combiné au plancher `minBpmToday` qui se resserre en continu (voir
     * WeightedSleepAnalyzer/FoxBrain), 7% de tolérance autour d'un plancher
     * déjà tombé à 44 bpm ne laisse passer que les BPM ≤ ~47 — trop strict
     * pour un sommeil réel qui oscille naturellement sur plusieurs bpm
     * (45-58 bpm observés cette nuit-là). 12% redonne la marge nécessaire.
     *
     * **Changée à nouveau le 2026-08-16 (même jour)** (12% -> 11,0%) :
     * `OptimizeWithRealNightsTest`, voir résultats complets dans le
     * commentaire de `bpmDropBonus`. Quasi inchangée par rapport à
     * l'historique — la leçon du plancher glissant (voir ci-dessus) est
     * respectée, contrairement à la tentative ratée du matin (7%).
     */
    val bpmDropThreshold: Float = 0.110f,

    /**
     * Magnitude RMS (m/s², voir MovementEngine côté montre) au-delà de
     * laquelle un mouvement est jugé "significatif". Recalibré le 2026-08-08
     * à partir d'une vraie nuit de test : à 0.5 (valeur d'origine, jamais
     * calibrée faute de données réelles avant ce correctif), des dizaines de
     * "mouvements significatifs" étaient détectés par nuit — bien trop
     * fréquent pour un sommeil normal (probablement de simples ajustements
     * du poignet), ce qui remettait le score à zéro avant qu'il ait une
     * chance de monter et empêchait TOUTE détection d'endormissement de
     * toute la nuit. 2.0 laisse passer ces micro-mouvements tout en
     * détectant les vrais changements de position (les pics observés à
     * 2-7+ dans cette même nuit). À réajuster si trop/pas assez sensible
     * sur d'autres nuits.
     *
     * **Changée le 2026-08-15** (2.0 -> 1.55) : proposition du framework
     * d'optimisation à 10 000 nuits (voir commentaire de `bpmDropBonus`) —
     * légèrement plus sensible, combinée à `significantMovementPenalty`
     * bien plus sévère (16,7% au lieu de 8%).
     *
     * **Revenue à 1.55 -> 2.0 le 2026-08-16** : régression réelle constatée
     * sur vraie nuit, voir commentaire de `bpmDropBonus`.
     *
     * **Changée à nouveau le 2026-08-16 (même jour)** (2.0 -> 3.25) :
     * `OptimizeWithRealNightsTest`, voir résultats complets dans le
     * commentaire de `bpmDropBonus`. Encore MOINS sensible que
     * l'historique — moins de mouvements comptent comme "significatifs",
     * combiné à `significantMovementPenalty` relevée à 13,4% (chaque
     * mouvement qui compte vraiment pénalise un peu plus, mais il y en a
     * moins).
     */
    val movementThreshold: Float = 3.25f,
    val lateNightHour: Int = 23,

    /**
     * Durée minimale entre deux octrois du `bpmDropBonus` tant que le BPM
     * reste CONTINÛMENT sous le seuil (voir WeightedSleepAnalyzer/FoxBrain,
     * champs `bpmBelowBaselineSince`/`lastBpmDropBonusAt`) — octroi
     * PÉRIODIQUE, pas une seule fois pour tout l'épisode : plus le sommeil
     * se prolonge, plus le score continue de monter. Corrige deux bugs
     * réels et opposés : (1) 2026-08-07, sans aucune garde, le bonus
     * s'ajoutait à CHAQUE échantillon sans jamais redescendre — assis
     * immobile devant la TV, le score grimpait jusqu'à ASLEEP en quelques
     * minutes sans endormissement réel ; (2) 2026-08-08, un premier
     * correctif trop strict (bonus accordé UNE SEULE fois par épisode)
     * plafonnait le score bien trop bas pour jamais atteindre ASLEEP, même
     * après plusieurs heures de sommeil réel confirmé (nuit de test avec
     * BPM stable 47-54 pendant des heures, score resté à 0-5% toute la
     * nuit). Ramenée de 5 à 3 min le 2026-08-10, avec `bpmDropBonus` monté à
     * 18% (voir son commentaire) — détection plus rapide, objectif ~15 min
     * jusqu'à ASLEEP dans le meilleur des cas plutôt que ~90 min.
     *
     * **Changée le 2026-08-15** (3 min -> 1 min) : proposition du framework
     * d'optimisation à 10 000 nuits (voir commentaire de `bpmDropBonus`) —
     * octroi plus fréquent, compensé par `bpmDropThreshold` bien plus
     * strict (7% au lieu de 12%) pour ne pas reproduire le faux positif du
     * 2026-08-07/12.
     *
     * **Revenue à 1 min -> 3 min le 2026-08-16** : régression réelle
     * constatée sur vraie nuit, voir commentaire de `bpmDropBonus`.
     *
     * **Changée à nouveau le 2026-08-16 (même jour)** (3 min -> 2min49s) :
     * `OptimizeWithRealNightsTest`, voir résultats complets dans le
     * commentaire de `bpmDropBonus`. Quasi inchangée par rapport à
     * l'historique.
     */
    val sustainedBpmDropDuration: Duration = Duration.ofSeconds(169), // 2min49s

    // Seuils de décision de FoxCore.startOrchestration() (Brain Decision
    // Loop) — déplacés ici depuis des valeurs codées en dur (voir
    // ROADMAP.md, Phase 2 "Stabilisation fonctionnelle minimale"), pour que
    // toute la sensibilité de détection soit configurable au même endroit.
    /** Score au-delà duquel la montre passe en mode Haute Précision. */
    val highPrecisionThreshold: Float = 0.70f,
    /** Confiance minimale exigée pour déclencher la pause TV automatique. */
    val autoPauseConfidenceThreshold: Float = 0.80f,

    /**
     * Multiplicateur appliqué au score quand la TV s'éteint
     * (`WeightedSleepAnalyzer`, branche `TVTurnedOff`). Valeur historique
     * (0.5) inchangée par défaut — rendue configurable le 2026-08-15 après
     * un écart réel constaté (nuit du 15 août : TV éteinte en veille
     * automatique à 04h10 pendant que l'utilisateur dormait déjà, score
     * amputé de plus de 30 points d'un coup, ~2h de progression annulées,
     * endormissement confirmé 6h08 après le vrai endormissement réel).
     * Voir `RealNightReplayTest`/ROADMAP.md Phase 5.
     *
     * **Changée le 2026-08-15** (0.5 -> 0.62) : proposition du framework
     * d'optimisation à 10 000 nuits (voir commentaire de `bpmDropBonus`) —
     * moins punitif que la valeur historique quand la TV s'éteint seule.
     *
     * **Revenue à 0.62 -> 0.5 le 2026-08-16** : régression réelle constatée
     * sur vraie nuit, voir commentaire de `bpmDropBonus`. Note : ce champ
     * n'est pas la cause principale de cette régression précise (la
     * coupure TV a fait perdre des points mais le score s'en était remis),
     * revenu à l'historique par cohérence avec le reste des 6 valeurs —
     * toutes proposées et validées ensemble par le même run d'optimisation
     * du 15 août, donc reprises ensemble.
     *
     * **Changée à nouveau le 2026-08-16 (même jour)** (0.5 -> 0.956) :
     * `OptimizeWithRealNightsTest`, voir résultats complets dans le
     * commentaire de `bpmDropBonus`. Quasi plus de pénalité du tout quand
     * la TV s'éteint seule — corrige directement la cause n°1 identifiée
     * dès le 15 août (extinction TV en veille automatique pendant le
     * sommeil, ~30 points de score perdus d'un coup).
     */
    val tvTurnedOffMultiplier: Float = 0.956f,

    /**
     * Ajouté le 2026-08-16 (candidat au réglage de la régression du
     * `minBpmToday` glissant — voir commentaire de `bpmDropBonus` et
     * ROADMAP.md Phase 5). `minBpmToday` (FoxBrain) ne fait que baisser
     * toute la nuit par conception (voir sa doc) : une lecture BPM isolée
     * et basse (bruit capteur ou vrai micro-creux ponctuel) resserre
     * PERMANENMENT le seuil "assez bas" pour tout le reste de la nuit, même
     * si elle n'est jamais reconfirmée. Quand ce champ est `true`, une
     * nouvelle valeur plus basse ne devient le nouveau `minBpmToday` que si
     * la lecture SUIVANTE est aussi proche de ce nouveau creux (à
     * `minBpmConfirmationToleranceBpm` près) — une lecture isolée reste en
     * attente (`FoxBrainState.pendingLowBpm`) sans resserrer le seuil tant
     * qu'elle n'est pas confirmée.
     *
     * `false` par défaut : AUCUN changement de comportement tant que ce
     * champ n'est pas activé explicitement (champ additif, comme
     * `tvTurnedOffMultiplier` en son temps) — pas encore validé sur assez
     * de nuits réelles pour devenir la valeur par défaut.
     */
    val debounceMinBpmFloor: Boolean = false,

    /**
     * Tolérance (en bpm) utilisée par `debounceMinBpmFloor` (voir sa doc)
     * pour juger qu'une deuxième lecture confirme le creux détecté par la
     * première, plutôt que d'exiger une valeur strictement identique (le
     * BPM varie naturellement de quelques battements d'une lecture à
     * l'autre même pendant un sommeil stable).
     */
    val minBpmConfirmationToleranceBpm: Int = 2
)
