package com.projectfox.foxoff.tv

import android.content.Context
import com.projectfox.foxoff.core.automation.TvAutoPowerOffCoordinator
import com.projectfox.foxoff.core.logging.FoxLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * TV mémorisée sans empreinte confirmée dont une connexion vient de
 * réussir : le moteur ne l'a PAS encore marquée connectée, et n'a rien
 * écrit dans FoxTvSettings. [proposedAddress] est l'adresse à laquelle la
 * connexion a réussi (peut différer de l'adresse mémorisée si elle vient
 * d'une correspondance par nom). Ne devient définitif qu'après
 * FoxTvEngine.confirmPendingIdentity(deviceId).
 */
data class PendingIdentityConfirmation(
    val deviceId: String,
    val proposedAddress: String,
    val fingerprint: String
)

/**
 * Un appareil redécouvert par le scan porte le même nom qu'une TV
 * mémorisée mais une adresse différente : ambigu par construction (voir
 * TvMultiDeviceMatcher), nécessite une confirmation explicite AVANT toute
 * tentative de connexion.
 */
data class PendingNameMatch(
    val storedDeviceId: String,
    val storedName: String,
    val discoveredAddress: String
)

/** Résultat de selectDevice(), pour que l'UI sache si elle doit naviguer (nouvel appairage) ou simplement informer (TV déjà connue). */
sealed class TvSelectDeviceOutcome {
    data class NewPairingStarted(val deviceId: String) : TvSelectDeviceOutcome()
    data class AlreadyAssociated(val deviceId: String, val deviceName: String) : TvSelectDeviceOutcome()
    object AmbiguousNameMatch : TvSelectDeviceOutcome()
}

class FoxTvEngine(
    private val context: Context
) {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val repository = TvStateRepository()

    private val discoveryManager =
        TvDiscoveryManager(context) { device ->
            repository.addDiscoveredDevice(device)
        }

    private val pairingManager =
        TvPairingManager(context) { deviceId, status ->
            repository.updateStatus(deviceId, status)
        }

    // Empêche deux tentatives de connexion concurrentes vers la MÊME TV.
    private val connectingIds = ConcurrentHashMap.newKeySet<String>()

    private val pendingConfirmations = ConcurrentHashMap<String, PendingIdentityConfirmation>()

    private val _pendingConfirmation = MutableStateFlow<PendingIdentityConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingIdentityConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _pendingNameMatch = MutableStateFlow<PendingNameMatch?>(null)
    val pendingNameMatch: StateFlow<PendingNameMatch?> = _pendingNameMatch.asStateFlow()

    init {
        /*
         * Recharge TOUTES les TV mémorisées dès la construction du moteur,
         * indépendamment de toute découverte réseau, mais AUCUNE ne se
         * connecte automatiquement sauf la TV utilisée (activeDeviceId) :
         * pas de boucle, pas de reconnexion en masse.
         */
        val storedDevices = FoxTvSettings.getPairedDevices(context)

        if (storedDevices.isNotEmpty()) {
            repository.setPairedDevices(
                storedDevices.map { it.copy(status = TvConnectionStatus.DISCONNECTED) }
            )

            val activeId = FoxTvSettings.getActiveDeviceId(context)
            repository.setActiveDeviceId(activeId)

            FoxLogger.i(
                "FOX-TV | Reprise de ${storedDevices.size} TV mémorisée(s), TV utilisée : $activeId"
            )

            val activeDevice = activeId?.let { id -> storedDevices.firstOrNull { it.id == id } }
            if (activeDevice != null) {
                attemptConnection(activeDevice, activeDevice.id, requireStillActive = true)
            }
        }
    }

    val pairedDevices: StateFlow<List<TvDevice>> = repository.pairedDeviceStates
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeDeviceId: StateFlow<String?> = repository.activeDeviceId

    /**
     * La TV utilisée, avec son état live. Toute commande (Play/Pause,
     * future pause automatique) doit passer par elle et rien d'autre —
     * jamais une boucle sur pairedDevices.
     */
    val activeDevice: StateFlow<TvDevice?> = combine(
        repository.pairedDeviceStates,
        repository.activeDeviceId
    ) { states, activeId -> activeId?.let { states[it] } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val discoveredDevices: StateFlow<List<TvDevice>> =
        repository.discoveredDevices

    fun initialize() {
        repository.clearDiscoveredDevices()
        discoveryManager.startDiscovery()
    }

    /**
     * Appelé quand l'utilisateur sélectionne un appareil dans la liste de
     * scan ("Ajouter une TV"). Ne change JAMAIS la TV utilisée et ne la
     * connecte jamais durablement : compare TOUJOURS à l'ensemble des TV
     * mémorisées avant de proposer un appairage.
     * - adresse déjà connue -> simple vérification/mise à jour d'adresse,
     *   sans rendre la TV active ;
     * - même nom, adresse différente -> ambigu, confirmation requise ;
     * - aucune correspondance -> nouvel appairage (id stable généré ici,
     *   jamais l'id volatil de découverte ; ne devient active que s'il
     *   s'agit de la toute première TV jamais associée).
     */
    fun selectDevice(device: TvDevice): TvSelectDeviceOutcome {
        FoxLogger.i(
            "FOX-TV | User selected device: " +
                    "${device.name} (${device.address})"
        )

        return when (val candidate = TvMultiDeviceMatcher.match(device, FoxTvSettings.getPairedDevices(context))) {
            is TvMatchCandidate.AddressCandidate -> {
                FoxLogger.i(
                    "FOX-TV | Scan : adresse correspond à ${candidate.storedDevice.id} " +
                            "-> vérification (TV non rendue active)"
                )
                attemptConnection(
                    candidate.storedDevice.copy(address = device.address),
                    candidate.storedDevice.id,
                    requireStillActive = false
                )
                TvSelectDeviceOutcome.AlreadyAssociated(candidate.storedDevice.id, candidate.storedDevice.name)
            }

            is TvMatchCandidate.NameOnlyCandidate -> {
                FoxLogger.i(
                    "FOX-TV | Scan : nom correspond à ${candidate.storedDevice.id}, " +
                            "adresse différente -> confirmation utilisateur requise"
                )
                _pendingNameMatch.value = PendingNameMatch(
                    storedDeviceId = candidate.storedDevice.id,
                    storedName = candidate.storedDevice.name,
                    discoveredAddress = device.address
                )
                TvSelectDeviceOutcome.AmbiguousNameMatch
            }

            TvMatchCandidate.NoCandidate -> {
                FoxLogger.i(
                    "FOX-TV | Scan : aucune correspondance -> nouvel appairage"
                )
                val newDevice = device.copy(id = FoxTvSettings.generateStableId())
                repository.upsertPairedDevice(newDevice.copy(status = TvConnectionStatus.PAIRING_REQUIRED))
                TvSelectDeviceOutcome.NewPairingStarted(newDevice.id)
            }
        }
    }

    /**
     * L'utilisateur confirme qu'un appareil redécouvert avec le même nom
     * qu'une TV mémorisée (adresse différente) est bien la même TV. Ne
     * rend PAS cette TV active : vérifie seulement son identité à la
     * nouvelle adresse (voir applyConnectionResult / TvConnectionOutcomeResolver).
     */
    fun confirmNameMatch() {
        val pending = _pendingNameMatch.value ?: return
        _pendingNameMatch.value = null

        val stored = FoxTvSettings.getDevice(context, pending.storedDeviceId) ?: return
        attemptConnection(stored.copy(address = pending.discoveredAddress), stored.id, requireStillActive = false)
    }

    fun dismissNameMatch() {
        _pendingNameMatch.value = null
    }

    /**
     * Action principale de la liste : rend cette TV "utilisée", désactive
     * logiquement l'ancienne (jamais deux TV actives ni "connectées" en
     * même temps -- changement atomique, une seule écriture de l'id actif),
     * puis tente une connexion réelle vers elle.
     */
    fun useDevice(id: String) {
        val previousActiveId = repository.activeDeviceId.value

        FoxTvSettings.setActiveDevice(context, id)
        repository.setActiveDeviceId(id)

        if (previousActiveId != null && previousActiveId != id) {
            FoxLogger.i("FOX-TV | TV utilisée changée : $previousActiveId -> $id")
            // Aucun état "connecté" ne doit rester associé à l'ancienne TV
            // utilisée après le changement.
            repository.updateStatus(previousActiveId, TvConnectionStatus.DISCONNECTED)
        }

        val device = repository.getDevice(id) ?: FoxTvSettings.getDevice(context, id) ?: return
        attemptConnection(device, id, requireStillActive = true)
    }

    /** Retente une connexion vers la TV actuellement utilisée (bouton "Actualiser"). */
    fun refreshActiveDevice() {
        val id = repository.activeDeviceId.value ?: return
        val device = repository.getDevice(id) ?: return
        attemptConnection(device, id, requireStillActive = true)
    }

    fun startPairing(device: TvDevice) {
        scope.launch {
            pairingManager.startPairing(device)
        }
    }

    fun submitPin(pin: String, deviceId: String) {
        scope.launch {
            repository.updateStatus(deviceId, TvConnectionStatus.CONNECTING)

            val success = pairingManager.verifyPin(pin)

            FoxLogger.i("FOX-TV | verifyPin result = $success")

            if (success) {
                // L'empreinte vient d'être enregistrée directement par
                // TvPairingManager.verifyPin() (PIN = confirmation physique
                // de l'utilisateur).
                val saved = FoxTvSettings.getDevice(context, deviceId)
                if (saved != null) {
                    repository.upsertPairedDevice(saved.copy(status = TvConnectionStatus.CONNECTED))
                }
                // Ne devient active que s'il s'agit de la toute première TV
                // jamais associée (déjà décidé par FoxTvSettings.savePairedDevice
                // au moment de l'enregistrement) -- resynchronise seulement
                // l'état live actif dans ce cas ; ne change JAMAIS une TV
                // déjà utilisée pour un nouvel appairage.
                if (repository.activeDeviceId.value == null) {
                    repository.setActiveDeviceId(FoxTvSettings.getActiveDeviceId(context))
                }
            } else {
                repository.updateStatus(deviceId, TvConnectionStatus.ERROR)
            }
        }
    }

    fun removeDevice(id: String) {
        FoxTvSettings.removePairedDevice(context, id)
        repository.removePairedDevice(id)
        repository.setActiveDeviceId(FoxTvSettings.getActiveDeviceId(context))
    }

    /**
     * Confirme qu'une TV sans empreinte enregistrée (ex: migrée depuis
     * l'ancien stockage) dont une connexion vient de réussir est bien la
     * bonne : enregistre alors l'empreinte ET l'adresse obtenues lors de
     * cette connexion. Ne fait rien si aucune confirmation n'est en
     * attente pour cet id.
     */
    fun confirmPendingIdentity(id: String) {
        val pending = pendingConfirmations.remove(id) ?: return
        if (_pendingConfirmation.value?.deviceId == id) _pendingConfirmation.value = null

        FoxTvSettings.updateFingerprint(context, id, pending.fingerprint)
        if (FoxTvSettings.getDevice(context, id)?.address != pending.proposedAddress) {
            FoxTvSettings.updateDeviceAddress(context, id, pending.proposedAddress)
        }

        val updated = FoxTvSettings.getDevice(context, id)?.copy(status = TvConnectionStatus.CONNECTED)
        if (updated != null) repository.upsertPairedDevice(updated)
    }

    /** L'utilisateur refuse ou ignore la confirmation : rien n'est enregistré. */
    fun dismissPendingIdentity(id: String) {
        pendingConfirmations.remove(id)
        if (_pendingConfirmation.value?.deviceId == id) _pendingConfirmation.value = null
        repository.updateStatus(id, TvConnectionStatus.OFFLINE)
    }

    /**
     * TV pouvant recevoir une commande AUTOMATIQUE (future pause
     * automatique, tâche #13) : exclusivement la TV utilisée, ET seulement
     * si son dernier état connu est CONNECTED. Décision déléguée à
     * TvCommandTarget (pure, testée) : aucune commande automatique ne peut
     * partir vers une TV hors ligne, ni en l'absence de TV active, ni vers
     * plusieurs TV à la fois.
     */
    fun commandTarget(): TvDevice? = TvCommandTarget.resolve(
        activeDeviceId = repository.activeDeviceId.value,
        pairedDevices = repository.pairedDeviceStates.value,
        requireConnected = true
    )

    /**
     * [requireStillActive] : pour les connexions qui représentent la TV
     * utilisée (démarrage, useDevice, refreshActiveDevice) — si la TV
     * utilisée a changé pendant la tentative, le résultat est ignoré
     * (jamais appliqué à une entrée qui n'est plus active). Pour les
     * vérifications de scan (adresse/nom déjà connus, TV non active), ce
     * garde n'a pas lieu d'être : false.
     */
    private fun attemptConnection(device: TvDevice, targetId: String, requireStillActive: Boolean) {
        if (!connectingIds.add(targetId)) {
            FoxLogger.i("FOX-TV | Connexion déjà en cours pour $targetId, nouvelle demande ignorée")
            return
        }

        scope.launch {
            try {
                repository.updateStatus(targetId, TvConnectionStatus.CONNECTING)

                val result = FoxTvController.testConnection(context, device.address)

                if (requireStillActive && repository.activeDeviceId.value != targetId) {
                    FoxLogger.i(
                        "FOX-TV | Résultat de connexion ignoré pour $targetId : " +
                                "n'est plus la TV utilisée"
                    )
                    return@launch
                }

                applyConnectionResult(targetId, device.address, result)
            } finally {
                connectingIds.remove(targetId)
            }
        }
    }

    private fun applyConnectionResult(
        id: String,
        connectedAtAddress: String,
        result: TvConnectionTestResult
    ) {
        val stored = FoxTvSettings.getDevice(context, id)

        val outcome = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = stored?.certificateFingerprint,
            storedAddress = stored?.address,
            connectedAtAddress = connectedAtAddress,
            result = result
        )

        FoxLogger.i(
            "FOX-TV | Résultat connexion ($id @ $connectedAtAddress) : " +
                    "$result -> $outcome"
        )

        when (outcome) {
            is TvConnectionOutcome.Reconnected -> {
                if (outcome.updateStoredAddress) {
                    FoxTvSettings.updateDeviceAddress(context, id, connectedAtAddress)
                }
                val updated = (stored ?: repository.getDevice(id))?.copy(
                    address = connectedAtAddress,
                    status = TvConnectionStatus.CONNECTED
                )
                if (updated != null) repository.upsertPairedDevice(updated)
            }

            TvConnectionOutcome.IdentityMismatch -> {
                FoxLogger.e(
                    "FOX-TV | Empreinte différente pour $id à $connectedAtAddress " +
                            "-> identité NON confirmée, rien n'est modifié"
                )
                repository.updateStatus(id, TvConnectionStatus.IDENTITY_MISMATCH)
            }

            is TvConnectionOutcome.ConfirmationRequired -> {
                val pending = PendingIdentityConfirmation(id, connectedAtAddress, outcome.fingerprint)
                pendingConfirmations[id] = pending
                _pendingConfirmation.value = pending
                repository.updateStatus(id, TvConnectionStatus.CONFIRMATION_REQUIRED)
            }

            TvConnectionOutcome.Offline -> {
                repository.updateStatus(id, TvConnectionStatus.OFFLINE)
            }

            TvConnectionOutcome.PairingRequired -> {
                repository.updateStatus(id, TvConnectionStatus.PAIRING_REQUIRED)
            }
        }
    }

    /**
     * Play/Pause manuel (`manual=true`, valeur par défaut, tous les appels
     * existants restent inchangés) : cible exclusivement la TV utilisée,
     * jamais une boucle. Contrairement à commandTarget() (réservé à
     * l'automatisation), n'exige pas un statut CONNECTED en cache : une
     * action manuelle explicite tente réellement, plutôt que de se fier à
     * un statut potentiellement périmé.
     *
     * `manual=false` (2026-08-16, réservé à `RealTvController`, jamais
     * appelé directement ailleurs) : la pause AUTOMATIQUE du sommeil ne
     * doit surtout pas se signaler elle-même comme une "reprise manuelle"
     * — voir `TvAutoPowerOffCoordinator` et `FoxForegroundService` pour
     * l'extinction automatique après 10 min sans reprise.
     */
    fun pause(manual: Boolean = true) {
        if (manual) {
            TvAutoPowerOffCoordinator.notifyManualInteraction()
        }
        scope.launch {
            val device = activeDevice.value

            if (device == null) {
                FoxLogger.e(
                    "FOX-TV | Pause failed: aucune TV utilisée"
                )
                return@launch
            }

            val result =
                FoxTvController.togglePlayPause(
                    context = context,
                    tvIp = device.address
                )

            result.onSuccess {
                FoxLogger.i(
                    "FOX-TV | PLAY/PAUSE sent successfully"
                )
            }

            result.onFailure { error ->
                FoxLogger.e(
                    "FOX-TV | PLAY/PAUSE failed",
                    error
                )

                repository.updateStatus(device.id, TvConnectionStatus.ERROR)
            }
        }
    }

    fun play() {
        pause()
    }

    /**
     * Extinction automatique (2026-08-16, demande explicite) : envoyée par
     * `FoxForegroundService` quand la pause du sommeil (voir
     * `SleepPauseCoordinator.executePause()`) n'a été suivie d'aucune
     * interaction manuelle avec la télécommande FoxOFF pendant 10 minutes
     * (voir `TvAutoPowerOffCoordinator`) — signal indirect, FoxOFF n'a
     * aucun moyen d'interroger l'état réel de lecture de la TV (voir
     * commentaire de `pause()` ci-dessus et de `FoxForegroundService
     * .startHeartbeat()`).
     */
    fun powerOff() {
        scope.launch {
            val device = activeDevice.value

            if (device == null) {
                FoxLogger.e(
                    "FOX-TV | Power off failed: aucune TV utilisée"
                )
                return@launch
            }

            val result =
                FoxTvController.powerOff(
                    context = context,
                    tvIp = device.address
                )

            result.onSuccess {
                FoxLogger.i(
                    "FOX-TV | POWER OFF sent successfully"
                )
            }

            result.onFailure { error ->
                FoxLogger.e(
                    "FOX-TV | POWER OFF failed",
                    error
                )

                repository.updateStatus(device.id, TvConnectionStatus.ERROR)
            }
        }
    }

    /*
     * FoxOFF ne doit piloter que Play/Pause (+ l'extinction automatique
     * ci-dessus, seule exception délibérée, voir powerOff()).
     * Ces fonctions restent présentes pour ne pas casser l'interface actuelle,
     * mais elles ne font volontairement rien.
     */
    fun back() = Unit

    fun home() = Unit

    fun volumeUp() = Unit

    fun volumeDown() = Unit

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }
}
