package com.projectfox.foxoff.core.automation

import android.content.Context
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class SleepDetectionOutcome { PAUSED, CANCELLED }

data class SleepDetectionRecord(
    val detectedAt: Instant,
    val sleepProbability: Float,
    val confidence: Float,
    val outcome: SleepDetectionOutcome
)

/**
 * Historique local des détections d'endormissement par FoxOFF (heure,
 * score, confiance, pause effectuée ou annulée par l'utilisateur) — brique
 * nécessaire pour comparer plus tard avec les sessions de sommeil Samsung
 * Health (via Health Connect, étape suivante). Ne contient QUE les propres
 * décisions de FoxOFF, aucune donnée de santé externe.
 *
 * Stockage volontairement simple (JSON dans SharedPreferences, borné à
 * MAX_RECORDS) : une liste de cette taille, ajoutée uniquement en fin et
 * jamais requêtée finement, ne justifie pas d'introduire Room.
 */
object SleepDetectionHistory {

    private const val PREFS_NAME = "fox_sleep_detection_history"
    private const val KEY_RECORDS = "records"

    // ~2 mois à raison d'une détection par nuit — largement suffisant pour
    // calibrer, sans laisser le stockage grossir indéfiniment.
    private const val MAX_RECORDS = 60

    private val _records = MutableStateFlow<List<SleepDetectionRecord>>(emptyList())
    val records: StateFlow<List<SleepDetectionRecord>> = _records.asStateFlow()

    /** Ajoute un enregistrement et met à jour le miroir réactif. */
    fun record(context: Context, entry: SleepDetectionRecord) {
        val updated = (loadFromPrefs(context) + entry).takeLast(MAX_RECORDS)
        saveToPrefs(context, updated)
        _records.value = updated
        FoxLogger.i(
            "FOX-SLEEP | Historique | ${entry.outcome} à ${entry.detectedAt} " +
                    "(score=${entry.sleepProbability}, confiance=${entry.confidence})"
        )
    }

    /** Charge l'historique persisté et synchronise le miroir réactif — à appeler au démarrage. */
    fun loadAll(context: Context): List<SleepDetectionRecord> {
        val loaded = loadFromPrefs(context)
        _records.value = loaded
        return loaded
    }

    private fun loadFromPrefs(context: Context): List<SleepDetectionRecord> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                SleepDetectionRecord(
                    detectedAt = Instant.parse(obj.getString("detectedAt")),
                    sleepProbability = obj.getDouble("sleepProbability").toFloat(),
                    confidence = obj.getDouble("confidence").toFloat(),
                    outcome = SleepDetectionOutcome.valueOf(obj.getString("outcome"))
                )
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-SLEEP | Historique | Échec lecture, historique réinitialisé", e)
            emptyList()
        }
    }

    private fun saveToPrefs(context: Context, entries: List<SleepDetectionRecord>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("detectedAt", entry.detectedAt.toString())
                    .put("sleepProbability", entry.sleepProbability.toDouble())
                    .put("confidence", entry.confidence.toDouble())
                    .put("outcome", entry.outcome.name)
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS, array.toString())
            .apply()
    }
}
