package com.projectfox.foxoff.tv

import com.projectfox.foxoff.core.logging.FoxLogger
import java.io.ByteArrayOutputStream

class TvCommandSender(private val connectionManager: TvConnectionManager) {

    suspend fun sendKeyCode(device: TvDevice, keyCode: Int): Boolean {
        FoxLogger.i("FOX-TV | Command | KeyCode $keyCode")
        
        // RemoteKeyInject message (Inner)
        val inner = ByteArrayOutputStream()
        inner.write(0x08); inner.write(keyCode) // 1: key_code
        inner.write(0x10); inner.write(0x02) // 2: direction = SHORT_CLICK (2)
        
        // Envelope RemoteMessage
        val envelope = ByteArrayOutputStream()
        envelope.write(0x52); envelope.write(inner.size()) // Tag 10: remote_key_inject (10 << 3 | 2 = 0x52)
        envelope.write(inner.toByteArray())
        
        return connectionManager.sendRaw(envelope.toByteArray())
    }

    suspend fun pause(device: TvDevice) = sendKeyCode(device, 85) // KEYCODE_MEDIA_PLAY_PAUSE
    suspend fun play(device: TvDevice) = sendKeyCode(device, 85)
    suspend fun stop(device: TvDevice) = sendKeyCode(device, 86)
    suspend fun home(device: TvDevice) = sendKeyCode(device, 3)
    suspend fun back(device: TvDevice) = sendKeyCode(device, 4)
    suspend fun volumeUp(device: TvDevice) = sendKeyCode(device, 24)
    suspend fun volumeDown(device: TvDevice) = sendKeyCode(device, 25)
}
