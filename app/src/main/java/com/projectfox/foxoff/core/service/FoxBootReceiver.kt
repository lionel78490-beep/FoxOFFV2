package com.projectfox.foxoff.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectfox.foxoff.core.logging.FoxLogger

/**
 * Redémarre la surveillance en arrière-plan après un redémarrage du
 * téléphone, SI ET SEULEMENT SI l'intention persistée est activée. Ne
 * duplique aucune décision : délègue entièrement à
 * FoxServiceReconciliation.reconcileNow(), la même réconciliation testée
 * (voir ServiceReconciliationDecision) déjà utilisée au retour de l'app au
 * premier plan — BOOT_COMPLETED n'est qu'un déclencheur de plus pour la
 * même logique.
 *
 * BOOT_COMPLETED est un broadcast système protégé et fait partie des
 * exemptions explicites d'Android aux restrictions de démarrage de service
 * en arrière-plan : démarrer FoxForegroundService depuis ce receiver reste
 * autorisé même si l'app n'a pas été ouverte depuis le redémarrage (voir
 * AndroidManifest.xml).
 */
class FoxBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        FoxLogger.i("FOX-BOOT | BOOT_COMPLETED reçu -> réconciliation")
        FoxServiceReconciliation.reconcileNow(context)
    }
}
