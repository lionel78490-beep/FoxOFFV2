package com.projectfox.foxoff.tv

/**
 * Résultat de la phase 2 de reconnaissance (confirmation d'identité) :
 * compare l'empreinte de certificat serveur mémorisée pour une TV à celle
 * réellement présentée lors d'une connexion. Décision pure (aucune E/S) —
 * l'extraction de l'empreinte depuis la session TLS se fait dans le code
 * réseau (étape B), pas ici.
 */
sealed class TvIdentityCheck {
    /** Empreinte mémorisée et empreinte présentée identiques : bonne TV confirmée. */
    object Confirmed : TvIdentityCheck()

    /**
     * Empreinte mémorisée et empreinte présentée différentes : un autre
     * appareil occupe cette adresse (ou répond à ce nom). Ne doit jamais
     * être traité comme une reconnexion réussie, et l'entrée mémorisée ne
     * doit jamais être modifiée dans ce cas.
     */
    object Mismatch : TvIdentityCheck()

    /**
     * Aucune empreinte mémorisée pour cette entrée (TV appairée avant
     * cette fonctionnalité, ou migrée depuis l'ancien stockage). Ne doit
     * JAMAIS être enregistrée automatiquement après une simple connexion
     * réussie — seulement après confirmation explicite de l'utilisateur,
     * ou directement après un nouvel appairage PIN réussi.
     */
    object NoStoredFingerprint : TvIdentityCheck()
}

object TvIdentityVerifier {

    fun check(storedFingerprint: String?, presentedFingerprint: String): TvIdentityCheck {
        return when {
            storedFingerprint == null -> TvIdentityCheck.NoStoredFingerprint
            storedFingerprint.equals(presentedFingerprint, ignoreCase = true) -> TvIdentityCheck.Confirmed
            else -> TvIdentityCheck.Mismatch
        }
    }
}
