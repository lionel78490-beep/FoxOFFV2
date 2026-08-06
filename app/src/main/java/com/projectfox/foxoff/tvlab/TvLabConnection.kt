package com.projectfox.foxoff.tvlab

import android.content.Context
import com.google.polo.wire.protobuf.PoloProto
import com.projectfox.foxoff.tv.pairing.TvPairingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket
import java.math.BigInteger

object TvLabConnection {

    private const val PAIRING_PORT = 6467

    // On garde la connexion ouverte pour que le PIN reste affiché.
    private var pairingSocket: SSLSocket? = null
    private var currentIdentity: TvLabIdentity? = null

    suspend fun connect(
        context: Context,
        ip: String
    ): String = withContext(Dispatchers.IO) {

        try {
            close()

            val rawSocket = Socket().apply {
                connect(
                    InetSocketAddress(ip, PAIRING_PORT),
                    5000
                )
                soTimeout = 8000
            }

            val identity = TvLabIdentity(
                context.applicationContext
            )
            currentIdentity = identity

            val sslSocket = identity.createSslContext()
                .socketFactory
                .createSocket(
                    rawSocket,
                    ip,
                    PAIRING_PORT,
                    true
                ) as SSLSocket

            sslSocket.useClientMode = true
            sslSocket.soTimeout = 8000
            sslSocket.startHandshake()

            pairingSocket = sslSocket

            val input = sslSocket.inputStream
            val output = sslSocket.outputStream

            // 1. PairingRequest
            writeMessage(
                output,
                TvPairingClient.createPairingRequest()
            )

            val pairingAck = readMessage(input)

            check(pairingAck.status ==
                    PoloProto.OuterMessage.Status.STATUS_OK) {
                "Statut TV : ${pairingAck.status}"
            }

            check(pairingAck.hasPairingRequestAck()) {
                "PairingRequestAck attendu, reçu : $pairingAck"
            }

            // 2. Options
            writeMessage(
                output,
                TvPairingClient.createOptions()
            )

            val tvOptions = readMessage(input)

            check(tvOptions.status ==
                    PoloProto.OuterMessage.Status.STATUS_OK) {
                "Statut Options : ${tvOptions.status}"
            }

            check(tvOptions.hasOptions()) {
                "Options attendues, reçu : $tvOptions"
            }

            // 3. Configuration
            writeMessage(
                output,
                TvPairingClient.createConfiguration()
            )

            val configurationAck = readMessage(input)

            check(configurationAck.status ==
                    PoloProto.OuterMessage.Status.STATUS_OK) {
                "Statut Configuration : ${configurationAck.status}"
            }

            check(configurationAck.hasConfigurationAck()) {
                "ConfigurationAck attendu, reçu : $configurationAck"
            }

            buildString {
                appendLine("✅ TCP OK")
                appendLine("✅ TLS OK")
                appendLine("✅ PairingRequestAck")
                appendLine("✅ Options reçues")
                appendLine("✅ ConfigurationAck")
                append("📺 REGARDE LA TV : le PIN doit être affiché")
            }

        } catch (e: Exception) {
            close()

            buildString {
                appendLine("❌ PAIRING ÉCHEC")
                appendLine(e.javaClass.simpleName)
                append(e.message ?: "Erreur sans message")
            }
        }
    }

    private fun writeMessage(
        output: OutputStream,
        message: PoloProto.OuterMessage
    ) {
        val payload = message.toByteArray()

        writeVarInt(output, payload.size)
        output.write(payload)
        output.flush()
    }

    private fun readMessage(
        input: InputStream
    ): PoloProto.OuterMessage {
        val length = readVarInt(input)

        require(length in 1..1_000_000) {
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
                error("Connexion fermée pendant la lecture")
            }

            offset += count
        }

        return PoloProto.OuterMessage.parseFrom(payload)
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
                error("Connexion fermée avant la longueur")
            }

            result = result or
                    ((byte and 0x7F) shl shift)

            if ((byte and 0x80) == 0) {
                return result
            }

            shift += 7
        }

        error("VarInt trop long")
    }
    suspend fun sendPin(
        pin: String
    ): String = withContext(Dispatchers.IO) {

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
                "Le PIN doit contenir exactement 6 caractères hexadécimaux"
            }

            val socket = pairingSocket
                ?: error("La connexion de pairing n’est plus ouverte")

            val clientIdentity = currentIdentity
                ?: error("Identité client indisponible")

            val clientCertificate =
                clientIdentity.getCertificate()

            val serverCertificate =
                socket.session.peerCertificates.first()
                        as X509Certificate

            val clientPublicKey =
                clientCertificate.publicKey as? RSAPublicKey
                    ?: error("La clé publique client n’est pas RSA")

            val serverPublicKey =
                serverCertificate.publicKey as? RSAPublicKey
                    ?: error("La clé publique TV n’est pas RSA")

            val clientModulus =
                clientPublicKey.modulus.toUnsignedByteArray()

            val clientExponent =
                clientPublicKey.publicExponent.toUnsignedByteArray()

            val serverModulus =
                serverPublicKey.modulus.toUnsignedByteArray()

            val serverExponent =
                serverPublicKey.publicExponent.toUnsignedByteArray()

            /*
             * Le premier octet du PIN sert à vérifier le résultat.
             * Les quatre derniers caractères représentent les deux octets
             * ajoutés au calcul SHA-256.
             */
            val expectedFirstByte =
                normalizedPin.substring(0, 2).toInt(16)

            val pinBytes =
                normalizedPin.substring(2)
                    .chunked(2)
                    .map { value ->
                        value.toInt(16).toByte()
                    }
                    .toByteArray()

            val digest = MessageDigest.getInstance("SHA-256")

            digest.update(clientModulus)
            digest.update(clientExponent)
            digest.update(serverModulus)
            digest.update(serverExponent)
            digest.update(pinBytes)

            val secretHash = digest.digest()

            require(
                secretHash[0].toInt() and 0xFF ==
                        expectedFirstByte
            ) {
                "PIN incorrect : contrôle cryptographique invalide"
            }

            writeMessage(
                socket.outputStream,
                TvPairingClient.createSecret(secretHash)
            )

            val response =
                readMessage(socket.inputStream)

            check(
                response.status ==
                        PoloProto.OuterMessage.Status.STATUS_OK
            ) {
                "La TV a refusé le secret : ${response.status}"
            }

            check(response.hasSecretAck()) {
                "SecretAck attendu, reçu : $response"
            }

            close()

            buildString {
                appendLine("✅ TCP OK")
                appendLine("✅ TLS OK")
                appendLine("✅ PairingRequestAck")
                appendLine("✅ Options reçues")
                appendLine("✅ ConfigurationAck")
                appendLine("✅ SecretAck")
                append("🎉 APPAIRAGE TERMINÉ")
            }

        } catch (e: Exception) {
            buildString {
                appendLine("❌ VALIDATION DU PIN ÉCHOUÉE")
                appendLine(e.javaClass.simpleName)
                append(e.message ?: "Erreur sans message")
            }
        }
    }
    private fun BigInteger.toUnsignedByteArray(): ByteArray {
        val bytes = toByteArray()

        return if (
            bytes.size > 1 &&
            bytes[0] == 0.toByte()
        ) {
            bytes.copyOfRange(1, bytes.size)
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
        currentIdentity = null
    }
}