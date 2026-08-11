package com.projectfox.foxoff.brain

import java.time.Instant
import java.time.LocalTime

/**
 * Global state of the Fox Brain.
 * This is the single source of truth for the entire application (including UI).
 */
data class FoxBrainState(
    // Global Status
    val isMonitoring: Boolean = false,
    val detectedSleepState: SleepState = SleepState.AWAKE,
    val lastScore: FoxBrainScore = FoxBrainScore(0f, 1f, "En attente de données..."),
    val lastEventTime: Instant? = null,

    // Watch Status
    val watchConnected: Boolean = false,
    val watchName: String = "Recherche...",
    val watchBattery: Int = 0,
    val watchIsCharging: Boolean = false,

    // TV Status
    val tvConnected: Boolean = false,
    val tvName: String = "Déconnectée",
    val tvCurrentApp: String = "Aucune",
    val tvIsPaused: Boolean = false,
    val tvLastCommand: String = "Aucune",
    val tvLastCommandTime: LocalTime? = null,

    // Biological Data
    val currentBpm: Int? = null,
    val minBpmToday: Int = 0,
    val maxBpmToday: Int = 0,
    val lastBpmTime: LocalTime? = null,
    val movementMagnitude: Float = 0f,

    /**
     * Valeur de repli utilisée par WeightedSleepAnalyzer comme référence
     * "BPM au repos" tant qu'aucune vraie donnée (minBpmToday) n'est encore
     * disponible pour l'utilisateur — typiquement à la toute première
     * utilisation, avant le premier échantillon BPM réel de la session.
     * Dès qu'un vrai minBpmToday existe, il prend le dessus (voir
     * WeightedSleepAnalyzer) : cette valeur ne sert donc qu'au "cold start".
     *
     * 70 bpm : repère générique le plus couramment cité pour un rythme
     * cardiaque au repos adulte, à l'intérieur de la plage normale 60-100
     * bpm reconnue par la Mayo Clinic et l'American Heart Association.
     */
    val restingBpmBaseline: Int = 70,

    /**
     * Instant depuis lequel le BPM est resté CONTINÛMENT sous le seuil de
     * baisse — null si la dernière mesure était au-dessus du seuil (aucun
     * "épisode" en cours). Remis à null dès qu'un échantillon repasse
     * au-dessus du seuil. Voir SleepScoringConfig.sustainedBpmDropDuration.
     */
    val bpmBelowBaselineSince: Instant? = null,

    /**
     * Dernier instant où le bonus de baisse de BPM a été accordé — null
     * tant qu'aucun bonus n'a encore été accordé pour l'épisode en cours.
     * Le bonus se réaccorde PÉRIODIQUEMENT (toutes les
     * sustainedBpmDropDuration, voir SleepScoringConfig) tant que le BPM
     * reste sous le seuil, pas une seule fois pour tout l'épisode — sinon
     * le score plafonne bien trop bas pour jamais atteindre ASLEEP même
     * après plusieurs heures de sommeil réel (régression observée dans la
     * nuit du 2026-08-07 au 08, corrigée le 08-08). Remis à null dès que le
     * BPM repasse au-dessus du seuil (nouvel épisode à venir).
     */
    val lastBpmDropBonusAt: Instant? = null
)
