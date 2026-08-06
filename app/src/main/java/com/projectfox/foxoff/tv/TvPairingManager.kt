package com.projectfox.foxoff.tv

import android.content.Context
import com.google.polo.wire.protobuf.PoloProto
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.tv.pairing.TvIdentity
import com.projectfox.foxoff.tv.pairing.TvPairingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/**
 * Appairage Android TV Remote v2.
 *
 * Séquence :
 * PairingRequest
 * -> PairingRequestAck
 * -> Options
 * -> Configuration
 * -> ConfigurationAck
 * -> saisie du PIN
 * -> Secret
 * -> SecretAck
 */
class TvPairingManager(
    context: Context,
    private val onStatusChanged: (TvConnectionStatus) -> Unit
) {

    companion object {
        private const val PAIRING_PORT = 6467
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_MESSAGE_SIZE = 1_000_000
    }

    private val appContext = context.applicationContext
    private val identity = TvIdentity(appContext)

    private var pairingSocket: SSLSocket? = null
    private var currentDevice: TvDevice? = null

    suspend fun startPairing(
        device: TvDevice
    ) = withContext(Dispatchers.IO) {

        try {
            close()

            currentDevice = device
            onStatusChanged(TvConnectionStatus.PAIRING_REQUIRED)

            FoxLogger.i(
                "FOX-TV | Pairing | Connexion à ${device.address}:$PAIRING_PORT"
            )

            val rawSocket = Socket().apply {
                connect(
                    InetSocketAddress(
                        device.address,
                        PAIRING_PORT
                    ),
                    CONNECT_TIMEOUT_MS
                )

                soTimeout = READ_TIMEOUT_MS
            }

            val sslSocket = identity.createSslContext()
                .socketFactory
                .createSocket(
                    rawSocket,
                    device.address,
                    PAIRING_PORT,
                    true
                ) as SSLSocket

            sslSocket.useClientMode = true
            sslSocket.soTimeout = READ_TIMEOUT_MS
            sslSocket.startHandshake()

            pairingSocket = sslSocket

            FoxLogger.i("FOX-TV | Pairing | TLS OK")

            val input = sslSocket.inputStream
            val output = sslSocket.outputStream

            // 1. PairingRequest
            writeMessage(
                output,
                TvPairingClient.createPairingRequest()
            )

            val pairingAck = readMessage(input)

            checkStatus(pairingAck, "PairingRequestAck")

            check(pairingAck.hasPairingRequestAck()) {
                "PairingRequestAck attendu"
            }

            FoxLogger.i(
                "FOX-TV | Pairing | PairingRequestAck reçu"
            )

            // 2. Options
            writeMessage(
                output,
                TvPairingClient.createOptions()
            )

            val tvOptions = readMessage(input)

            checkStatus(tvOptions, "Options")

            check(tvOptions.hasOptions()) {
                "Options attendues"
            }

            FoxLogger.i(
                "FOX-TV | Pairing | Options reçues"
            )

            // 3. Configuration
            writeMessage(
                output,
                TvPairingClient.createConfiguration()
            )

            val configurationAck = readMessage(input)

            checkStatus(
                configurationAck,
                "ConfigurationAck"
            )

            check(configurationAck.hasConfigurationAck()) {
                "ConfigurationAck attendu"
            }

            FoxLogger.i(
                "FOX-TV | Pairing | ConfigurationAck reçu"
            )

            FoxLogger.i(
                "FOX-TV | Pairing | PIN affiché sur la TV"
            )

            onStatusChanged(TvConnectionStatus.PAIRING_SENT)

        } catch (e: Exception) {
            FoxLogger.e(
                "FOX-TV | Pairing | Échec du démarrage",
                e
            )

            close()
            onStatusChanged(TvConnectionStatus.ERROR)
        }
    }

    suspend fun verifyPin(
        pin: String
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            val normalizedPin = pin
                .trim()
                .uppercase()

            require(
                normalizedPin.length == 6 &&
                        normalizedPin.all {
                            it in '0'..'9' || it in 'A'..'F'
                        }
            ) {
                "Le PIN doit contenir 6 caractères hexadécimaux"
            }

            val socket = pairingSocket
                ?: error(
                    "La connexion d’appairage n’est plus ouverte"
                )

            val clientCertificate =
                identity.getCertificate()

            val serverCertificate =
                socket.session.peerCertificates.first()
                        as? X509Certificate
                    ?: error(
                        "Certificat de la TV indisponible"
                    )

            val clientPublicKey =
                clientCertificate.publicKey as? RSAPublicKey
                    ?: error(
                        "La clé client n’est pas RSA"
                    )

            val serverPublicKey =
                serverCertificate.publicKey as? RSAPublicKey
                    ?: error(
                        "La clé TV n’est pas RSA"
                    )

            val expectedFirstByte =
                normalizedPin.substring(0, 2)
                    .toInt(16)

            val pinBytes =
                normalizedPin.substring(2)
                    .chunked(2)
                    .map { value ->
                        value.toInt(16).toByte()
                    }
                    .toByteArray()

            val digest =
                MessageDigest.getInstance("SHA-256")

            digest.update(
                clientPublicKey.modulus
                    .toUnsignedByteArray()
            )

            digest.update(
                clientPublicKey.publicExponent
                    .toUnsignedByteArray()
            )

            digest.update(
                serverPublicKey.modulus
                    .toUnsignedByteArray()
            )

            digest.update(
                serverPublicKey.publicExponent
                    .toUnsignedByteArray()
            )

            digest.update(pinBytes)

            val secretHash = digest.digest()

            require(
                (secretHash[0].toInt() and 0xFF) ==
                        expectedFirstByte
            ) {
                "PIN incorrect"
            }

            writeMessage(
                socket.outputStream,
                TvPairingClient.createSecret(
                    secretHash
                )
            )

            val response =
                readMessage(socket.inputStream)

            checkStatus(response, "SecretAck")

            check(response.hasSecretAck()) {
                "SecretAck attendu"
            }

            FoxLogger.i(
                "FOX-TV | Pairing | SecretAck reçu"
            )

            currentDevice?.let { device ->
                FoxTvSettings.saveTvIp(
                    appContext,
                    device.address
                )
            }

            close()

            FoxLogger.i(
                "FOX-TV | Pairing | Appairage terminé"
            )

            true

        } catch (e: Exception) {
            FoxLogger.e(
                "FOX-TV | Pairing | PIN refusé",
                e
            )

            false
        }
    }

    private fun checkStatus(
        message: PoloProto.OuterMessage,
        step: String
    ) {
        check(
            message.status ==
                    PoloProto.OuterMessage.Status.STATUS_OK
        ) {
            "$step refusé : ${message.status}"
        }
    }

    private fun writeMessage(
        output: OutputStream,
        message: PoloProto.OuterMessage
    ) {
        val payload = message.toByteArray()

        writeVarInt(
            output,
            payload.size
        )

        output.write(payload)
        output.flush()

        FoxLogger.i(
            "FOX-TV | Pairing | Message envoyé : ${payload.size} octets"
        )
    }

    private fun readMessage(
        input: InputStream
    ): PoloProto.OuterMessage {
        val length = readVarInt(input)

        require(
            length in 1..MAX_MESSAGE_SIZE
        ) {
            "Longueur Protobuf invalide : $length"
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
                error(
                    "Connexion fermée pendant la lecture"
                )
            }

            offset += count
        }

        return PoloProto.OuterMessage.parseFrom(
            payload
        )
    }

    private fun writeVarInt(
        output: OutputStream,
        value: Int
    ) {
        var remaining = value

        while (true) {
            if (
                remaining and 0x7F.inv() == 0
            ) {
                output.write(remaining)
                return
            }

            output.write(
                remaining and 0x7F or 0x80
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
                error(
                    "Connexion fermée avant la longueur"
                )
            }

            result = result or
                    ((byte and 0x7F) shl shift)

            if (byte and 0x80 == 0) {
                return result
            }

            shift += 7
        }

        error("VarInt trop long")
    }

    private fun BigInteger
            .toUnsignedByteArray(): ByteArray {

        val bytes = toByteArray()

        return if (
            bytes.size > 1 &&
            bytes[0] == 0.toByte()
        ) {
            bytes.copyOfRange(
                1,
                bytes.size
            )
        } else {
            bytes
        }
    }

    fun close() {
        try {
            pairingSocket?.close()
        } catch (_: Exception) {
        }

        pairingSocket = null
        currentDevice = null
    }
}