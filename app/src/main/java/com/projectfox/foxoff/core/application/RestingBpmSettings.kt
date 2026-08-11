package com.projectfox.foxoff.core.application

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mémorise la référence BPM de repos calibrée depuis Health Connect (voir
 * HealthConnectBaselineProvider) — remplace la valeur générique par défaut
 * (70 bpm, voir FoxBrainState.restingBpmBaseline) une fois calculée depuis
 * les vraies données de l'utilisateur.
 */
object RestingBpmSettings {

    private const val PREFS_NAME = "fox_resting_bpm_settings"
    private const val KEY_CALIBRATED_BPM = "calibrated_resting_bpm"

    // Miroir réactif — même principe que WatchSettings/BackgroundServiceSettings.
    private val _calibratedBpm = MutableStateFlow<Int?>(null)
    val calibratedBpm: StateFlow<Int?> = _calibratedBpm.asStateFlow()

    fun saveCalibratedBpm(context: Context, bpm: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CALIBRATED_BPM, bpm)
            .apply()
        _calibratedBpm.value = bpm
    }

    /** À appeler au démarrage pour synchroniser le miroir réactif. */
    fun getCalibratedBpm(context: Context): Int? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = if (prefs.contains(KEY_CALIBRATED_BPM)) prefs.getInt(KEY_CALIBRATED_BPM, 0) else null
        _calibratedBpm.value = value
        return value
    }
}
