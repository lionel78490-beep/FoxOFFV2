package com.projectfox.foxoff.core.watch

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.core.presence.WatchPresenceCoordinator
import com.projectfox.foxoff.sensors.events.SensorEvent
import com.projectfox.foxoff.sensors.model.HeartRateSample
import com.projectfox.foxoff.sensors.model.SensorBackend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * Implémentation WatchTransport pour Garmin, via le Connect IQ Mobile SDK
 * (`com.garmin.connectiq:ciq-companion-app-sdk`, gratuit, Maven Central —
 * à ne pas confondre avec le "Companion SDK"/Health API, commercial, écarté
 * pour ce projet, voir le plan). Signatures vérifiées par décompilation du
 * vrai AAR 2.2.0 (pas devinées) : `ConnectIQ`, `IQDevice`, `IQApp`.
 *
 * NON VALIDÉ SUR MATÉRIEL RÉEL : le format exact des messages
 * (`onMessageReceived` → `List<Object>`) est déduit du comportement
 * documenté du SDK (un Dictionary Monkey C arrive côté Android comme
 * `List<Object>` dont le premier élément est une `Map`), mais n'a pas pu
 * être vérifié avec une vraie montre Garmin. Le parsing ci-dessous est donc
 * défensif (accepte un Number brut OU une Map) — à confirmer/ajuster une
 * fois du matériel disponible.
 *
 * Contrairement à Wear OS (PhoneWearListenerService, réveillé par l'OS via
 * BIND_LISTENER), le Connect IQ Mobile SDK exige que le processus appelant
 * ait explicitement initialisé le SDK et soit vivant pour recevoir les
 * AppMessages — pas d'équivalent "service système" ici. C'est pourquoi
 * cette classe possède elle-même le cycle de vie ConnectIQ (contrairement à
 * WearOsTransport, simple wrapper sans état d'initialisation).
 */
class GarminTransport(
    private val context: Context,
    /**
     * Identifiant de l'app Connect IQ (UUID), DOIT correspondre exactement
     * au champ `id` du `manifest.xml` de l'app montre Monkey C (Phase C).
     * Pour un usage personnel (sideload, pas de publication sur le Store),
     * cet UUID est choisi librement — pas besoin d'enregistrement auprès de
     * Garmin. Valeur provisoire générée pour ce projet : à garder identique
     * des deux côtés (Kotlin + Monkey C), ne JAMAIS la régénérer séparément.
     */
    private val appId: String = GARMIN_APP_ID
) : WatchTransport {

    companion object {
        const val GARMIN_APP_ID = "b7e6f8a2-9c4d-4e1a-8f3b-2d5e6a7c9f10"
        private const val SDK_READY_TIMEOUT_MS = 10_000L
    }

    private val connectIQ: ConnectIQ =
        ConnectIQ.getInstance(context.applicationContext, ConnectIQ.IQConnectType.WIRELESS)

    private val app = IQApp(appId)

    private val sdkReady = CompletableDeferred<Boolean>()
    private var registeredDevices = mutableSetOf<Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * À appeler une fois avant tout usage (idempotent côté ConnectIQ, mais
     * PAS rappelé plusieurs fois ici — voir GarminTransportProvider si un
     * jour plusieurs points d'entrée en ont besoin).
     */
    fun start() {
        try {
            connectIQ.initialize(context.applicationContext, false, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    FoxLogger.i("FOX-GARMIN | SDK prêt")
                    if (!sdkReady.isCompleted) sdkReady.complete(true)
                    registerKnownDevices()
                }

                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                    FoxLogger.e("FOX-GARMIN | Échec initialisation SDK : $status")
                    if (!sdkReady.isCompleted) sdkReady.complete(false)
                }

                override fun onSdkShutDown() {
                    FoxLogger.i("FOX-GARMIN | SDK arrêté")
                }
            })
        } catch (e: Exception) {
            FoxLogger.e("FOX-GARMIN | Erreur lors de initialize()", e)
            if (!sdkReady.isCompleted) sdkReady.complete(false)
        }
    }

    private fun registerKnownDevices() {
        try {
            connectIQ.safeGetKnownDevices()?.forEach { device ->
                if (registeredDevices.add(device.deviceIdentifier)) {
                    registerForDevice(device)
                }
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-GARMIN | Échec enregistrement des appareils connus", e)
        }
    }

    private fun registerForDevice(device: IQDevice) {
        try {
            connectIQ.registerForDeviceEvents(device) { changedDevice, status ->
                FoxLogger.i("FOX-GARMIN | Statut appareil changé : ${changedDevice.friendlyName} -> $status")
            }
            connectIQ.registerForAppEvents(device, app) { sourceDevice, _, data, status ->
                onAppMessage(sourceDevice, data, status)
            }
            FoxLogger.i("FOX-GARMIN | Écoute enregistrée pour ${device.friendlyName}")
        } catch (e: InvalidStateException) {
            FoxLogger.e("FOX-GARMIN | SDK non prêt lors de l'enregistrement", e)
        }
    }

    /**
     * Format attendu du message (protocole propre à FoxOFF, voir Phase C) :
     * un Dictionary Monkey C `{"bpm" => Number, "battery" => Number}`, reçu
     * ici comme une `Map` en première position de la liste. Accepte aussi
     * un `Number` brut en position 0 par tolérance (voir avertissement en
     * tête de fichier — à confirmer sur matériel réel).
     */
    private fun onAppMessage(device: IQDevice, data: List<Any>, status: ConnectIQ.IQMessageStatus) {
        if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
            FoxLogger.e("FOX-GARMIN | Message reçu en échec : $status")
            return
        }
        val payload = data.firstOrNull()
        val bpm: Float? = when (payload) {
            is Map<*, *> -> (payload["bpm"] as? Number)?.toFloat()
            is Number -> payload.toFloat()
            else -> null
        }
        if (bpm == null) {
            FoxLogger.e("FOX-GARMIN | Message reçu au format inattendu : $payload")
            return
        }

        WatchPresenceCoordinator.recordProof()
        FoxLogger.i("FOX-GARMIN | BPM reçu : $bpm | Source : ${device.friendlyName}")

        val sample = HeartRateSample(
            bpm = bpm,
            accuracy = 3,
            timestamp = Instant.now(),
            backend = SensorBackend.UNKNOWN
        )
        scope.launch {
            FoxCore.eventBus.publish(SensorEvent.HeartRateReceived(sample))

            val battery = (payload as? Map<*, *>)?.get("battery") as? Number
            if (battery != null) {
                FoxCore.eventBus.publish(
                    SensorEvent.WatchInfoReceived(device.friendlyName, battery.toInt(), true)
                )
            }
        }
    }

    override suspend fun connectedDevices(): List<WatchTransportDevice> {
        val ready = withTimeoutOrNull(SDK_READY_TIMEOUT_MS) { sdkReady.await() }
        if (ready != true) {
            FoxLogger.e("FOX-GARMIN | connectedDevices() : SDK non prêt (timeout ou échec init)")
            return emptyList()
        }
        return try {
            connectIQ.getConnectedDevices()
                .map { WatchTransportDevice(id = it.deviceIdentifier.toString(), displayName = it.friendlyName) }
        } catch (e: InvalidStateException) {
            FoxLogger.e("FOX-GARMIN | connectedDevices() : SDK non initialisé", e)
            emptyList()
        } catch (e: ServiceUnavailableException) {
            FoxLogger.e("FOX-GARMIN | connectedDevices() : Garmin Connect Mobile indisponible", e)
            emptyList()
        }
    }

    override suspend fun requestInfo(deviceId: String): Boolean {
        val ready = withTimeoutOrNull(SDK_READY_TIMEOUT_MS) { sdkReady.await() }
        if (ready != true) return false

        val device = try {
            connectIQ.getConnectedDevices().firstOrNull { it.deviceIdentifier.toString() == deviceId }
        } catch (e: Exception) {
            FoxLogger.e("FOX-GARMIN | requestInfo() : échec résolution appareil", e)
            null
        } ?: return false

        val result = CompletableDeferred<Boolean>()
        try {
            connectIQ.sendMessage(device, app, "REQUEST_INFO") { _, _, status ->
                result.complete(status == ConnectIQ.IQMessageStatus.SUCCESS)
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-GARMIN | requestInfo() : échec envoi", e)
            return false
        }
        return withTimeoutOrNull(SDK_READY_TIMEOUT_MS) { result.await() } == true
    }

    /**
     * `payload` n'est pas utilisé : le protocole Connect IQ de ce projet
     * échange des messages string simples (voir requestInfo()), pas de
     * charge utile binaire arbitraire — `path` sert directement de
     * contenu du message. Non vérifié sur matériel (pas de montre Garmin
     * physique disponible), même statut que le reste de ce transport.
     */
    override suspend fun sendCommand(deviceId: String, path: String, payload: ByteArray): Boolean {
        val ready = withTimeoutOrNull(SDK_READY_TIMEOUT_MS) { sdkReady.await() }
        if (ready != true) return false

        val device = try {
            connectIQ.getConnectedDevices().firstOrNull { it.deviceIdentifier.toString() == deviceId }
        } catch (e: Exception) {
            FoxLogger.e("FOX-GARMIN | sendCommand() : échec résolution appareil", e)
            null
        } ?: return false

        val result = CompletableDeferred<Boolean>()
        try {
            connectIQ.sendMessage(device, app, path) { _, _, status ->
                result.complete(status == ConnectIQ.IQMessageStatus.SUCCESS)
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-GARMIN | sendCommand() : échec envoi", e)
            return false
        }
        return withTimeoutOrNull(SDK_READY_TIMEOUT_MS) { result.await() } == true
    }
}

/** `getKnownDevices()` renvoie null-safe même si le SDK grogne en interne. */
private fun ConnectIQ.safeGetKnownDevices(): List<IQDevice>? = try {
    getKnownDevices()
} catch (e: Exception) {
    FoxLogger.e("FOX-GARMIN | getKnownDevices() a échoué", e)
    null
}
