package com.projectfox.foxoff.tv

/**
 * Ce qu'il faut faire après une tentative de connexion réelle vers une TV
 * mémorisée. Décision pure (aucune E/S, aucun accès à FoxTvSettings ou à
 * un socket) : prend en entrée le résultat déjà obtenu (TvConnectionTestResult)
 * et l'état déjà connu (empreinte/adresse mémorisées), pour rester
 * testable indépendamment du code réseau.
 */
sealed class TvConnectionOutcome {
    /** Identité confirmée (ou aucune empreinte à vérifier n'était nécessaire car déjà confirmée par ailleurs). */
    data class Reconnected(val updateStoredAddress: Boolean) : TvConnectionOutcome()

    /** Empreinte différente : ne jamais modifier l'entrée mémorisée, ne jamais considérer comme connecté. */
    object IdentityMismatch : TvConnectionOutcome()

    /** Connexion réussie mais aucune empreinte mémorisée : confirmation utilisateur requise avant d'enregistrer quoi que ce soit. */
    data class ConfirmationRequired(val fingerprint: String) : TvConnectionOutcome()

    /** Échec réseau (TV éteinte, injoignable, délai dépassé...) : rien n'est modifié, TV laissée "Hors ligne". */
    object Offline : TvConnectionOutcome()

    /** Rejet TLS confirmé : seul cas où un nouvel appairage doit être proposé. */
    object PairingRequired : TvConnectionOutcome()
}

object TvConnectionOutcomeResolver {

    /**
     * @param storedFingerprint empreinte actuellement mémorisée pour cette TV (null si jamais confirmée).
     * @param storedAddress adresse actuellement mémorisée pour cette TV (null si TV pas encore persistée, ex: nouvel appairage en cours).
     * @param connectedAtAddress adresse à laquelle la tentative de connexion a réellement eu lieu.
     * @param result résultat brut de la tentative de connexion.
     */
    fun resolve(
        storedFingerprint: String?,
        storedAddress: String?,
        connectedAtAddress: String,
        result: TvConnectionTestResult
    ): TvConnectionOutcome {
        return when (result) {
            is TvConnectionTestResult.Connected -> {
                when (TvIdentityVerifier.check(storedFingerprint, result.serverCertificateFingerprint)) {
                    TvIdentityCheck.Confirmed ->
                        TvConnectionOutcome.Reconnected(updateStoredAddress = storedAddress != connectedAtAddress)

                    TvIdentityCheck.Mismatch ->
                        TvConnectionOutcome.IdentityMismatch

                    TvIdentityCheck.NoStoredFingerprint ->
                        TvConnectionOutcome.ConfirmationRequired(result.serverCertificateFingerprint)
                }
            }

            TvConnectionTestResult.NetworkUnreachable -> TvConnectionOutcome.Offline
            TvConnectionTestResult.AuthRejected -> TvConnectionOutcome.PairingRequired
        }
    }
}
