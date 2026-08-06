package com.projectfox.foxoff.tvlab

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import remote.Remotemessage
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket

object TvRemoteConnection {

    private const val REMOTE_PORT = 6466

    private const val FEATURE_PING = 1
    private const val FEATURE_KEY = 2
    private const val FEATURE_POWER = 32
    private const val FEATURE_VOLUME = 64
    private const val FEATURE_APP_LINK = 512

    private const val REQUESTED_FEATURES =
        FEATURE_PING or
                FEATURE_KEY or
                FEATURE_POWER or
                FEATURE_VOLUME or
                FEATURE_APP_LINK

    suspend fun connectAndPause(
        context: Context,
        ip: String
    ): String = withContext(Dispatchers.IO) {

        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null

        try {
            rawSocket = Socket().apply {
                connect(
                    InetSocketAddress(ip, REMOTE_PORT),
                    5000
                )
                soTimeout = 10_000
            }

            val identity = TvLabIdentity(
                context.applicationContext
            )

            sslSocket = identity.createSslContext()
                .socketFactory
                .createSocket(
                    rawSocket,
                    ip,
                    REMOTE_PORT,
                    true
                ) as SSLSocket

            sslSocket.useClientMode = true
            sslSocket.soTimeout = 10_000
            sslSocket.startHandshake()

            val input = sslSocket.inputStream
            val output = sslSocket.outputStream

            var remoteReady = false
            var attempts = 0

            while (!remoteReady && attempts < 20) {
                attempts++

                val message = readMessage(input)

                when {
                    message.hasRemoteConfigure() -> {
                        val configureResponse =
                            Remotemessage.RemoteConfigure.newBuilder()
                                .setCode1(REQUESTED_FEATURES)
                                .setDeviceInfo(
                                    Remotemessage.RemoteDeviceInfo.newBuilder()
                                        .setUnknown1(1)
                                        .setUnknown2("1")
                                        .setPackageName("atvremote")
                                        .setAppVersion("1.0.0")
                                        .build()
                                )
                                .build()

                        val response =
                            Remotemessage.RemoteMessage.newBuilder()
                                .setRemoteConfigure(configureResponse)
                                .build()

                        writeMessage(output, response)
                    }

                    message.hasRemoteSetActive() -> {
                        val activeResponse =
                            Remotemessage.RemoteSetActive.newBuilder()
                                .setActive(REQUESTED_FEATURES)
                                .build()

                        val response =
                            Remotemessage.RemoteMessage.newBuilder()
                                .setRemoteSetActive(activeResponse)
                                .build()

                        writeMessage(output, response)
                    }

                    message.hasRemotePingRequest() -> {
                        val pingResponse =
                            Remotemessage.RemotePingResponse.newBuilder()
                                .setVal1(
                                    message.remotePingRequest.val1
                                )
                                .build()

                        val response =
                            Remotemessage.RemoteMessage.newBuilder()
                                .setRemotePingResponse(pingResponse)
                                .build()

                        writeMessage(output, response)
                    }

                    message.hasRemoteStart() -> {
                        remoteReady = true
                    }
                }
            }

            check(remoteReady) {
                "La Freebox n’a pas confirmé RemoteStart"
            }

            val keyInject =
                Remotemessage.RemoteKeyInject.newBuilder()
                    .setKeyCode(
                        Remotemessage.RemoteKeyCode
                            .KEYCODE_MEDIA_PLAY_PAUSE
                    )
                    .setDirection(
                        Remotemessage.RemoteDirection.SHORT
                    )
                    .build()

            val pauseMessage =
                Remotemessage.RemoteMessage.newBuilder()
                    .setRemoteKeyInject(keyInject)
                    .build()

            writeMessage(output, pauseMessage)
            Thread.sleep(500)


            "✅ REMOTE CONNECTÉ\n⏸️ COMMANDE PAUSE ENVOYÉE"

        } catch (e: Exception) {
            buildString {
                appendLine("❌ REMOTE ÉCHEC")
                appendLine(e.javaClass.simpleName)
                append(e.message ?: "Erreur sans message")
            }
        } finally {
            try {
                sslSocket?.close()
            } catch (_: Exception) {
            }

            try {
                rawSocket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeMessage(
        output: OutputStream,
        message: Remotemessage.RemoteMessage
    ) {
        val payload = message.toByteArray()

        writeVarInt(output, payload.size)
        output.write(payload)
        output.flush()
    }

    private fun readMessage(
        input: InputStream
    ): Remotemessage.RemoteMessage {
        val length = readVarInt(input)

        require(length in 1..1_000_000) {
            "Longueur Remote invalide : $length"
        }

        val payload = ByteArray(length)
        var offset = 0

        while (offset < length) {
            val count = input.read(
                payload,
                offset,
                length - offset
            )

            if (count == -1) {
                error("Connexion Remote fermée pendant la lecture")
            }

            offset += count
        }

        return Remotemessage.RemoteMessage.parseFrom(payload)
    }

    private fun writeVarInt(
        output: OutputStream,
        value: Int
    ) {
        var remaining = value

        while (true) {
            if ((remaining and 0x7F.inv()) == 0) {
                output.write(remaining)
                return
            }

            output.write(
                (remaining and 0x7F) or 0x80
            )

            remaining = remaining ushr 7
        }
    }

    private fun readVarInt(
        input: InputStream
    ): Int {
        var result = 0
        var shift = 0

        while (shift < 32) {
            val byte = input.read()

            if (byte == -1) {
                error("Connexion Remote fermée avant la longueur")
            }

            result = result or
                    ((byte and 0x7F) shl shift)

            if ((byte and 0x80) == 0) {
                return result
            }

            shift += 7
        }

        error("VarInt Remote trop long")
    }
}