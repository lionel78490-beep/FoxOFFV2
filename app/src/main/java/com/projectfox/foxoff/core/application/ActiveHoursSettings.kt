package com.projectfox.foxoff.core.application

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Créneaux horaires prédéfinis proposés à l'utilisateur (page d'installation
 * + Réglages) — demande explicite : la surveillance tournait "du matin au
 * soir" alors qu'elle n'a d'utilité que pour l'endormissement. Créneaux fixes
 * plutôt qu'un sélecteur d'heure libre, pour rester simple à l'installation.
 *
 * `EVENING_NIGHT` traverse minuit (20h -> 8h le lendemain) — voir
 * containsHour() ci-dessous pour la gestion de ce cas.
 */
enum class ActiveHoursSlot(val label: String, val startHour: Int, val endHour: Int) {
    MORNING("8h – 12h", 8, 12),
    MIDDAY("12h – 15h", 12, 15),
    AFTERNOON("15h – 20h", 15, 20),
    EVENING_NIGHT("20h – 8h", 20, 8),
}

/**
 * Réglage utilisateur : dans quel(s) créneau(x) la surveillance en
 * arrière-plan (BackgroundServiceSettings) a le droit de tourner. Persisté
 * comme un ensemble de noms d'enum (StringSet) — aucun créneau sélectionné
 * = aucune restriction (comportement historique conservé, zéro régression
 * pour un utilisateur qui n'a jamais configuré ce réglage).
 *
 * Ne contrôle que la FENÊTRE de fonctionnement — pas d'effet si
 * BackgroundServiceSettings.isEnabled() est false, voir
 * FoxServiceReconciliation.reconcileNow() qui combine les deux.
 */
object ActiveHoursSettings {

    private const val PREFS_NAME = "fox_active_hours_settings"
    private const val KEY_SELECTED_SLOTS = "active_hours_selected_slots"

    // Même pattern miroir réactif que BackgroundServiceSettings.enabledState.
    private val _selectedSlotsState = MutableStateFlow<Set<ActiveHoursSlot>>(emptySet())
    val selectedSlotsState: StateFlow<Set<ActiveHoursSlot>> = _selectedSlotsState.asStateFlow()

    fun getSelectedSlots(context: Context): Set<ActiveHoursSlot> {
        val stored = com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .getStringSet(KEY_SELECTED_SLOTS, emptySet()) ?: emptySet()
        val slots = stored.mapNotNull { name ->
            runCatching { ActiveHoursSlot.valueOf(name) }.getOrNull()
        }.toSet()
        _selectedSlotsState.value = slots
        return slots
    }

    fun setSelectedSlots(context: Context, slots: Set<ActiveHoursSlot>) {
        com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .edit()
            .putStringSet(KEY_SELECTED_SLOTS, slots.map { it.name }.toSet())
            .apply()
        _selectedSlotsState.value = slots
    }

    /**
     * L'utilisateur a-t-il déjà vu la page de choix des créneaux à
     * l'installation ? Distinct d'un ensemble vide de créneaux (qui reste un
     * choix valide : "aucune restriction") — évite de réafficher la page à
     * chaque lancement tant qu'aucun choix explicite n'a été fait.
     */
    fun hasSeenPrompt(context: Context): Boolean {
        return com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .getBoolean("active_hours_prompt_seen", false)
    }

    fun markPromptSeen(context: Context) {
        com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .edit()
            .putBoolean("active_hours_prompt_seen", true)
            .apply()
    }

    /**
     * Décision PURE (testable sans Android) : l'heure courante tombe-t-elle
     * dans au moins un des créneaux sélectionnés ? Ensemble vide = pas de
     * restriction configurée -> toujours autorisé.
     */
    fun isWithinSelectedSlots(hour: Int, slots: Set<ActiveHoursSlot>): Boolean {
        if (slots.isEmpty()) return true
        return slots.any { containsHour(it, hour) }
    }

    private fun containsHour(slot: ActiveHoursSlot, hour: Int): Boolean {
        return if (slot.startHour < slot.endHour) {
            hour in slot.startHour until slot.endHour
        } else {
            // Créneau traversant minuit (ex: 20h -> 8h) : actif si l'heure est
            // après le début OU avant la fin, jamais "entre les deux".
            hour >= slot.startHour || hour < slot.endHour
        }
    }
}
