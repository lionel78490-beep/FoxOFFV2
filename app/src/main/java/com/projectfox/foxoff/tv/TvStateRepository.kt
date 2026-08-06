package com.projectfox.foxoff.tv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TvStateRepository {
    private val _discoveredDevices = MutableStateFlow<List<TvDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TvDevice>> = _discoveredDevices.asStateFlow()

    private val _currentDevice = MutableStateFlow<TvDevice?>(null)
    val currentDevice: StateFlow<TvDevice?> = _currentDevice.asStateFlow()

    fun addDiscoveredDevice(device: TvDevice) {
        _discoveredDevices.update { current ->
            val existing = current.find { it.address == device.address }
            if (existing != null) {
                // Merge info
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
                com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | Device mis à jour dans la liste UI | Taille: ${current.size}")
                current.map { if (it.address == device.address) merged else it }
            } else {
                com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | Device ajouté à la liste UI | Taille: ${current.size + 1}")
                current + device
            }
        }
    }

    fun updateDevice(device: TvDevice) {
        _currentDevice.value = device
    }

    fun updateStatus(status: TvConnectionStatus) {
        _currentDevice.update { it?.copy(status = status) }
    }

    fun updateApp(appName: String?) {
        _currentDevice.update { it?.copy(currentApp = appName) }
    }

    fun updatePlayback(isPlaying: Boolean) {
        _currentDevice.update { it?.copy(isPlaying = isPlaying) }
    }

    fun updateLastResponse(timeMs: Long) {
        _currentDevice.update { it?.copy(lastResponseTimeMs = timeMs) }
    }
}
