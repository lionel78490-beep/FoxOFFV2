package com.projectfox.foxoff.tv.pairing

import com.google.polo.wire.protobuf.PoloProto
import com.google.protobuf.ByteString

object TvPairingClient {

    private fun baseMessage(): PoloProto.OuterMessage.Builder {
        return PoloProto.OuterMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(PoloProto.OuterMessage.Status.STATUS_OK)
    }

    fun createPairingRequest(): PoloProto.OuterMessage {
        val request = PoloProto.PairingRequest.newBuilder()
            .setServiceName("atvremote")
            .setClientName("FoxOFF")
            .build()

        return baseMessage()
            .setPairingRequest(request)
            .build()
    }

    fun createOptions(): PoloProto.OuterMessage {
        val encoding = PoloProto.Options.Encoding.newBuilder()
            .setType(
                PoloProto.Options.Encoding.EncodingType
                    .ENCODING_TYPE_HEXADECIMAL
            )
            .setSymbolLength(6)
            .build()

        val options = PoloProto.Options.newBuilder()
            .setPreferredRole(
                PoloProto.Options.RoleType.ROLE_TYPE_INPUT
            )
            .addInputEncodings(encoding)
            .build()

        return baseMessage()
            .setOptions(options)
            .build()
    }

    fun createConfiguration(): PoloProto.OuterMessage {
        val encoding = PoloProto.Options.Encoding.newBuilder()
            .setType(
                PoloProto.Options.Encoding.EncodingType
                    .ENCODING_TYPE_HEXADECIMAL
            )
            .setSymbolLength(6)
            .build()

        val configuration = PoloProto.Configuration.newBuilder()
            .setClientRole(
                PoloProto.Options.RoleType.ROLE_TYPE_INPUT
            )
            .setEncoding(encoding)
            .build()

        return baseMessage()
            .setConfiguration(configuration)
            .build()
    }
    fun createSecret(
        secretHash: ByteArray
    ): PoloProto.OuterMessage {

        val secret = PoloProto.Secret.newBuilder()
            .setSecret(ByteString.copyFrom(secretHash))
            .build()

        return baseMessage()
            .setSecret(secret)
            .build()
    }
}