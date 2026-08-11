package com.projectfox.foxoff.tv

/**
 * Résultat d'une vérification de connexion réelle vers une TV mémorisée,
 * utilisé pour décider s'il faut la garder "Hors ligne" (réessayer plus
 * tard, ne rien effacer) ou proposer un nouvel appairage (identité
 * réellement rejetée).
 */
sealed class TvConnectionTestResult {
    /**
     * Handshake TLS + RemoteStart confirmés : la TV est bien joignable.
     * [serverCertificateFingerprint] est l'empreinte canonique (voir
     * TvCertificateFingerprint) du certificat présenté PENDANT cette
     * connexion — elle doit encore être comparée à celle mémorisée avant
     * de considérer l'identité comme confirmée (voir TvIdentityVerifier) :
     * une connexion TLS réussie prouve seulement que l'appareil accepte
     * l'identité client globale de FoxOFF, pas qu'il s'agit de la bonne TV.
     */
    data class Connected(val serverCertificateFingerprint: String) : TvConnectionTestResult()

    /** TV éteinte, adresse injoignable, délai dépassé, réseau absent... Rien n'indique un problème d'identité. */
    object NetworkUnreachable : TvConnectionTestResult()

    /** Le handshake TLS a échoué explicitement (SSLHandshakeException) : l'identité mémorisée n'est plus acceptée. */
    object AuthRejected : TvConnectionTestResult()
}
