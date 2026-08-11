package com.projectfox.foxoff.tv

import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Calcule l'empreinte canonique d'un certificat serveur TV : SHA-256 de
 * l'encodage DER, en hexadécimal minuscule sans séparateur. Format UNIQUE
 * utilisé partout où une empreinte est calculée (appairage, connexion de
 * contrôle) pour qu'une même TV produise toujours exactement la même
 * chaîne — toute divergence de format créerait de fausses différences.
 */
object TvCertificateFingerprint {

    fun of(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
