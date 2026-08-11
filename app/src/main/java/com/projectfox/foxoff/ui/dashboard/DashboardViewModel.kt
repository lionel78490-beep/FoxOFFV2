package com.projectfox.foxoff.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectfox.foxoff.brain.SleepState
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.presence.WatchConnectionState
import com.projectfox.foxoff.core.presence.WatchConnectionStateHolder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Lu une seule fois (voir ensureWatchConnected) pour éviter de relire les
    // SharedPreferences à chaque émission de FoxCore.brain.state.
    private var lastKnownWatchName: String? = null

    init {
        startRealUpdates()
    }

    private fun startRealUpdates() {
        viewModelScope.launch {
            // SINGLE SOURCE OF TRUTH pour "connectée ou non": FoxBrain.state.
            // WatchConnectionStateHolder complète avec le détail des
            // sous-phases (Vérification/Reconnexion) que ce Boolean seul ne
            // distingue pas (voir WatchPresenceCoordinator).
            combine(FoxCore.brain.state, WatchConnectionStateHolder.state) { brainState, connState ->
                brainState to connState
            }.collectLatest { (brainState, connState) ->
                _uiState.update {
                    it.copy(
                        // Global
                        mainStatus = if (!brainState.watchConnected) "Recherche montre..."
                                     else getMainStatusLabel(brainState.detectedSleepState),
                        mainStatusColor = if (!brainState.watchConnected) Color.Yellow
                                          else getStatusColor(brainState.detectedSleepState),

                        // Watch — si non connectée mais déjà associée par le passé,
                        // on affiche son nom mémorisé avec un état "Hors ligne"
                        // plutôt que "Aucune montre" (voir DeviceCard).
                        watchConnected = brainState.watchConnected,
                        watchKnown = brainState.watchConnected || lastKnownWatchName != null,
                        watchName = when {
                            brainState.watchConnected -> brainState.watchName
                            lastKnownWatchName != null -> lastKnownWatchName!!
                            else -> brainState.watchName
                        },
                        watchBattery = if (brainState.watchBattery > 0) "${brainState.watchBattery}%" else "Connecté",
                        watchStatusOverride = when (connState) {
                            WatchConnectionState.VERIFYING -> "Vérification de la montre…"
                            WatchConnectionState.RECONNECTING -> "Reconnexion à la montre…"
                            else -> null
                        },

                        // TV — nom/statut/app viennent de FoxCore.tvEngine.state
                        // (voir le collecteur dédié ci-dessous), pas de brainState :
                        // FoxBrainState.tvName n'est jamais renseigné et
                        // FoxBrainState.tvConnected est réservé au scoring.
                        tvLastCommand = brainState.tvLastCommand,
                        tvLastCommandTime = brainState.tvLastCommandTime,

                        // HR — 0 côté FoxBrainState signifie "aucune mesure reçue
                        // pour l'instant" (voir FoxBrain.onEvent) ; traduit ici en
                        // null pour un affichage honnête ("--") plutôt qu'un faux 0.
                        currentBpm = brainState.currentBpm,
                        lastBpmTime = brainState.lastBpmTime,
                        minBpmToday = brainState.minBpmToday.takeIf { it > 0 },
                        maxBpmToday = brainState.maxBpmToday.takeIf { it > 0 },
                        
                        // Analysis
                        sleepState = brainState.detectedSleepState,
                        confidence = (brainState.lastScore.confidence * 100).toInt(),
                        analysisExplanation = brainState.lastScore.reason
                    )
                }
            }
        }

        viewModelScope.launch {
            // La carte Accueil suit la TV ACTIVE, pas la TV sélectionnée
            // dans la télécommande (engine.state) : ouvrir la télécommande
            // d'une autre TV depuis l'onglet TV ne doit jamais changer ce
            // qui est affiché ici (voir FoxTvEngine.selectForRemote).
            val engine = FoxCore.tvEngine ?: return@launch
            combine(engine.pairedDevices, engine.activeDeviceId) { devices, activeId ->
                activeId?.let { id -> devices.firstOrNull { it.id == id } }
            }.collectLatest { activeDevice ->
                _uiState.update {
                    it.copy(
                        tvName = activeDevice?.name ?: "Aucune TV associée",
                        tvStatus = activeDevice?.status,
                        tvCurrentApp = activeDevice?.currentApp ?: "Aucune"
                    )
                }
            }
        }
    }

    fun onAssociateClick(context: android.content.Context) {
        viewModelScope.launch {
            FoxCore.discoverWatch(context)
        }
    }

    /**
     * Déclenche une reconnexion montre automatique à l'ouverture du
     * Dashboard. FoxCore.discoverWatch() a son propre verrou anti-concurrence
     * (voir FoxCore.isDiscoveringWatch), donc un appel redondant depuis
     * plusieurs endroits (bouton "Associer" par ex.) ne crée pas de recherche
     * en double.
     */
    fun ensureWatchConnected(context: android.content.Context) {
        lastKnownWatchName = com.projectfox.foxoff.core.application.WatchSettings
            .getLastKnownWatchName(context)

        viewModelScope.launch {
            FoxCore.discoverWatch(context)
        }
    }

    private fun getMainStatusLabel(state: SleepState): String {
        return when (state) {
            SleepState.AWAKE -> "Utilisateur éveillé"
            SleepState.DROWSY -> "Fatigue légère"
            SleepState.PRE_SLEEP -> "Pré-endormissement"
            SleepState.ASLEEP -> "Sommeil probable"
        }
    }

    private fun getStatusColor(state: SleepState): Color {
        return when (state) {
            SleepState.AWAKE -> Color.Green
            SleepState.DROWSY -> Color.Cyan
            SleepState.PRE_SLEEP -> Color.Yellow
            SleepState.ASLEEP -> Color(0xFFD500F9) // FoxElectricViolet
        }
    }
}
