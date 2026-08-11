package com.projectfox.foxoff.core.watch

/**
 * Marque de la montre associée. `WEAR_OS` est la seule utilisée
 * aujourd'hui — `GARMIN` prépare le terrain pour `GarminTransport`
 * (Connect IQ Mobile SDK), pas encore implémenté.
 */
enum class WatchBrand {
    WEAR_OS,
    GARMIN
}
