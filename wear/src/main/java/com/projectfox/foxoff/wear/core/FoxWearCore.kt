package com.projectfox.foxoff.wear.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.projectfox.foxoff.wear.communication.WearCommunicationManager
import com.projectfox.foxoff.wear.communication.model.ConnectionState
import com.projectfox.foxoff.wear.sensors.api.WearSensorEngine
import com.projectfox.foxoff.wear.sensors.engines.HealthServicesEngine
import com.projectfox.foxoff.wear.sensors.engines.LegacyEngine
import com.projectfox.foxoff.wear.sensors.engines.MovementEngine
import com.projectfox.foxoff.wear.sensors.engines.PassiveMonitoringEngine
import com.projectfox.foxoff.wear.sensors.engines.exercise.ExerciseEngine
import com.projectfox.foxoff.wear.sensors.model.WearHeartRateSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Diagnostic TEMPORAIRE du moteur passif (voir WearHomeScreen) — permet de
 * comprendre pourquoi aucun BPM n'arrive écran éteint sans dépendre d'ADB.
 * À retirer une fois ce comportement validé sur matériel réel.
 */
data class PassiveEngineDiagnostic(
    val supported: Boolean? = null,
    val started: Boolean = false,
    val lastSampleAt: Instant? = null,
    val lastError: String? = null
)

/**
 * Orchestrator for the FoxOFF Wear application.
 */
class FoxWearCore(private val context: Context) {

