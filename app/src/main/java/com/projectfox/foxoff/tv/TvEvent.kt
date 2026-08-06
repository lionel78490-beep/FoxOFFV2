package com.projectfox.foxoff.tv

sealed class TvEvent {
    data class DeviceFound(val device: TvDevice) : TvEvent()
    data class ConnectionStatusChanged(val status: TvConnectionStatus) : TvEvent()
    data class AppChanged(val appName: String?) : TvEvent()
    data class PlaybackStateChanged(val isPlaying: Boolean) : TvEvent()
    data class CommandResult(val command: String, val success: Boolean, val responseTimeMs: Long) : TvEvent()
    data class Error(val message: String) : TvEvent()
}
