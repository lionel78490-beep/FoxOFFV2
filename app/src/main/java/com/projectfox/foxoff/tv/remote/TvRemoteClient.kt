package com.projectfox.foxoff.tv.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import remote.Remotemessage
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import com.projectfox.foxoff.tv.TvCertificateFingerprint
import com.projectfox.foxoff.tv.TvConnectionTestResult
import com.projectfox.foxoff.tv.pairing.TvIdentity


object TvRemoteClient {

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

        val identity = TvIdentity(
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

/**
 * Extinction automatique de la TV (2026-08-16, demande explicite : si la
 * lecture n'a pas été reprise manuellement 10 min après la pause auto du
 * sommeil, éteindre complètement la TV plutôt que la laisser en pause).
 * `KEYCODE_POWER` existe déjà dans le protocole (FEATURE_POWER déjà
 * négociée dans REQUESTED_FEATURES ci-dessus) mais n'était utilisée nulle
 * part jusqu'ici — seul KEYCODE_MEDIA_PLAY_PAUSE était envoyé.
 *
 * Duplique volontairement la séquence de handshake de connectAndPause()
 * (même raison que testConnection() ci-dessous : ne prendre aucun risque de
 * régression sur le chemin Play/Pause manuel déjà validé par l'utilisateur).
 */
suspend fun connectAndPowerOff(
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

        val identity = TvIdentity(
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
            "La TV n'a pas confirmé RemoteStart"
        }

        val keyInject =
            Remotemessage.RemoteKeyInject.newBuilder()
                .setKeyCode(
                    Remotemessage.RemoteKeyCode
                        .KEYCODE_POWER
                )
                .setDirection(
                    Remotemessage.RemoteDirection.SHORT
                )
                .build()

        val powerOffMessage =
            Remotemessage.RemoteMessage.newBuilder()
                .setRemoteKeyInject(keyInject)
                .build()

        writeMessage(output, powerOffMessage)
        Thread.sleep(500)

        "✅ REMOTE CONNECTÉ\n🔌 COMMANDE POWER ENVOYÉE"

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

/**
 * Vérifie une connexion de contrôle réelle (handshake TLS + RemoteConfigure
 * + attente de RemoteStart) SANS injecter de commande, pour ne pas produire
 * d'action involontaire sur la TV pendant une simple vérification d'état.
 *
 * Distingue explicitement :
 * - NetworkUnreachable : TV éteinte, adresse injoignable, délai dépassé,
 *   réseau absent... rien n'indique un rejet d'identité -> la TV mémorisée
 *   doit rester affichée "Hors ligne" et être retentée plus tard, sans
 *   jamais être effacée.
 * - AuthRejected : le handshake TLS lui-même échoue explicitement
 *   (SSLHandshakeException) -> seul ce cas doit proposer un nouvel
 *   appairage avec PIN.
 *
 * Duplique volontairement la séquence de handshake de connectAndPause()
 * plutôt que de la factoriser, pour ne prendre aucun risque de régression
 * sur le chemin Play/Pause manuel déjà validé par l'utilisateur.
 */
suspend fun testConnection(
    context: Context,
    ip: String
): TvConnectionTestResult = withContext(Dispatchers.IO) {

    var rawSocket: Socket? = null
    var sslSocket: SSLSocket? = null

    try {
        rawSocket = Socket()
        try {
            rawSocket.connect(
                InetSocketAddress(ip, REMOTE_PORT),
                5000
            )
            rawSocket.soTimeout = 10_000
        } catch (e: Exception) {
            return@withContext TvConnectionTestResult.NetworkUnreachable
        }

        val identity = TvIdentity(
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

        try {
            sslSocket.startHandshake()
        } catch (e: SSLHandshakeException) {
            return@withContext TvConnectionTestResult.AuthRejected
        } catch (e: Exception) {
            return@withContext TvConnectionTestResult.NetworkUnreachable
        }

        // Empreinte du certificat SERVEUR présenté pendant CETTE connexion.
        // Une simple réussite de handshake ne prouve que l'acceptation de
        // notre identité client (globale, partagée entre toutes les TV) —
        // pas qu'il s'agit de la bonne TV : la comparaison à l'empreinte
        // mémorisée se fait plus haut dans FoxTvEngine (TvIdentityVerifier).
        val serverFingerprint = try {
            val serverCertificate = sslSocket.session.peerCertificates.firstOrNull()
                    as? java.security.cert.X509Certificate
                ?: return@withContext TvConnectionTestResult.NetworkUnreachable
            TvCertificateFingerprint.of(serverCertificate)
        } catch (e: Exception) {
            return@withContext TvConnectionTestResult.NetworkUnreachable
        }

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

        if (remoteReady) {
            TvConnectionTestResult.Connected(serverFingerprint)
        } else {
            TvConnectionTestResult.NetworkUnreachable
        }

    } catch (e: Exception) {
        TvConnectionTestResult.NetworkUnreachable
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