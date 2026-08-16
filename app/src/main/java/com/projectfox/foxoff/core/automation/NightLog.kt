package com.projectfox.foxoff.core.automation

import android.content.Context
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Journal chronologique des événements pertinents pour diagnostiquer une
 * nuit (onglet "Historique") — but explicite : donner en un coup d'œil ce
 * qui s'est passé, plutôt que de devoir décrire la nuit à la main. Pensé
 * pour être lu le lendemain matin après un test réel, pas pour du debug
 * technique bas niveau (voir FoxLogger pour ça).
 *
 * Stockage JSON dans SharedPreferences, même pattern que
 * SleepDetectionHistory — borné à MAX_ENTRIES, ajouté uniquement en fin.
 */
enum class NightLogType {
    HEART_RATE_TREND,
    MOVEMENT,
    SLEEP_STATE_CHANGE,
    TV_ON,
    TV_OFF,
    PAUSE_COUNTDOWN_STARTED,
    PAUSE_EXECUTED,
    PAUSE_CANCELLED,
    // Extinction automatique (2026-08-16) : 10 min après PAUSE_EXECUTED
    // sans interaction manuelle avec la télécommande FoxOFF — voir
    // TvAutoPowerOffCoordinator/FoxForegroundService.
    AUTO_POWER_OFF,
    WATCH_CONNECTED,
    WATCH_DISCONNECTED
}

data class NightLogEntry(
    val timestamp: Instant,
    val type: NightLogType,
    val detail: String
)

object NightLog {

    private const val PREFS_NAME = "fox_night_log"
    private const val KEY_ENTRIES = "entries"

    // ~8h à raison d'un mouvement/30s (le plus fréquent des types loggés)
    // + marge pour les autres événements — largement suffisant pour une
    // nuit complète sans grossir indéfiniment.
    private const val MAX_ENTRIES = 2000

    private val _entries = MutableStateFlow<List<NightLogEntry>>(emptyList())
    val entries: StateFlow<List<NightLogEntry>> = _entries.asStateFlow()

    fun record(context: Context, type: NightLogType, detail: String) {
        val entry = NightLogEntry(Instant.now(), type, detail)
        val updated = (loadFromPrefs(context) + entry).takeLast(MAX_ENTRIES)
        saveToPrefs(context, updated)
        _entries.value = updated
    }

    /** Charge l'historique persisté et synchronise le miroir réactif — à appeler au démarrage. */
    fun loadAll(context: Context): List<NightLogEntry> {
        val loaded = loadFromPrefs(context)
        _entries.value = loaded
        return loaded
    }

    /** Repart de zéro avant une nouvelle nuit de test — geste explicite de l'utilisateur. */
    fun clear(context: Context) {
        saveToPrefs(context, emptyList())
        _entries.value = emptyList()
    }

    private fun loadFromPrefs(context: Context): List<NightLogEntry> {
        val raw = com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                NightLogEntry(
                    timestamp = Instant.parse(obj.getString("timestamp")),
                    type = NightLogType.valueOf(obj.getString("type")),
                    detail = obj.getString("detail")
                )
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-NIGHT-LOG | Échec lecture, journal réinitialisé", e)
            emptyList()
        }
    }

    private fun saveToPrefs(context: Context, entries: List<NightLogEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestamp", entry.timestamp.toString())
                    .put("type", entry.type.name)
                    .put("detail", entry.detail)
            )
        }
        com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }
}
