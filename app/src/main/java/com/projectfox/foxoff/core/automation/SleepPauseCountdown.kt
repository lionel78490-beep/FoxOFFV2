package com.projectfox.foxoff.core.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Compte à rebours ANNULABLE avant une pause TV automatique liée au
 * sommeil : laisse à l'utilisateur un délai pour annuler (ex: bouton
 * "Annuler" sur une notification) avant que l'action ne s'exécute
 * réellement. Un seul compte à rebours à la fois — start() est idempotent :
 * un déclenchement pendant qu'un compte à rebours est déjà en cours ne
 * relance pas un second minuteur (évite une double pause si l'état ASLEEP
 * se répète pendant la fenêtre d'annulation).
 *
 * Voir SleepPauseCoordinator pour le câblage production (notification
 * réelle, TvController) — cette classe reste pure et testable avec un
 * scope virtuel (voir SleepPauseCountdownTest).
 */
class SleepPauseCountdown(
    private val countdownDuration: Duration,
    private val scope: CoroutineScope,
    private val onCountdownStarted: suspend () -> Unit = {},
    private val onExecute: suspend () -> Unit,
    private val onCancelled: suspend () -> Unit = {}
) {
    private var job: Job? = null

    val isPending: Boolean get() = job?.isActive == true

    /** Démarre le compte à rebours. Idempotent : ignoré si déjà en cours. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            onCountdownStarted()
            delay(countdownDuration.toMillis())
            onExecute()
        }
    }

    /** Annule le compte à rebours en cours, s'il y en a un. Sans effet sinon. */
    fun cancel() {
        val wasPending = job?.isActive == true
        job?.cancel()
        job = null
        if (wasPending) {
            scope.launch { onCancelled() }
        }
    }
}
