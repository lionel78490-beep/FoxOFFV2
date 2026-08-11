package com.projectfox.foxoff.tv

import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * État live de TOUTES les TV mémorisées (une par id), plus la liste des
 * résultats de scan (éphémère). Une seule TV peut être "utilisée"
 * (activeDeviceId) à la fois : c'est la seule à être reconnectée
 * automatiquement, la seule pouvant recevoir une commande, et la seule
 * affichée comme "Connectée" — les autres restent "Associée" quel que soit
 * leur statut interne (voir FoxTvEngine, RemoteScreen).
 */
class TvStateRepository {

    private val _pairedDeviceStates = MutableStateFlow<Map<String, TvDevice>>(emptyMap())
    val pairedDeviceStates: StateFlow<Map<String, TvDevice>> = _pairedDeviceStates.asStateFlow()

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    val activeDeviceId: StateFlow<String?> = _activeDeviceId.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TvDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TvDevice>> = _discoveredDevices.asStateFlow()

    // -----------------------------------------------------------------
    // TV mémorisées (état live)
    // -----------------------------------------------------------------

    fun setPairedDevices(devices: List<TvDevice>) {
        _pairedDeviceStates.value = devices.associateBy { it.id }
    }

    fun upsertPairedDevice(device: TvDevice) {
        _pairedDeviceStates.update { it + (device.id to device) }
    }

    fun updateStatus(id: String, status: TvConnectionStatus) {
        _pairedDeviceStates.update { map ->
            val existing = map[id] ?: return@update map
            map + (id to existing.copy(status = status))
        }
    }

    fun removePairedDevice(id: String) {
        _pairedDeviceStates.update { it - id }
        if (_activeDeviceId.value == id) _activeDeviceId.value = null
    }

    fun getDevice(id: String): TvDevice? = _pairedDeviceStates.value[id]

    fun setActiveDeviceId(id: String?) {
        _activeDeviceId.value = id
    }

    // -----------------------------------------------------------------
    // Résultats de scan (éphémère, inchangé)
    // -----------------------------------------------------------------

    fun addDiscoveredDevice(device: TvDevice) {
        _discoveredDevices.update { current ->
            val existing = current.find { it.address == device.address }
            if (existing != null) {
                val mergedServices = (existing.services + device.services).distinct()
                val mergedPorts = (existing.openPorts + device.openPorts).distinct()
                val mergedTxt = existing.txtRecords + device.txtRecords
                val merged = existing.copy(
                    name = if (device.name != device.id) device.name else existing.name,
                    services = mergedServices,
                    openPorts = mergedPorts,
                    txtRecords = mergedTxt,
                    manufacturer = device.manufacturer ?: existing.manufacturer,
                    model = device.model ?: existing.model
                )
                FoxLogger.i("FOX-TV | Device mis à jour dans la liste UI | Taille: ${current.size}")
                current.map { if (it.address == device.address) merged else it }
            } else {
                FoxLogger.i("FOX-TV | Device ajouté à la liste UI | Taille: ${current.size + 1}")
                current + device
            }
        }
    }

    /**
     * Vide la liste des appareils découverts, pour qu'un nouveau scan
     * ("Relancer le scan") reparte visiblement de zéro plutôt que de garder
     * d'anciens résultats potentiellement plus présents sur le réseau.
     */
    fun clearDiscoveredDevices() {
        _discoveredDevices.value = emptyList()
    }
}
