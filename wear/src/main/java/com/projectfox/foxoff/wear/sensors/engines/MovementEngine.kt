package com.projectfox.foxoff.wear.sensors.engines

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.projectfox.foxoff.wear.core.FoxWearLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.sqrt

/**
 * Détecte le mouvement via l'accéléromètre (TYPE_LINEAR_ACCELERATION — la
 * gravité est déjà retirée par le capteur, pas besoin de la soustraire à la
 * main). Agrège en RMS sur des fenêtres de WINDOW_MS avant d'émettre, plutôt
 * que chaque échantillon brut (haute fréquence, bruyant) — donne directement
 * une magnitude comparable au seuil de FoxBrainEvent.MovementDetected
 * (voir SleepScoringConfig.movementThreshold).
 *
 * Corrige un faux positif réel observé le 2026-08-07 : sans AUCUN signal de
 * mouvement en production, le score de sommeil ne pouvait que monter (BPM
 * bas + TV allumée), jamais redescendre en cas d'activité réelle (repas,
 * lecture...) — voir ROADMAP.md Phase 1/5, SleepScoringConfig
 * .sustainedBpmDropDuration pour l'autre moitié du correctif.
 *
 * `movementThreshold` (0.5 par défaut, voir SleepScoringConfig) est une
 * estimation raisonnable mais NON calibrée sur matériel réel — aucun signal
 * de mouvement n'existait avant ce correctif, donc aucune donnée réelle
 * pour la valider. À ajuster si le seuil se révèle trop sensible (pénalise
 * une immobilité normale) ou pas assez (ne détecte pas un vrai mouvement)
 * une fois testé.
 *
 * Mode basse fréquence (voir setLowPowerMode) : une fois la TV en pause
 * (sommeil confirmé), inutile de remonter une magnitude toutes les 30s —
 * seul un VRAI changement de position (réveil) mérite d'être vu. Élargir
 * la fenêtre d'agrégation à 10 min réduit d'autant la fréquence d'envoi
 * Bluetooth (voir sendMovement() dans FoxWearCore.initialize()), qui est
 * le vrai poste de consommation ici — le capteur lui-même continue
 * d'échantillonner à la même cadence matérielle (SENSOR_DELAY_NORMAL),
 * seule l'émission/transmission est espacée.
 */
class MovementEngine(context: Context) {

    companion object {
        private const val NORMAL_WINDOW_MS = 30_000L
        private const val LOW_POWER_WINDOW_MS = 10 * 60_000L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val _magnitude = MutableSharedFlow<Float>(replay = 0, extraBufferCapacity = 4)
    val magnitude: SharedFlow<Float> = _magnitude.asSharedFlow()

    @Volatile
    private var windowMs = NORMAL_WINDOW_MS

    private var windowSumSquares = 0.0
    private var windowSampleCount = 0
    private var windowStartMs = 0L

    var lastError: String? = null
        private set

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val instantMagnitude = sqrt(
                (event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]).toDouble()
            )
            windowSumSquares += instantMagnitude * instantMagnitude
            windowSampleCount++

            val now = System.currentTimeMillis()
            if (now - windowStartMs >= windowMs) {
                if (windowSampleCount > 0) {
                    val rms = sqrt(windowSumSquares / windowSampleCount).toFloat()
                    _magnitude.tryEmit(rms)
                }
                windowSumSquares = 0.0
                windowSampleCount = 0
                windowStartMs = now
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun isSupported(): Boolean {
        val supported = accelerometer != null
        if (!supported) lastError = "TYPE_LINEAR_ACCELERATION non disponible sur cet appareil"
        return supported
    }

    fun start() {
        val sensor = accelerometer
        if (sensor == null) {
            lastError = "Capteur indisponible"
            return
        }
        windowStartMs = System.currentTimeMillis()
        windowSumSquares = 0.0
        windowSampleCount = 0
        val registered = sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
        lastError = if (registered) null else "Échec registerListener"
        FoxWearLogger.i("FOX-WEAR-MOVEMENT | Démarré (registered=$registered)")
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
        FoxWearLogger.i("FOX-WEAR-MOVEMENT | Arrêté")
    }

    /**
     * Bascule la fenêtre d'agrégation entre normale (30s) et basse
     * fréquence (10min) — voir /foxoff/movement_low_power côté
     * FoxWearListenerService. N'arrête pas le capteur (registerListener
     * reste actif) : seule la cadence d'émission change.
     */
    fun setLowPowerMode(enabled: Boolean) {
        windowMs = if (enabled) LOW_POWER_WINDOW_MS else NORMAL_WINDOW_MS
        FoxWearLogger.i("FOX-WEAR-MOVEMENT | Mode basse fréquence = $enabled (fenêtre ${windowMs / 1000}s)")
    }
}