    companion object {
        // Tentatives limitées avec délai progressif si aucun téléphone n'est
        // encore joignable au démarrage — plutôt que d'attendre jusqu'à 60s
        // (le cycle périodique normal, voir init{}) avant le premier essai.
        private const val STARTUP_RETRY_MAX_ATTEMPTS = 5
        private const val STARTUP_RETRY_INITIAL_DELAY_MS = 3_000L
        private const val STARTUP_RETRY_MAX_DELAY_MS = 30_000L
        private const val STARTUP_RETRY_MULTIPLIER = 2.0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: WearSensorEngine? = null

    // Filet de sécurité écran éteint : ExerciseClient/MeasureClient (moteur
    // "actif" ci-dessus) ne garantissent PAS la livraison quand l'écran est
    // noir — c'est justement le rôle de PassiveMonitoringClient, l'API
    // officielle pour la surveillance en arrière-plan. Démarré EN PLUS du
    // moteur actif (pas à sa place) : les deux canaux sont indépendants et
    // routent vers le même pipeline (voir sendPassiveSample()).
    private var passiveEngine: PassiveMonitoringEngine? = null

    private val _passiveDiagnostic = MutableStateFlow(PassiveEngineDiagnostic())
    val passiveDiagnostic: StateFlow<PassiveEngineDiagnostic> = _passiveDiagnostic.asStateFlow()

    // Détection de mouvement (accéléromètre) — voir MovementEngine. Corrige
    // l'absence totale de contre-signal au score de sommeil (faux positif
    // du 2026-08-07).
    private val movementEngine = MovementEngine(context)

    private val communicationManager = WearCommunicationManager(context)
    
    private val _status = MutableSharedFlow<String>(replay = 1)
    val status: Flow<String> = _status.asSharedFlow()

    private val _isMonitoring = MutableSharedFlow<Boolean>(replay = 1)
    val isMonitoring: Flow<Boolean> = _isMonitoring.asSharedFlow()

    private val _isForegroundServiceActive = MutableStateFlow(false)
    val isForegroundServiceActive: StateFlow<Boolean> = _isForegroundServiceActive.asStateFlow()

    val connectionState: Flow<ConnectionState> = communicationManager.connectionState

    private val _samples = MutableSharedFlow<WearHeartRateSample>(replay = 1, extraBufferCapacity = 64)
    val heartRateSamples: Flow<WearHeartRateSample> = _samples.asSharedFlow()

    // Même valeur que celle envoyée au téléphone (voir sendDeviceInfo()) —
    // évite une seconde lecture BatteryManager indépendante côté UI, qui
    // pourrait légèrement diverger de ce qui est réellement communiqué.
    private val _batteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    init {
        _status.tryEmit("READY")
        _isMonitoring.tryEmit(false)
        
        // Start periodic info sending
        scope.launch {
            while (true) {
                sendDeviceInfo()
                delay(60000) // Every 1 minute
            }
        }
    }

    /** @return true si un nœud téléphone était joignable (envoi tenté). */
    private suspend fun sendDeviceInfo(): Boolean {
        FoxWearLogger.d("FOX-WEAR | Collecte et envoi des infos de la montre au téléphone...")
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val batteryPct: Int = batteryStatus?.let { intent ->
            val level: Int = intent.getIntOfExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntOfExtra(BatteryManager.EXTRA_SCALE, -1)
            (level * 100 / scale.toFloat()).toInt()
        } ?: 0
        _batteryPercent.value = batteryPct

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        return communicationManager.sendWatchInfo(deviceName, batteryPct)
    }

    /**
     * Au démarrage, si aucun nœud téléphone n'est encore joignable (l'app
     * téléphone n'a pas fini de démarrer, Bluetooth pas encore stable...),
     * réessaie un nombre limité de fois avec un délai progressif plutôt que
     * d'attendre silencieusement le prochain cycle périodique (60s, voir
     * init{}).
     */
    private suspend fun retryInitialDeviceInfoIfNeeded() {
        var attempt = 0
        var delayMs = STARTUP_RETRY_INITIAL_DELAY_MS
        while (attempt < STARTUP_RETRY_MAX_ATTEMPTS) {
            if (sendDeviceInfo()) return
            attempt++
            if (attempt >= STARTUP_RETRY_MAX_ATTEMPTS) return
            delay(delayMs)
            delayMs = (delayMs * STARTUP_RETRY_MULTIPLIER).toLong().coerceAtMost(STARTUP_RETRY_MAX_DELAY_MS)
        }
    }

    private fun Intent.getIntOfExtra(name: String, defaultValue: Int): Int {
        return getIntExtra(name, defaultValue)
    }

    suspend fun initialize() {
        FoxWearLogger.i("FoxWearCore initializing")

        // Sélection du backend BPM. PassiveMonitoringClient est l'API
        // officiellement recommandée par Google pour une surveillance
        // continue en arrière-plan (agrégation par lots côté puce, bien
        // plus économe) — voir developer.android.com/health-and-fitness
        // /health-services. ExerciseClient est conçu pour un entraînement
        // ponctuel de durée limitée : le faire tourner EN PLUS de
        // PassiveMonitoringClient, en continu, 24h/24 (comme avant) doublait
        // la consommation pour la MÊME donnée (les deux remontaient déjà le
        // même flux de BPM au téléphone, voir la boucle de pontage
        // ci-dessous et sendPassiveSample()) — cause confirmée du vidage
        // total de batterie de la montre en une seule nuit (2026-08-08 :
        // 77% au coucher, 0% au réveil ~9h plus tard). Passive devient donc
        // le SEUL moteur actif quand l'appareil le supporte ; ExerciseClient
        // /HealthServicesEngine ne servent plus que de repli si Passive
        // n'est pas disponible sur ce matériel.
        val passiveCandidate = PassiveMonitoringEngine(context)
        val isPassiveSupported = passiveCandidate.isSupported()
        _passiveDiagnostic.update { it.copy(supported = isPassiveSupported, lastError = passiveCandidate.lastError) }

        if (isPassiveSupported) {
            FoxWearLogger.i("FOX-WEAR | Selected backend = PASSIVE (seul moteur actif, économe en batterie)")
            passiveCandidate.initialize()
            passiveEngine = passiveCandidate
            _status.emit("INITIALIZED")

            // Send initial device info to phone even if no BPM yet — avec
            // tentatives limitées si aucun nœud n'est encore joignable.
            scope.launch {
                retryInitialDeviceInfoIfNeeded()
            }
        } else {
            FoxWearLogger.w("FOX-WEAR | PassiveMonitoringEngine non supporté sur cet appareil (${passiveCandidate.lastError}) -> repli sur backend actif")

            val exerciseEngine = ExerciseEngine(context)
            val isExerciseSupported = exerciseEngine.isSupported()

            val hsEngine = HealthServicesEngine(context)
            val isHsSupported = hsEngine.isSupported()

            FoxWearLogger.i("Exercise Client supported = $isExerciseSupported")
            FoxWearLogger.i("Health Services available = $isHsSupported")

            engine = when {
                isExerciseSupported -> {
                    FoxWearLogger.i("Selected backend = EXERCISE (repli)")
                    exerciseEngine
                }
                isHsSupported -> {
                    FoxWearLogger.i("Selected backend = HEALTH_SERVICES (repli)")
                    hsEngine
                }
                else -> {
                    FoxWearLogger.i("Selected backend = LEGACY (repli)")
                    LegacyEngine(context)
                }
            }

            try {
                engine?.initialize()
                FoxWearLogger.i("Backend initialized successfully")
                _status.emit("INITIALIZED")

                scope.launch {
                    retryInitialDeviceInfoIfNeeded()
                }
            } catch (e: Exception) {
                FoxWearLogger.e("Error initializing selected backend, falling back to LEGACY", e)
                engine = LegacyEngine(context)
                engine?.initialize()
                _status.emit("INITIALIZED")
            }

            // Bridge samples to communication manager and internal flow
            scope.launch {
                FoxWearLogger.i("FOX-WEAR | Démarrage de la boucle de pontage des BPM")
                engine?.samples?.collect { sample ->
                    FoxWearLogger.d("FOX-WEAR | Pontage BPM: ${sample.bpm} vers UI et Communication")
                    _samples.emit(sample)
                    communicationManager.sendSample(sample)
                }
            }
        }

        if (movementEngine.isSupported()) {
            FoxWearLogger.i("FOX-WEAR | MovementEngine supporté")
        } else {
            FoxWearLogger.w("FOX-WEAR | MovementEngine non supporté sur cet appareil (${movementEngine.lastError})")
        }
        scope.launch {
            movementEngine.magnitude.collect { magnitude ->
                FoxWearLogger.d("FOX-WEAR | Mouvement mesuré : $magnitude")
                communicationManager.sendMovement(magnitude)
            }
        }
    }

    // Suit l'état réel indépendamment de _isMonitoring (SharedFlow, pas
    // directement lisible de façon synchrone) — sert de garde d'idempotence
    // pour startMonitoring()/stopMonitoring(), désormais appelables à
    // distance (voir setMonitoringActive ci-dessous) et donc potentiellement
    // redondants (deux messages /foxoff/monitoring_stop rapprochés ne
    // doivent pas arrêter des moteurs déjà arrêtés).
    @Volatile
    private var monitoringActive = false

    suspend fun startMonitoring() {
        if (monitoringActive) return
        FoxWearLogger.i("Starting monitoring")
        engine?.start()
        passiveEngine?.let { passive ->
            passive.start()
            _passiveDiagnostic.update { it.copy(started = passive.lastError == null, lastError = passive.lastError) }
        }
        movementEngine.start()
        monitoringActive = true
        _isMonitoring.emit(true)
        _status.emit("RUNNING")
    }

    suspend fun stopMonitoring() {
        if (!monitoringActive) return
        FoxWearLogger.i("Stopping monitoring")
        engine?.stop()
        passiveEngine?.stop()
        movementEngine.stop()
        monitoringActive = false
        _isMonitoring.emit(false)
        _status.emit("READY")
    }

    /**
     * Piloté par le téléphone selon les plages horaires choisies par
     * l'utilisateur (voir ActiveHoursSettings/FoxServiceReconciliation côté
     * téléphone) — messages /foxoff/monitoring_stop et
     * /foxoff/monitoring_start (voir FoxWearListenerService). Contrairement
     * à setMovementLowPowerMode(), qui ne fait que ralentir le mouvement
     * après la pause TV, ceci arrête RÉELLEMENT tous les capteurs (BPM actif,
     * BPM passif, mouvement) : hors plage horaire choisie, la montre n'a
     * plus aucune raison de mesurer quoi que ce soit. Best-effort comme le
     * reste des commandes montre — sans effet si le message n'arrive jamais
     * (montre injoignable), la montre continue simplement de fonctionner
     * comme avant cette fonctionnalité.
     */
    fun setMonitoringActive(active: Boolean) {
        scope.launch {
            if (active) startMonitoring() else stopMonitoring()
        }
    }

    /**
     * Manually bridge a sample from passive background services.
     */
    suspend fun sendPassiveSample(sample: WearHeartRateSample) {
        _passiveDiagnostic.update { it.copy(lastSampleAt = Instant.now()) }
        _samples.emit(sample)
        communicationManager.sendSample(sample)
        sendDeviceInfo()
    }

    /**
     * Envoie immédiatement les infos montre (nom + batterie), en réponse à
     * une demande explicite du téléphone (/foxoff/request_watch_info),
     * plutôt que d'attendre le prochain envoi périodique (60s).
     */
    fun sendDeviceInfoNow() {
        scope.launch {
            sendDeviceInfo()
        }
    }

    /**
     * Réponse de l'utilisateur à ConfirmAwakeNotifier ("Je suis là") — voir
     * SleepPauseCoordinator côté téléphone, qui annule le compte à rebours
     * de pause à la réception.
     */
    fun notifyStillAwake() {
        scope.launch {
            communicationManager.sendConfirmResponse()
        }
    }

    /**
     * Économie de batterie post-endormissement (/foxoff/movement_low_power
     * et /foxoff/movement_normal_power, voir FoxWearListenerService) — ne
     * touche que MovementEngine, pas le BPM (PassiveMonitoringEngine reste
     * actif toute la nuit, seul suivi qui compte encore une fois la TV en
     * pause).
     */
    fun setMovementLowPowerMode(enabled: Boolean) {
        movementEngine.setLowPowerMode(enabled)
    }

    fun notifyForegroundServiceActive(isActive: Boolean) {
        _isForegroundServiceActive.value = isActive
        if (isActive) {
            FoxWearLogger.i("Foreground confirmed")
        }
    }

    suspend fun shutdown() {
        engine?.shutdown()
        passiveEngine?.shutdown()
        movementEngine.stop()
        _status.emit("OFF")
    }

    fun getEngineDiagnostic(): String {
        val active = engine?.getDiagnosticInfo() ?: "Aucun (Passive utilisé seul, voir initialize())"
        val passive = if (passiveEngine != null) "PASSIVE actif" else "PASSIVE inactif"
        return "$active | $passive"
    }
}
