package com.projectfox.foxoff.tv

/**
 * Résultat de la phase 1 (heuristique, sans réseau) de reconnaissance d'un
 * appareil redécouvert par le scan face à la liste des TV mémorisées.
 * Ne désigne qu'un CANDIDAT : la confirmation définitive (phase 2) exige
 * toujours une connexion réelle et, si une empreinte est mémorisée, sa
 * vérification (voir TvIdentityVerifier). Un candidat par adresse n'est
 * jamais traité comme confirmé tant que la phase 2 n'a pas conclu.
 */
sealed class TvMatchCandidate {
    /**
     * Une TV mémorisée occupe la même adresse que l'appareil redécouvert.
     * Signal fort, mais pas une preuve : une adresse IP mémorisée peut être
     * réattribuée à un autre appareil (voir TvIdentityVerifier).
     */
    data class AddressCandidate(val storedDevice: TvDevice) : TvMatchCandidate()

    /**
     * Une TV mémorisée porte le même nom mais une adresse différente.
     * Ambigu par nature (plusieurs appareils peuvent partager un nom) :
     * ne doit jamais entraîner de mise à jour silencieuse, seulement une
     * confirmation explicite de l'utilisateur.
     */
    data class NameOnlyCandidate(val storedDevice: TvDevice) : TvMatchCandidate()

    /** Aucune correspondance : l'appareil redécouvert est considéré comme non identifié. */
    object NoCandidate : TvMatchCandidate()
}

/**
 * Phase 1 de la reconnaissance multi-TV : décision pure (aucune E/S), donc
 * testable indépendamment du code réseau. Compare un appareil redécouvert
 * par le scan à TOUTES les TV mémorisées (pas une seule), pour supporter
 * plusieurs TV associées.
 */
object TvMultiDeviceMatcher {

    fun match(discovered: TvDevice, storedDevices: List<TvDevice>): TvMatchCandidate {
        if (discovered.address.isNotBlank()) {
            storedDevices
                .firstOrNull { it.address.equals(discovered.address, ignoreCase = true) }
                ?.let { return TvMatchCandidate.AddressCandidate(it) }
        }

        if (discovered.name.isNotBlank()) {
            storedDevices
                .firstOrNull { it.name.equals(discovered.name, ignoreCase = true) }
                ?.let { return TvMatchCandidate.NameOnlyCandidate(it) }
        }

        return TvMatchCandidate.NoCandidate
    }
}
