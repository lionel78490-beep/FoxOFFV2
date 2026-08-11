package com.projectfox.foxoff.core.watch

/**
 * Appareil montre vu par un transport, quelle que soit la marque —
 * `id` est l'identifiant natif du transport (ex. Node.id GMS côté Wear OS ;
 * un futur GarminTransport utiliserait l'identifiant IQDevice).
 */
data class WatchTransportDevice(val id: String, val displayName: String)

/**
 * Abstraction des actions réseau que WatchPresenceCoordinator et
 * FoxCore.discoverWatch() effectuent aujourd'hui directement via l'API GMS
 * Wearable (Data Layer). Introduite pour permettre un futur GarminTransport
 * (Connect IQ Mobile SDK) sans dupliquer la logique de présence/reconnexion
 * (WatchPresenceEngine/WatchReconnectionEngine, déjà agnostiques de la
 * marque — ne dépendent que de lambdas d'effets).
 *
 * Seule implémentation actuelle : WearOsTransport.
 */
interface WatchTransport {
    /** Appareils actuellement joignables (équivalent connectedNodes() GMS). */
    suspend fun connectedDevices(): List<WatchTransportDevice>

    /**
     * Demande à l'appareil de répondre avec ses informations
     * (équivalent de l'envoi `/foxoff/request_watch_info`). Renvoie `true`
     * si la demande a été envoyée avec succès (pas une preuve de réponse —
     * la preuve arrive plus tard via SensorEvent.WatchInfoReceived).
     */
    suspend fun requestInfo(deviceId: String): Boolean

    /**
     * Envoie une commande arbitraire (chemin + charge utile) à l'appareil —
     * utilisé notamment pour `/foxoff/confirm_awake` (confirmation avant
     * pause auto, voir SleepPauseCoordinator). Best-effort comme
     * requestInfo() : `true` signifie "envoyé", pas "reçu".
     */
    suspend fun sendCommand(deviceId: String, path: String, payload: ByteArray = ByteArray(0)): Boolean
}
