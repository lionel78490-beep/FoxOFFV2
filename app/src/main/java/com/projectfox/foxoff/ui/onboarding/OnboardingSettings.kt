package com.projectfox.foxoff.ui.onboarding

import android.content.Context

/**
 * Persiste si l'onboarding a déjà été terminé, pour que MainActivity ne le
 * relance plus à chaque démarrage de l'app.
 *
 * Volontairement EN CLAIR (pas de FoxEncryptedPrefs) : c'est le tout premier
 * réglage lu, de façon synchrone, dès la première composition de
 * MainActivity (`remember { !OnboardingSettings.isCompleted(...) }`) — avant
 * même que FoxApplication.onCreate() ait fini d'amorcer le cache chiffré.
 * Régression réelle constatée le 2026-08-12 : un simple booléen, jamais
 * sensible, forçait la génération synchrone de la clé maître Android
 * Keystore sur le thread principal au tout premier lancement (3-4 minutes
 * de blocage observées après réinstallation). Voir FoxEncryptedPrefs pour
 * les données réellement sensibles (BPM, sommeil, identifiants montre/TV),
 * qui restent chiffrées.
 */
object OnboardingSettings {

    private const val PREFS_NAME = "fox_onboarding_settings"
    private const val KEY_COMPLETED = "onboarding_completed"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompleted(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_COMPLETED, false)
    }

    fun markCompleted(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    /**
     * Réinitialise l'onboarding pour pouvoir le retester sans réinstaller
     * l'application. Destiné à être appelé depuis l'écran Réglages.
     */
    fun reset(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED, false).apply()
    }
}
