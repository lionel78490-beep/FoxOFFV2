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
     * constaté le 15 août (`RealNightReplayTest`). **Non encore revérifiée
     * sur une vraie nuit** — le modèle synthétique ne reproduit pas
     * parfaitement le bruit capteur réel, à confirmer sur les prochaines
     * nuits.
     */
    val bpmDropBonus: Float = 0.143f,
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
     * réelle. Non revérifiée sur une vraie nuit.
     */
    val significantMovementPenalty: Float = 0.167f, // -16,7%

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
     * 1 min au lieu de 3). Non revérifiée sur une vraie nuit.
     */
    val bpmDropThreshold: Float = 0.070f,

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
     * bien plus sévère (16,7% au lieu de 8%). Non revérifiée sur une vraie
     * nuit.
     */
    val movementThreshold: Float = 1.55f,
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
     * 2026-08-07/12. Non revérifiée sur une vraie nuit.
     */
    val sustainedBpmDropDuration: Duration = Duration.ofMinutes(1),

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
     * Non revérifiée sur une vraie nuit.
     */
    val tvTurnedOffMultiplier: Float = 0.62f
)
