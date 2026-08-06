package com.projectfox.foxoff.tv

import android.content.Context
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FoxTvEngine(
    private val context: Context
) {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val keyStore = TvKeyStore(context)
    private val repository = TvStateRepository()

    private val discoveryManager =
        TvDiscoveryManager(context) { device ->
            repository.addDiscoveredDevice(device)
        }

    private val pairingManager =
        TvPairingManager(context) { status ->
            repository.updateStatus(status)
        }

    val state: StateFlow<TvDevice?> =
        repository.currentDevice

    val discoveredDevices: StateFlow<List<TvDevice>> =
        repository.discoveredDevices

    fun initialize() {
        discoveryManager.startDiscovery()
    }

    fun selectDevice(device: TvDevice) {
        FoxLogger.i(
            "FOX-TV | User selected device: " +
                    "${device.name} (${device.address})"
        )

        repository.updateDevice(device)

        /*
         * Le nouvel appairage sauvegarde l'identité TLS et l'adresse IP.
         * L'ancien TvKeyStore reste temporairement utilisé ici uniquement
         * pour conserver la compatibilité avec l'interface actuelle.
         */
        if (keyStore.getPairingKey(device.id) != null) {
            connect(device)
        } else {
            repository.updateStatus(
                TvConnectionStatus.PAIRING_REQUIRED
            )
        }
    }

    fun startPairing(device: TvDevice) {
        scope.launch {
            pairingManager.startPairing(device)
        }
    }

    fun submitPin(pin: String) {
        scope.launch {
            repository.updateStatus(
                TvConnectionStatus.CONNECTING
            )

            val success =
                pairingManager.verifyPin(pin)

            FoxLogger.i(
                "FOX-TV | verifyPin result = $success"
            )

            if (success) {
                repository.updateStatus(
                    TvConnectionStatus.CONNECTED
                )
            } else {
                repository.updateStatus(
                    TvConnectionStatus.ERROR
                )
            }
        }
    }

    fun connect(device: TvDevice) {
        repository.updateDevice(device)
        repository.updateStatus(
            TvConnectionStatus.CONNECTED
        )
    }

    fun pause() {
        scope.launch {
            val device = state.value

            if (device == null) {
                FoxLogger.e(
                    "FOX-TV | Pause failed: no selected device"
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

                repository.updateStatus(
                    TvConnectionStatus.ERROR
                )
            }
        }
    }

    fun play() {
        pause()
    }

    /*
     * FoxOFF ne doit piloter que Play/Pause.
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