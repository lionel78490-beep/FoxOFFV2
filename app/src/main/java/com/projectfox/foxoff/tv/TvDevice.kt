package com.projectfox.foxoff.tv

data class TvDevice(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
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
    ERROR
}
