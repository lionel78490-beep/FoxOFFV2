package com.projectfox.foxoff.tv

import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import java.io.ByteArrayOutputStream

/**
 * Porte fidèle du protocole de contrôle v2 (androidtvremote2).
 */
class TvConnectionManager(
    private val keyStore: TvKeyStore,
    private val onStatusChanged: (TvConnectionStatus) -> Unit
) {
    private var controlSocket: SSLSocket? = null

    private fun writeVarint(output: java.io.OutputStream, value: Int) {
        var v = value
        while (v >= 0x80) {
            output.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        output.write(v)
    }

    private fun readVarint(input: java.io.InputStream): Int {
        var value = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b == -1) return 0
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return value
    }

    private fun writeMessage(payload: ByteArray) {
        val output = controlSocket?.outputStream ?: return
        writeVarint(output, payload.size)
        output.write(payload)
        output.flush()
        FoxLogger.i("FOX-TV | Control | Sent [Varint ${payload.size}]: ${payload.joinToString("") { "%02x".format(it) }}")
    }

    private fun readMessage(): ByteArray {
        val input = controlSocket?.inputStream ?: return byteArrayOf()
        val length = readVarint(input)
        if (length <= 0) return byteArrayOf()
        val buffer = ByteArray(length)
        var total = 0
        while (total < length) {
            val r = input.read(buffer, total, length - total)
            if (r == -1) break
            total += r
        }
        FoxLogger.i("FOX-TV | Control | Received [Varint $length]: ${buffer.joinToString("") { "%02x".format(it) }}")
        return buffer
    }

    private fun buildConfigureMessage(): ByteArray {
        val deviceInfo = ByteArrayOutputStream()
        deviceInfo.write(0x0a); deviceInfo.write(7); deviceInfo.write("Android".toByteArray()) // 1: model
        deviceInfo.write(0x12); deviceInfo.write(6); deviceInfo.write("FoxOFF".toByteArray())  // 2: vendor
        deviceInfo.write(0x18); deviceInfo.write(0x01) // 3: unknown = 1
        
        val inner = ByteArrayOutputStream()
        inner.write(0x08); inner.write(0xee); inner.write(0x04) // 1: code = 622
        inner.write(0x12); inner.write(deviceInfo.size()) // 2: device_info tag
        inner.write(deviceInfo.toByteArray())
        
        val envelope = ByteArrayOutputStream()
        envelope.write(0x0a); envelope.write(inner.size()) // tag 1: remote_configure
        envelope.write(inner.toByteArray())
        return envelope.toByteArray()
    }

    private fun buildSetActiveMessage(): ByteArray {
        val inner = ByteArrayOutputStream()
        inner.write(0x08); inner.write(0x01) // 1: active = 1
        
        val envelope = ByteArrayOutputStream()
        envelope.write(0x22); envelope.write(inner.size()) // tag 4: remote_set_active
        envelope.write(inner.toByteArray())
        return envelope.toByteArray()
    }

    suspend fun connect(device: TvDevice) = withContext(Dispatchers.IO) {
        try {
            onStatusChanged(TvConnectionStatus.CONNECTING)
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(device.address, 6467), 5000)
            
            controlSocket = keyStore.getSslContext().socketFactory.createSocket(rawSocket, device.address, 6467, true) as SSLSocket
            keyStore.logTlsDetails(controlSocket!!)
            
            controlSocket?.addHandshakeCompletedListener { event ->
                FoxLogger.i("FOX-TV | HandshakeCompletedListener")
                FoxLogger.i("FOX-TV | Protocol: ${event.session.protocol}")
                FoxLogger.i("FOX-TV | CipherSuite: ${event.session.cipherSuite}")
            }

            FoxLogger.i("FOX-TV | TLS | Handshake START on port 6467")
            try {
                controlSocket?.startHandshake()
            } catch (e: Exception) {
                FoxLogger.e("FOX-TV | TLS | Handshake FAILED: ${e.message}")
                throw e
            }

            // 1. Send Configure
            writeMessage(buildConfigureMessage())
            FoxLogger.i("FOX-TV | Control | RemoteConfigure sent")
            
            // 2. Wait for RemoteConfiguration (Ack)
            val resp = readMessage()
            if (resp.isNotEmpty() && resp[0].toInt() == 0x0a) { 
                FoxLogger.i("FOX-TV | REMOTE AUTHENTICATED")
                
                // 3. Send SetActive
                writeMessage(buildSetActiveMessage())
                onStatusChanged(TvConnectionStatus.CONNECTED)
                
                // Start listening for pings/updates in background
                scope.launch {
                    listenForMessages()
                }
            } else {
                FoxLogger.e("FOX-TV | REMOTE REJECTED")
                onStatusChanged(TvConnectionStatus.ERROR)
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-TV | Control | Connection failed", e)
            onStatusChanged(TvConnectionStatus.ERROR)
        }
    }

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private fun listenForMessages() {
        try {
            while (controlSocket?.isConnected == true) {
                val msg = readMessage()
                if (msg.isEmpty()) break

                // If tag is 0x1a (remote_ping), send 0x22 0x02 0x08 0x01 (remote_pong)
                if (msg[0].toInt() == 0x1a) {
                    FoxLogger.i("FOX-TV | Control | Ping received, sending Pong")
                    writeMessage(byteArrayOf(0x22, 0x02, 0x08, 0x01))
                }
            }
        } catch (e: Exception) {
            FoxLogger.e("FOX-TV | Control | Listener error", e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            controlSocket?.close()
            controlSocket = null
            onStatusChanged(TvConnectionStatus.DISCONNECTED)
        } catch (e: Exception) {
            FoxLogger.e("TV Disconnect error", e)
        }
    }

    suspend fun sendRaw(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            writeMessage(data)
            true
        } catch (e: Exception) {
            false
        }
    }
}
