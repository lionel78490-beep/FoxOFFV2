package com.projectfox.foxoff.core.application

import android.content.Context
import com.projectfox.foxoff.core.watch.WatchBrand

/**
 * Mémorise le nom de la dernière montre associée avec succès, pour pouvoir
 * l'afficher comme "connue" (Hors ligne) pendant la reconnexion, plutôt que
 * de repartir sur un état "Aucune montre" au redémarrage de l'app. Mémorise
 * aussi la marque active (WatchBrand) — WEAR_OS par défaut pour ne rien
 * changer au comportement des installations existantes.
 */
object WatchSettings {

    private const val PREFS_NAME = "fox_watch_settings"
    private const val KEY_LAST_KNOWN_NAME = "last_known_watch_name"
    private const val KEY_LAST_KNOWN_NODE_ID = "last_known_watch_node_id"
    private const val KEY_WATCH_BRAND = "watch_brand"

    fun saveLastKnownWatch(context: Context, name: String) {
        if (name.isBlank()) return
        com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .edit()
            .putString(KEY_LAST_KNOWN_NAME, name)
            .apply()
    }

    fun getLastKnownWatchName(context: Context): String? {
        return com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .getString(KEY_LAST_KNOWN_NAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Identifiant du nœud Wearable Data Layer correspondant à la montre
     * actuellement reconnue — permet à PhoneWearListenerService de
     * distinguer une déconnexion de LA montre active d'une déconnexion
     * d'un autre nœud Wear sans rapport.
     */
    fun saveLastKnownNodeId(context: Context, nodeId: String) {
        if (nodeId.isBlank()) return
        com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .edit()
            .putString(KEY_LAST_KNOWN_NODE_ID, nodeId)
            .apply()
    }

    fun getLastKnownNodeId(context: Context): String? {
        return com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .getString(KEY_LAST_KNOWN_NODE_ID, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun saveWatchBrand(context: Context, brand: WatchBrand) {
        com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .edit()
            .putString(KEY_WATCH_BRAND, brand.name)
            .apply()
    }

    /** WEAR_OS par défaut : préserve le comportement des installations existantes. */
    fun getWatchBrand(context: Context): WatchBrand {
        val raw = com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .getString(KEY_WATCH_BRAND, null)
        return raw?.let { runCatching { WatchBrand.valueOf(it) }.getOrNull() } ?: WatchBrand.WEAR_OS
    }

    /**
     * Efface l'appareil connu (nom + identifiant), sans toucher à la marque
     * sélectionnée — à appeler lors d'un changement de marque de montre
     * (voir SettingsScreen "Changer de montre") pour ne pas afficher le nom
     * de l'ancienne montre pendant qu'on en appaire une nouvelle.
     */
    fun clearKnownDevice(context: Context) {
        com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .edit()
            .remove(KEY_LAST_KNOWN_NAME)
            .remove(KEY_LAST_KNOWN_NODE_ID)
            .apply()
    }
}
