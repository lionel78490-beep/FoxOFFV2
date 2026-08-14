package com.projectfox.foxoff.brain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Couverture exhaustive de `WeightedSleepAnalyzer` — voir ROADMAP.md Phase 5
 * ("Tests unitaires exhaustifs du moteur de scoring : c'est la logique la
 * plus critique du produit... aujourd'hui zéro test"). Logique pure, sans
 * dépendance Android — chaque règle de `SleepScoringConfig` est vérifiée
 * indépendamment, plus les bornes de clamp [0,1] et le comportement par
 * défaut (confiance fixe, événements non gérés).
 */
class WeightedSleepAnalyzerTest {

    private val analyzer = WeightedSleepAnalyzer()
    private val config = SleepScoringConfig()

    private fun stateWithScore(
        prob: Float,
        reason: String = "précédent",
        minBpmToday: Int = 0,
        tvConnected: Boolean = false
    ) = FoxBrainState(
        lastScore = FoxBrainScore(prob, 0.85f, reason),
        minBpmToday = minBpmToday,
        tvConnected = tvConnected
    )

    // --- HeartRateReceived ---
    //
    // Le bonus n'est accordé qu'après config.sustainedBpmDropDuration (5 min
    // par défaut) de BPM CONTINÛMENT sous le seuil, puis se RÉ-accorde
    // PÉRIODIQUEMENT tant que le BPM reste bas (voir lastBpmDropBonusAt) —
    // pas une seule fois pour tout l'épisode : ce premier design plafonnait
    // le score bien trop bas pour jamais atteindre ASLEEP, même après
    // plusieurs heures de sommeil réel (régression du 2026-08-08, corrigée
    // le jour même). Voir SleepScoringConfig pour l'historique complet.

    private fun sustainedSince() = Instant.now().minus(Duration.ofMinutes(6))

    @Test
    fun `heart rate drop sustained beyond the required duration grants the bonus`() {
        val state = stateWithScore(0.30f, minBpmToday = 60)
            .copy(bpmBelowBaselineSince = sustainedSince())

        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 55f, source = "TEST"), state)

        assertEquals(0.30f + config.bpmDropBonus, score.sleepProbability, 0.0001f)
        assertEquals("Baisse soutenue de la fréquence cardiaque détectée", score.reason)
    }

    @Test
    fun `heart rate drop not yet sustained grants no bonus`() {
        // bpmBelowBaselineSince absent (premier échantillon sous le seuil,
        // ou état par défaut) : rien n'est encore accordé, même si le BPM
        // est bien sous le seuil.
        val state = stateWithScore(0.30f, minBpmToday = 60)

        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 55f, source = "TEST"), state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `heart rate drop sustained but less than required duration grants no bonus`() {
        val state = stateWithScore(0.30f, minBpmToday = 60)
            .copy(bpmBelowBaselineSince = Instant.now().minus(Duration.ofMinutes(2)))

        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 55f, source = "TEST"), state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `heart rate drop grants no bonus again before the next period elapses since last grant`() {
        // Le dernier octroi date de 2 min (< 5 min) : pas encore un nouveau
        // bonus, même si l'épisode a commencé il y a bien plus longtemps.
        val state = stateWithScore(0.30f, minBpmToday = 60)
            .copy(bpmBelowBaselineSince = sustainedSince(), lastBpmDropBonusAt = Instant.now().minus(Duration.ofMinutes(2)))

        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 55f, source = "TEST"), state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `heart rate drop grants a new bonus once the period has elapsed since the last grant`() {
        // Un premier octroi a eu lieu il y a plus de 5 min : un DEUXIÈME
        // bonus doit être accordé — c'est exactement l'accumulation
        // progressive qui manquait dans la version trop stricte du 2026-08-08.
        val state = stateWithScore(0.30f, minBpmToday = 60)
            .copy(bpmBelowBaselineSince = Instant.now().minus(Duration.ofMinutes(20)), lastBpmDropBonusAt = sustainedSince())

        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 55f, source = "TEST"), state)

        assertEquals(0.30f + config.bpmDropBonus, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `heart rate at or above baseline threshold does not change probability`() {
        val state = stateWithScore(0.30f, minBpmToday = 60)
            .copy(bpmBelowBaselineSince = sustainedSince())

        // Seuil = 60 * 1.12 = 67.2 ; 70 >= 67.2 -> pas de bonus.
        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 70f, source = "TEST"), state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `heart rate drop falls back to restingBpmBaseline when no real minBpmToday yet`() {
        // Avant le premier échantillon réel (minBpmToday = 0, valeur par
        // défaut), la règle s'appuie sur restingBpmBaseline (70 par défaut)
        // plutôt que d'être désactivée — voir FoxBrainState.
        val state = stateWithScore(0.30f, minBpmToday = 0)
            .copy(restingBpmBaseline = 70, bpmBelowBaselineSince = sustainedSince())

        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 60f, source = "TEST"), state)

        assertEquals(0.30f + config.bpmDropBonus, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `real minBpmToday takes precedence over restingBpmBaseline once available`() {
        // baseline réelle 50 -> seuil 56 (50 * 1.12) ; 58 bpm ne déclenche
        // PAS le bonus contre la référence réelle (58 >= 56), même si 58
        // aurait déclenché le bonus contre restingBpmBaseline=70 (seuil 78.4).
        val state = stateWithScore(0.30f, minBpmToday = 50).copy(restingBpmBaseline = 70)

        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 58f, source = "TEST"), state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `minBpmToday never widens the threshold above restingBpmBaseline`() {
        // Faux positif réel du 2026-08-12 : minBpmToday figé à 71 dès la
        // première lecture de la soirée (encore élevée, loin du sommeil
        // réel), restingBpmBaseline calibré à 65. Sans plancher, le seuil
        // serait 71 * 1.12 = 79,52 -> 79 bpm déclencherait le bonus. Avec le
        // plancher (minOf), le seuil retombe à 65 * 1.12 = 72,8 -> 79 bpm ne
        // doit PAS déclencher le bonus.
        val state = stateWithScore(0.30f, minBpmToday = 71)
            .copy(restingBpmBaseline = 65, bpmBelowBaselineSince = sustainedSince())

        val score = analyzer.analyze(FoxBrainEvent.HeartRateReceived(bpm = 79f, source = "TEST"), state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
    }

    // --- MovementDetected ---

    @Test
    fun `significant movement decreases probability`() {
        val state = stateWithScore(0.50f)

        // Au-delà du seuil recalibré (2.0, voir SleepScoringConfig) après la
        // nuit de test du 2026-08-08 — 2.5 correspond à un vrai changement
        // de position, pas un micro-ajustement du poignet.
        val score = analyzer.analyze(FoxBrainEvent.MovementDetected(magnitude = 2.5f), state)

        assertEquals(0.50f - config.significantMovementPenalty, score.sleepProbability, 0.0001f)
        assertEquals("Mouvement important détecté", score.reason)
    }

    @Test
    fun `movement at threshold boundary does not penalize`() {
        val state = stateWithScore(0.50f)

        // Comparaison stricte (>) : magnitude == threshold ne pénalise pas.
        val score = analyzer.analyze(FoxBrainEvent.MovementDetected(magnitude = config.movementThreshold), state)

        assertEquals(0.50f, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `minor movement does not penalize`() {
        val state = stateWithScore(0.50f)

        val score = analyzer.analyze(FoxBrainEvent.MovementDetected(magnitude = 0.1f), state)

        assertEquals(0.50f, score.sleepProbability, 0.0001f)
    }

    // --- StillnessDetected ---

    @Test
    fun `stillness increases probability only when tv is on`() {
        val state = stateWithScore(0.50f, tvConnected = true)

        val score = analyzer.analyze(FoxBrainEvent.StillnessDetected, state)

        assertEquals(0.50f + config.stationaryDurationBonus, score.sleepProbability, 0.0001f)
        assertEquals("Utilisateur immobile (5 min+)", score.reason)
    }

    @Test
    fun `stillness does not increase probability when tv is off`() {
        val state = stateWithScore(0.50f, tvConnected = false)

        val score = analyzer.analyze(FoxBrainEvent.StillnessDetected, state)

        assertEquals(0.50f, score.sleepProbability, 0.0001f)
    }

    // --- WalkingDetected / SleepCancelled : reset immédiat ---

    @Test
    fun `walking detected resets probability to zero regardless of prior score`() {
        val state = stateWithScore(0.95f)

        val score = analyzer.analyze(FoxBrainEvent.WalkingDetected, state)

        assertEquals(0f, score.sleepProbability, 0.0001f)
        assertEquals("Utilisateur actif (Marche)", score.reason)
    }

    @Test
    fun `sleep cancelled resets probability to zero regardless of prior score`() {
        val state = stateWithScore(0.95f)

        val score = analyzer.analyze(FoxBrainEvent.SleepCancelled, state)

        assertEquals(0f, score.sleepProbability, 0.0001f)
        assertEquals("Sommeil annulé par l'utilisateur", score.reason)
    }

    // --- TV events ---

    @Test
    fun `tv turned off halves the current probability`() {
        val state = stateWithScore(0.60f)

        val score = analyzer.analyze(FoxBrainEvent.TVTurnedOff, state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
        assertEquals("TV éteinte", score.reason)
    }

    @Test
    fun `tv turned on adds a small bonus`() {
        val state = stateWithScore(0f)

        val score = analyzer.analyze(FoxBrainEvent.TVTurnedOn, state)

        assertEquals(config.tvOnBonus, score.sleepProbability, 0.0001f)
        assertEquals("TV allumée", score.reason)
    }

    // --- ScreenUnlocked ---

    @Test
    fun `screen unlocked penalizes probability`() {
        val state = stateWithScore(0.50f)

        val score = analyzer.analyze(FoxBrainEvent.ScreenUnlocked, state)

        assertEquals(0.50f - config.userInteractionPenalty, score.sleepProbability, 0.0001f)
        assertEquals("Interaction utilisateur (Écran déverrouillé)", score.reason)
    }

    // --- TimeChanged ---

    private fun instantAtHour(hour: Int) =
        ZonedDateTime.of(2026, 8, 7, hour, 0, 0, 0, ZoneId.systemDefault()).toInstant()

    @Test
    fun `late night hour boundary (23h) grants late night bonus`() {
        val state = stateWithScore(0.30f)

        val score = analyzer.analyze(FoxBrainEvent.TimeChanged(instantAtHour(23)), state)

        assertEquals(0.30f + config.lateNightBonus, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `hour just before late night boundary (22h) grants no bonus`() {
        val state = stateWithScore(0.30f)

        val score = analyzer.analyze(FoxBrainEvent.TimeChanged(instantAtHour(22)), state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `early morning hour (4h) grants late night bonus`() {
        val state = stateWithScore(0.30f)

        val score = analyzer.analyze(FoxBrainEvent.TimeChanged(instantAtHour(4)), state)

        assertEquals(0.30f + config.lateNightBonus, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `morning hour boundary (5h) grants no bonus`() {
        val state = stateWithScore(0.30f)

        val score = analyzer.analyze(FoxBrainEvent.TimeChanged(instantAtHour(5)), state)

        assertEquals(0.30f, score.sleepProbability, 0.0001f)
    }

    // --- Clamping [0, 1] ---

    @Test
    fun `probability never exceeds one`() {
        val state = stateWithScore(0.99f)

        val score = analyzer.analyze(FoxBrainEvent.TVTurnedOn, state)

        assertEquals(1f, score.sleepProbability, 0.0001f)
    }

    @Test
    fun `probability never goes below zero`() {
        val state = stateWithScore(0.05f)

        val score = analyzer.analyze(FoxBrainEvent.MovementDetected(magnitude = 3f), state)

        assertEquals(0f, score.sleepProbability, 0.0001f)
    }

    // --- Comportement par défaut ---

    @Test
    fun `confidence is always fixed regardless of event type`() {
        val state = stateWithScore(0.5f)

        val scores = listOf(
            analyzer.analyze(FoxBrainEvent.TVTurnedOn, state),
            analyzer.analyze(FoxBrainEvent.WalkingDetected, state),
            analyzer.analyze(FoxBrainEvent.StillnessDetected, state)
        )

        scores.forEach { assertEquals(0.85f, it.confidence, 0.0001f) }
    }

    @Test
    fun `unhandled events leave probability and reason unchanged`() {
        val state = stateWithScore(0.42f, reason = "raison précédente")

        val score = analyzer.analyze(FoxBrainEvent.BatteryChanged(level = 80, isCharging = false), state)

        assertEquals(0.42f, score.sleepProbability, 0.0001f)
        assertEquals("raison précédente", score.reason)
    }

    // --- Configuration personnalisée ---

    @Test
    fun `custom config changes bonus magnitude`() {
        val customAnalyzer = WeightedSleepAnalyzer(SleepScoringConfig(tvOnBonus = 0.5f))
        val state = stateWithScore(0f)

        val score = customAnalyzer.analyze(FoxBrainEvent.TVTurnedOn, state)

        assertEquals(0.5f, score.sleepProbability, 0.0001f)
    }
}
