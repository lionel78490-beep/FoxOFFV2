package com.projectfox.foxoff.tv.protobuf

import com.google.polo.wire.protobuf.PoloProto
import com.google.protobuf.ByteString

/**
 * Construit les messages du protocole Google TV Pairing.
 */
object PoloFactory {

    private fun baseMessage(): PoloProto.OuterMessage.Builder {
        return PoloProto.OuterMessage.newBuilder()
            .setProtocolVersion(TvConstants.PROTOCOL_VERSION)
            .setStatus(PoloProto.OuterMessage.Status.STATUS_OK)
    }

    /**
     * Premier message envoyé à la TV.
     */
    fun createPairingRequest(): PoloProto.OuterMessage {
        val request = PoloProto.PairingRequest.newBuilder()
            .setServiceName(TvConstants.SERVICE_NAME)
            .setClientName(TvConstants.CLIENT_NAME)
            .build()

        return baseMessage()
            .setPairingRequest(request)
            .build()
    }

    /**
     * Envoyé après réception de pairing_request_ack.
     */
    fun createOptionsMessage(): PoloProto.OuterMessage {
        val encoding = PoloProto.Options.Encoding.newBuilder()
            .setType(
                PoloProto.Options.Encoding.EncodingType
                    .ENCODING_TYPE_HEXADECIMAL
            )
            .setSymbolLength(TvConstants.ENCODING_LENGTH)
            .build()

        val options = PoloProto.Options.newBuilder()
            .setPreferredRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
            .addInputEncodings(encoding)
            .build()

        return baseMessage()
            .setOptions(options)
            .build()
    }

    /**
     * Envoyé après réception des options de la TV.
     */
    fun createConfigurationMessage(): PoloProto.OuterMessage {
        val encoding = PoloProto.Options.Encoding.newBuilder()
            .setType(
                PoloProto.Options.Encoding.EncodingType
                    .ENCODING_TYPE_HEXADECIMAL
            )
            .setSymbolLength(TvConstants.ENCODING_LENGTH)
            .build()

        val configuration = PoloProto.Configuration.newBuilder()
            .setClientRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
            .setEncoding(encoding)
            .build()

        return baseMessage()
            .setConfiguration(configuration)
            .build()
    }

    /**
     * Dernier message envoyé après calcul du hash avec le PIN.
     */
    fun createSecretMessage(secretHash: ByteArray): PoloProto.OuterMessage {
        require(secretHash.isNotEmpty()) {
            "Le secret de pairing ne peut pas être vide"
        }

        val secret = PoloProto.Secret.newBuilder()
            .setSecret(ByteString.copyFrom(secretHash))
            .build()

        return baseMessage()
            .setSecret(secret)
            .build()
    }

    /**
     * Transforme les octets reçus en message Protobuf.
     */
    fun parse(data: ByteArray): PoloProto.OuterMessage {
        return PoloProto.OuterMessage.parseFrom(data)
    }
}