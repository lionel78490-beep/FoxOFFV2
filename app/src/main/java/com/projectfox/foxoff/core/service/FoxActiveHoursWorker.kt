package com.projectfox.foxoff.core.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectfox.foxoff.core.logging.FoxLogger
import java.util.concurrent.TimeUnit

/**
 * Seul déclencheur de FoxServiceReconciliation.reconcileNow() qui ne dépend
 * PAS d'un retour au premier plan de l'application (ProcessLifecycleOwner)
 * ni d'un redémarrage (BOOT_COMPLETED) — sans lui, un créneau horaire
 * (ActiveHoursSettings) ne pourrait jamais démarrer/arrêter la surveillance
 * tout seul : l'utilisateur devrait rouvrir l'app pile à l'heure du
 * créneau, ce qui est exactement le problème à résoudre ("tourne du matin
 * au soir").
 *
 * 15 minutes = intervalle minimum autorisé par WorkManager pour du travail
 * périodique — suffisant ici, la précision à la minute près n'a pas
 * d'intérêt pour ce cas d'usage.
 */
class FoxActiveHoursWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val UNIQUE_WORK_NAME = "fox_active_hours_reconciliation"
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FoxActiveHoursWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        FoxLogger.i("FOX-ACTIVE-HOURS | Vérification périodique du créneau horaire")
        FoxServiceReconciliation.reconcileNow(applicationContext)
        return Result.success()
    }
}
