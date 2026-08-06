package com.projectfox.foxoff.tv.pairing

class PairingSession {

    fun start() {
        val message = TvPairingClient.createPairingRequest()
        val bytes = message.toByteArray()

        println("PairingRequest : ${bytes.size} octets")
    }
}