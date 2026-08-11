package com.projectfox.foxoff.ui.onboarding

import android.content.Context

/**
 * Persiste si l'onboarding a déjà été terminé, pour que MainActivity ne le
 * relance plus à chaque démarrage de l'app.
 */
object OnboardingSettings {

    private const val PREFS_NAME = "fox_onboarding_settings"
    private const val KEY_COMPLETED = "onboarding_completed"

    fun isCompleted(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETED, false)
    }

    fun markCompleted(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETED, true)
            .apply()
    }

    /**
     * Réinitialise l'onboarding pour pouvoir le retester sans réinstaller
     * l'application. Destiné à être appelé depuis l'écran Réglages.
     */
    fun reset(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETED, false)
            .apply()
    }
}
