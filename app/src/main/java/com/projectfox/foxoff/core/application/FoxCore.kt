package com.projectfox.foxoff.core.application

import com.projectfox.foxoff.automation.RealTvController
import com.projectfox.foxoff.automation.TvController
import com.projectfox.foxoff.brain.*
import com.projectfox.foxoff.core.events.FoxEventBus
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.sensors.events.SensorEvent
import android.content.Context
import com.projectfox.foxoff.core.automation.NightLog
import com.projectfox.foxoff.core.automation.NightLogType
import com.projectfox.foxoff.core.automation.SleepPauseCoordinator
import com.projectfox.foxoff.core.presence.WatchPresenceCoordinator
import com.projectfox.foxoff.core.watch.GarminTransport
import com.projectfox.foxoff.core.watch.WatchBrand
import com.projectfox.foxoff.core.watch.WatchTransport
import com.projectfox.foxoff.core.watch.WearOsTransport
import com.projectfox.foxoff.tv.FoxTvEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FoxCore {
    val eventBus = FoxEventBus()
    private val coreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Instance UNIQUE, partagée entre l'analyseur (bonus/pénalités de score)
    // et la boucle de décision ci-dessous (seuils de déclenchement) — voir
    // shouldSendAutoPause() et startOrchestration(). Centralise toute la
    // sensibilité de détection au même endroit (ROADMAP.md, Phase 2).
    private val sleepScoringConfig = SleepScoringConfig()

    val brain = FoxBrain(WeightedSleepAnalyzer(sleepScoringConfig), sleepScoringConfig)

    private var _tvEngine: FoxTvEngine? = null
    val tvEngine: FoxTvEngine? get() = _tvEngine

    private var _tvController: TvController? = null
    val tvController: TvController? get() = _tvController

    // Wear OS par défaut — voir WatchTransport pour l'abstraction préparée
    // en vue d'un futur GarminTransport (Connect IQ Mobile SDK).
    private var watchTransport: WatchTransport? = null

    // Real-time Watch State
    private val _watchInfo = MutableStateFlow<SensorEvent.WatchInfoReceived?>(null)
    val watchInfo: StateFlow<SensorEvent.WatchInfoReceived?> = _watchInfo.asStateFlow()

    // Empêche les recherches de montre concurrentes (ex: auto-découverte au
    // lancement du Dashboard + clic manuel "Associer" en même temps).
    private val isDiscoveringWatch = java.util.concurrent.atomic.AtomicBoolean(false)

    suspend fun initialize(context: Context) {
        // Initialize TV Engine but DON'T start discovery yet
        _tvEngine = FoxTvEngine(context)
        _tvController = RealTvController(_tvEngine!!)

        // Marque sélectionnée par l'utilisateur (Réglages "Changer de
        // montre") — WEAR_OS par défaut, voir WatchSettings.getWatchBrand().
        watchTransport = when (WatchSettings.getWatchBrand(context)) {
            WatchBrand.WEAR_OS -> WearOsTransport(context)
            WatchBrand.GARMIN -> GarminTransport(context).also { it.start() }
        }

        // Référence BPM de repos calibrée depuis Health Connect si déjà
        // calculée lors d'une session précédente (voir SettingsScreen —
        // "Calibrer avec Samsung Health"). Sinon, reste sur la moyenne
        // générique par défaut (70 bpm, voir FoxBrainState).
        RestingBpmSettings.getCalibratedBpm(context)?.let { brain.setRestingBpmBaseline(it) }

        startOrchestration(context)

        // Removed discoverWatch(context) and _tvEngine?.initialize() from here
        // They will be triggered in the Onboarding screens after permissions.
    }

    private fun startOrchestration(context: Context) {
        // Arme le moteur de présence — fait ICI, une seule fois : dès le
        // lancement du processus, indépendamment de la surveillance en
        // arrière-plan optionnelle (FoxForegroundService). C'est le SEUL
        // endroit du code qui appelle WatchPresenceCoordinator.start().
        // Transport déjà résolu selon la marque sélectionnée (voir initialize()).
        watchTransport?.let { WatchPresenceCoordinator.start(context, it) }
        SleepPauseCoordinator.start(context)
        NightLog.loadAll(context)

        coreScope.launch {
            // All sensor/external events are funneled through FoxBrain
            launch {
                eventBus.subscribeAs<SensorEvent.HeartRateReceived>().collectLatest { event ->
                    FoxLogger.i("FOX-CORE | Orchestrator | BPM reçu -> Brain")
                    brain.onEvent(FoxBrainEvent.HeartRateReceived(event.sample.bpm, "WEAR"))
                }
            }

            launch {
                eventBus.subscribeAs<SensorEvent.WatchInfoReceived>().collectLatest { info ->
                    FoxLogger.i("FOX-CORE | Orchestrator | WatchInfo reçu -> Brain")
                    WatchSettings.saveLastKnownWatch(context, info.name)
                    brain.onEvent(FoxBrainEvent.WatchConnected(info.name))
                    brain.onEvent(FoxBrainEvent.BatteryChanged(info.battery, false))
                }
            }

            launch {
                eventBus.subscribeAs<SensorEvent.WatchDisconnected>().collectLatest {
                    FoxLogger.i("FOX-CORE | Orchestrator | Montre déconnectée (nœud Data Layer) -> Brain")
                    brain.onEvent(FoxBrainEvent.WatchDisconnected)
                }
            }

            // Contre-signal au score de sommeil (voir MovementEngine côté
            // montre) — corrige le faux positif du 2026-08-07 (score qui ne
            // pouvait que monter, jamais redescendre, faute de tout signal
            // de mouvement en production).
            launch {
                eventBus.subscribeAs<SensorEvent.MovementDetected>().collectLatest { event ->
                    val significant = event.magnitude > sleepScoringConfig.movementThreshold
                    // Une fois la TV en pause (sommeil confirmé), les
                    // micro-mouvements n'ont plus d'utilité : ils ne
                    // changent plus aucune décision (déjà prise) et ne
                    // servent qu'à noyer l'Historique de la nuit sous des
                    // centaines d'entrées sans intérêt. Seuls les VRAIS
                    // changements de position restent suivis après la
                    // pause — demande explicite de l'utilisateur.
                    if (!significant && brain.state.value.tvIsPaused) {
                        return@collectLatest
                    }
                    FoxLogger.i("FOX-CORE | Orchestrator | Mouvement (${event.magnitude}) -> Brain")
                    brain.onEvent(FoxBrainEvent.MovementDetected(event.magnitude))
                    NightLog.record(
                        context,
                        NightLogType.MOVEMENT,
                        "Magnitude ${"%.2f".format(event.magnitude)}" + if (significant) " (significatif)" else ""
                    )
                }
            }

            // Pilote WatchReconnectionEngine à partir de la source de vérité
            // unique (FoxBrainState.watchConnected), pas seulement des deux
            // chemins internes de WatchPresenceEngine : couvre aussi le cas
            // d'un lancement d'app où la montre connue ne répond jamais (rien
            // n'a encore été "déclaré Hors ligne" par le moniteur puisqu'il
            // n'a jamais reçu de preuve durant ce processus).
            launch {
                var lastKnownConnected: Boolean? = null
                brain.state.collectLatest { state ->
                    if (lastKnownConnected != state.watchConnected) {
                        lastKnownConnected = state.watchConnected
                        if (state.watchConnected) {
                            WatchPresenceCoordinator.onWatchConnected()
                            NightLog.record(context, NightLogType.WATCH_CONNECTED, state.watchName)
                        } else {
                            WatchPresenceCoordinator.onWatchOffline(context)
                            NightLog.record(context, NightLogType.WATCH_DISCONNECTED, "Montre déconnectée")
                        }
                    }
                }
            }

            // Observe TV Engine State (TV utilisée uniquement)
            launch {
                var lastTvConnected: Boolean? = null
                tvEngine?.activeDevice?.collectLatest { tvDevice ->
                    if (tvDevice != null) {
                        // CONNECTING est un état transitoire (sonde de
                        // reconnexion, notamment le heartbeat périodique de
                        // FoxForegroundService toutes les 15 min) — ne doit
                        // déclencher ni TVTurnedOn ni TVTurnedOff. Régression
                        // réelle constatée nuit du 2026-08-08 au 09 : traité
                        // comme "off", CONNECTING faisait chuter le score de
                        // moitié (currentProb *= 0.5f côté
                        // WeightedSleepAnalyzer) toutes les 15 min pendant
                        // toute la nuit, empêchant toute accumulation même
                        // avec un vrai sommeil confirmé (BPM stable très bas,
                        // jusqu'à 45). Introduite par l'ajout du heartbeat TV
                        // le jour même (voir ROADMAP.md Phase 5).
                        if (tvDevice.status == com.projectfox.foxoff.tv.TvConnectionStatus.CONNECTING) {
                            return@collectLatest
                        }
                        val connected = tvDevice.status == com.projectfox.foxoff.tv.TvConnectionStatus.CONNECTED
                        // L'événement Brain ne doit partir que sur un VRAI
                        // changement d'état, pas à chaque réémission du même
                        // statut (le heartbeat réémet régulièrement la même
                        // valeur "non connectée") — sinon le halving se
                        // répète silencieusement sans même apparaître dans
                        // NightLog (le texte de raison ne change pas d'une
                        // fois à l'autre, donc le journal Historique ne le
                        // montre pas non plus).
                        if (lastTvConnected != connected) {
                            if (connected) {
                                brain.onEvent(FoxBrainEvent.TVTurnedOn)
                                // TV rallumée -> l'utilisateur regarde de
                                // nouveau (nouvelle soirée, ou réveil manuel
                                // qui a repris la lecture) : remet le
                                // mouvement montre en fréquence normale si le
                                // mode basse consommation avait été activé
                                // par une pause précédente. Best-effort, sans
                                // effet si déjà en fréquence normale.
                                sendToWatch(context, "/foxoff/movement_normal_power")
                            } else {
                                brain.onEvent(FoxBrainEvent.TVTurnedOff)
                            }
                            lastTvConnected = connected
                            NightLog.record(
                                context,
                                if (connected) NightLogType.TV_ON else NightLogType.TV_OFF,
                                tvDevice.name
                            )
                        }
                        brain.onEvent(FoxBrainEvent.TvAppChanged(tvDevice.currentApp))
                    }
                }
            }

            // Journal "Historique" (onglet dédié) : trace chaque changement
            // de raison de score ou d'état de sommeil, avec le contexte
            // BPM/score/confiance — reconstruit le fil de la nuit sans
            // avoir à décrire ce qui s'est passé.
            launch {
                var lastReason: String? = null
                var lastSleepState: SleepState? = null
                brain.state.collectLatest { state ->
                    val reasonChanged = lastReason != state.lastScore.reason
                    val sleepStateChanged = lastSleepState != state.detectedSleepState
                    if (reasonChanged || sleepStateChanged) {
                        lastReason = state.lastScore.reason
                        lastSleepState = state.detectedSleepState
                        NightLog.record(
                            context,
                            NightLogType.SLEEP_STATE_CHANGE,
                            "${state.detectedSleepState} — score ${(state.lastScore.sleepProbability * 100).toInt()}%, " +
                                    "confiance ${(state.lastScore.confidence * 100).toInt()}%, BPM ${state.currentBpm ?: "?"} — ${state.lastScore.reason}"
                        )
                    }
                }
            }

            // Brain Decision Loop
            launch {
                brain.state.collectLatest { brainState ->
                    val score = brainState.lastScore.sleepProbability

                    // 1. Rule: Score > 90% -> Pause TV (via un compte à rebours
                    // annulable, voir SleepPauseCoordinator — la pause réelle
                    // et les événements SleepDetected/TvCommandSent
                    // n'arrivent qu'à l'issue du délai, pas ici directement).
                    // Comportement central de l'application, non désactivable
                    // par l'utilisateur (aucun réglage ne le conditionne).
                    if (shouldSendAutoPause(brainState)) {
                        FoxLogger.i("FOX-CORE | Brain decision: User ASLEEP. Endormissement détecté -> compte à rebours annulable")
                        SleepPauseCoordinator.onSleepDetected(
                            brainState.lastScore.sleepProbability,
                            brainState.lastScore.confidence
                        )
                    }
                    
                    // 2. Rule: Score > seuil -> Start High Precision Monitoring
                    if (score > sleepScoringConfig.highPrecisionThreshold && !brainState.isMonitoring) {
                        FoxLogger.i("FOX-CORE | Pré-endormissement (70%+) : Demande de mode Haute Précision")
                        sendToWatch(context, "/foxoff/start_high_precision")
                    }
                }
            }
        }
    }

    /**
     * Décision pure (sans effet de bord), extraite pour être testable
     * unitairement : détermine si la pause TV automatique doit réellement
     * être envoyée. Comportement central de l'application — aucun réglage
     * utilisateur ne le conditionne. `config` par défaut = l'instance
     * partagée réelle ; paramétrable pour les tests qui veulent un seuil
     * différent sans dépendre de l'état global de FoxCore.
     */
    internal fun shouldSendAutoPause(
        brainState: FoxBrainState,
        config: SleepScoringConfig = sleepScoringConfig
    ): Boolean {
        return brainState.detectedSleepState == SleepState.ASLEEP &&
                brainState.lastScore.confidence > config.autoPauseConfidenceThreshold &&
                !brainState.tvIsPaused
    }

    /**
     * Envoie une commande arbitraire à la montre active (voir
     * WatchTransport.sendCommand) — fire-and-forget, best-effort comme le
     * reste des interactions montre : `context` explicite pour être
     * appelable depuis n'importe quel appelant (ex. SleepPauseCoordinator,
     * qui a son propre appContext), pas seulement depuis
     * startOrchestration().
     */
    internal fun sendToWatch(context: Context, path: String, payload: ByteArray = byteArrayOf()) {
        coreScope.launch(Dispatchers.IO) {
            try {
                val transport = watchTransport
                val nodeId = WatchSettings.getLastKnownNodeId(context)
                if (transport == null || nodeId == null) {
                    FoxLogger.w("FOX-CORE | Commande montre [$path] ignorée : transport ou nœud inconnu")
                    return@launch
                }
                val sent = transport.sendCommand(nodeId, path, payload)
                FoxLogger.i("FOX-CORE | Commande envoyée à la montre [$path] -> $sent")
            } catch (e: Exception) {
                FoxLogger.e("FOX-CORE | Erreur envoi commande montre", e)
            }
        }
    }

    suspend fun discoverWatch(context: Context) = withContext(Dispatchers.IO) {
        if (!isDiscoveringWatch.compareAndSet(false, true)) {
            FoxLogger.i("FOX-WATCH | discoverWatch() déjà en cours, appel ignoré")
            return@withContext
        }
        try {
            FoxLogger.i("FOX-WATCH | discoverWatch() started")
            val transport = watchTransport ?: WearOsTransport(context).also { watchTransport = it }
            val devices = transport.connectedDevices()

            FoxLogger.i("FOX-WATCH | connectedDevices() -> ${devices.size} appareil(s)")
            devices.forEach { d ->
                FoxLogger.i("FOX-WATCH | Appareil : id=${d.id} nom=${d.displayName}")
            }

            val primaryDevice = devices.firstOrNull()
            if (primaryDevice != null) {
                FoxLogger.i("FOX-WATCH | Device found : ${primaryDevice.displayName}")
                // Ne PAS publier WatchInfoReceived ici : connectedDevices() seul ne
                // prouve pas que la montre répond réellement (voir
                // WatchPresenceCoordinator). Signal local pour l'onboarding
                // uniquement — ne déclenche aucune transition dans le Brain.
                _watchInfo.value = SensorEvent.WatchInfoReceived(primaryDevice.displayName, 0, true)

                // Demande à la montre de répondre avec ses vraies infos (nom +
                // batterie) : c'est cette réponse (onMessageReceived côté
                // PhoneWearListenerService) qui fera réellement passer l'état à
                // "Connectée".
                if (transport.requestInfo(primaryDevice.id)) {
                    FoxLogger.i("FOX-WATCH | Demande watch_info envoyée à ${primaryDevice.id}")
                } else {
                    FoxLogger.e("FOX-WATCH | Échec envoi demande watch_info")
                }
            } else {
                // Aucun appareil joignable n'est pas une preuve suffisante à elle
                // seule (voir WatchPresenceCoordinator) : on se contente de
                // journaliser, l'échéance de WatchPresenceEngine (15s sans
                // preuve -> Vérification -> 5s -> Hors ligne) reste seule
                // autorité sur la décision, indépendamment de ce sondage
                // ponctuel.
                FoxLogger.w("FOX-WATCH | Aucun appareil joignable via le transport")
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-WATCH | Erreur lors de la découverte", e)
        } finally {
            isDiscoveringWatch.set(false)
        }
    }

}
