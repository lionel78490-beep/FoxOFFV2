package com.projectfox.foxoff.tv

data class TvDevice(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    // Empreinte SHA-256 du certificat serveur présenté lors de l'appairage
    // ou d'une connexion réussie. null tant qu'elle n'a jamais été
    // confirmée (jamais renseignée automatiquement après une simple
    // connexion — voir TvIdentityVerifier). Sert à distinguer la vraie TV
    // mémorisée d'un autre appareil qui accepterait notre identité client
    // globale (TvIdentity) à une adresse réattribuée.
    val certificateFingerprint: String? = null,
    val status: TvConnectionStatus = TvConnectionStatus.DISCONNECTED,
    val currentApp: String? = null,
    val isPlaying: Boolean = false,
    val lastResponseTimeMs: Long = 0,
    val manufacturer: String? = null,
    val model: String? = null,
    val discoveryType: String = "Unknown",
    val services: List<String> = emptyList(),
    val openPorts: List<Int> = emptyList(),
    val txtRecords: Map<String, String> = emptyMap()
)

enum class TvConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    PAIRING_REQUIRED,
    PAIRING_SENT,
    CONNECTING,
    CONNECTED,
    // Appareil mémorisé (déjà appairé par le passé) mais actuellement
    // injoignable sur le réseau — distinct de DISCONNECTED (aucun appareil
    // connu du tout).
    OFFLINE,
    // Connexion TLS réussie, mais l'empreinte du certificat serveur diffère
    // de celle mémorisée : un autre appareil occupe cette adresse. Jamais
    // traité comme une reconnexion réussie, rien n'est modifié.
    IDENTITY_MISMATCH,
    // Connexion TLS réussie, mais aucune empreinte n'était mémorisée pour
    // cette TV (ex: migrée depuis l'ancien stockage) : confirmation
    // explicite de l'utilisateur requise avant d'enregistrer la première
    // empreinte (voir FoxTvEngine.pendingConfirmation).
    CONFIRMATION_REQUIRED,
    ERROR
}
