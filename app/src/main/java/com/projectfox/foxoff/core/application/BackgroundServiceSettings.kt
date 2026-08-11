package com.projectfox.foxoff.core.application

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Réglage utilisateur pour la surveillance en arrière-plan (tâche #13 —
 * service de premier plan). Désactivé par défaut.
 *
 * Ne contrôle que la persistance de cette intention utilisateur — la pause
 * TV automatique liée au sommeil (FoxCore.shouldSendAutoPause) est un
 * comportement central non désactivable, indépendant de ce réglage.
 */
object BackgroundServiceSettings {

    private const val PREFS_NAME = "fox_background_service_settings"
    private const val KEY_ENABLED = "background_surveillance_enabled"
    private const val KEY_PROMPT_SEEN = "background_monitoring_prompt_seen"

    // Miroir réactif de isEnabled(), tenu à jour à CHAQUE lecture ET écriture
    // (voir isEnabled()/setEnabled() ci-dessous). Un composable qui
    // l'observe via collectAsState() reflète immédiatement un changement
    // décidé ailleurs (FoxForegroundService.failAndStop(),
    // FoxServiceReconciliation...) — contrairement à un `remember` local,
    // lu une seule fois à la composition initiale, qui avait déjà causé ce
    // même bug (l'écran Réglages ne se mettait à jour qu'en le quittant et
    // en y revenant).
    private val _enabledState = MutableStateFlow(false)
    val enabledState: StateFlow<Boolean> = _enabledState.asStateFlow()

    fun isEnabled(context: Context): Boolean {
        val value = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        _enabledState.value = value
        return value
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        _enabledState.value = enabled
    }

    /**
     * L'utilisateur a-t-il déjà répondu explicitement à la proposition de
     * surveillance en arrière-plan (section de l'écran Permissions pour un
     * nouvel utilisateur, ou boîte de dialogue ponctuelle sur le Dashboard
     * pour un utilisateur existant) ? Marqué seulement sur un choix
     * explicite ("Activer la surveillance" ou "Plus tard"), jamais en
     * continuant l'onboarding sans y toucher.
     */
    fun hasSeenPrompt(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMPT_SEEN, false)
    }

    fun markPromptSeen(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROMPT_SEEN, true)
            .apply()
    }
}
