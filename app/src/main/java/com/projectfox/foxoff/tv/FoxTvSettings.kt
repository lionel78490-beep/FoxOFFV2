package com.projectfox.foxoff.tv

import android.content.Context
import android.content.SharedPreferences
import com.projectfox.foxoff.core.logging.FoxLogger

/**
 * Stockage des TV associées à FoxOFF.
 *
 * Supporte plusieurs TV mémorisées, chacune identifiée par un id stable
 * (généré une seule fois au moment de l'appairage, jamais réécrit — voir
 * generateStableId()). Une seule TV peut être "active" à la fois (cible de
 * l'automatisation et de la carte Accueil, voir getActiveDevice()).
 *
 * L'ancien stockage à TV unique est migré une seule fois, de façon
 * idempotente (voir migrateLegacyIfNeeded), sans jamais inventer
 * d'empreinte de certificat pour l'entrée migrée.
 */
object FoxTvSettings {

    private const val PREFS_NAME = "fox_tv_settings"

    // --- Anciennes clés (TV unique). Lues uniquement par la migration,
    // jamais réécrites après coup.
    private const val LEGACY_KEY_TV_IP = "selected_tv_ip"
    private const val LEGACY_KEY_TV_ID = "selected_tv_id"
    private const val LEGACY_KEY_TV_NAME = "selected_tv_name"
    private const val LEGACY_KEY_TV_PORT = "selected_tv_port"

    // --- Stockage multi-TV
    private const val KEY_TV_IDS = "paired_tv_ids"
    private const val KEY_ACTIVE_TV_ID = "active_tv_id"
    private const val KEY_MIGRATION_DONE = "migration_multi_tv_v1_done"

    // --- Réservé à TvLabActivity (outil de diagnostic manuel), voir
    // saveTvIp()/getTvIp() ci-dessous.
    private const val KEY_MANUAL_IP_OVERRIDE = "manual_ip_override"

    private fun prefs(context: Context): SharedPreferences =
        com.projectfox.foxoff.core.security.FoxEncryptedPrefs.get(context, PREFS_NAME)

    private fun fieldName(id: String, field: String) = "tv_${id}_$field"

    /** Génère un identifiant stable pour une TV qui vient d'être appairée pour la première fois. */
    fun generateStableId(): String = java.util.UUID.randomUUID().toString()

    // -----------------------------------------------------------------
    // API historique — TvLabActivity (diagnostic manuel) / FoxTvPauseAction
    // -----------------------------------------------------------------

    /**
     * Réservé à TvLabActivity : force une adresse IP de test pour
     * l'automatisation, prioritaire sur la TV active, sans toucher à la
     * liste des TV réellement associées.
     */
    fun saveTvIp(context: Context, ip: String) {
        prefs(context).edit()
            .putString(KEY_MANUAL_IP_OVERRIDE, ip.trim())
            .apply()
    }

    /**
     * Adresse ciblée par l'automatisation (FoxTvPauseAction) : la
     * surcharge manuelle de TvLabActivity si présente, sinon celle de la
     * TV active.
     */
    fun getTvIp(context: Context): String? {
        prefs(context).getString(KEY_MANUAL_IP_OVERRIDE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return getActiveDevice(context)?.address
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_MANUAL_IP_OVERRIDE)
            .apply()
    }

    // -----------------------------------------------------------------
    // Multi-TV
    // -----------------------------------------------------------------

    fun getPairedDevices(context: Context): List<TvDevice> {
        migrateLegacyIfNeeded(context)
        val p = prefs(context)
        val ids = p.getStringSet(KEY_TV_IDS, emptySet()).orEmpty()
        return ids.mapNotNull { id -> readDevice(p, id) }
    }

    fun getDevice(context: Context, id: String): TvDevice? {
        migrateLegacyIfNeeded(context)
        return readDevice(prefs(context), id)
    }

    fun getActiveDeviceId(context: Context): String? {
        migrateLegacyIfNeeded(context)
        return prefs(context).getString(KEY_ACTIVE_TV_ID, null)
    }

    fun getActiveDevice(context: Context): TvDevice? {
        val id = getActiveDeviceId(context) ?: return null
        return readDevice(prefs(context), id)
    }

    /**
     * Compatibilité étape A : renvoie la TV active, le temps que
     * FoxTvEngine soit adapté à la liste (étape B).
     */
    fun getPairedDevice(context: Context): TvDevice? = getActiveDevice(context)

    /**
     * Enregistre (ou met à jour) une TV appairée. Si aucune TV n'est
     * encore active, celle-ci le devient automatiquement (première TV
     * associée = active par défaut).
     */
    fun savePairedDevice(context: Context, device: TvDevice) {
        migrateLegacyIfNeeded(context)

        val p = prefs(context)
        writeDevice(p, device)

        val ids = p.getStringSet(KEY_TV_IDS, emptySet()).orEmpty().toMutableSet()
        ids.add(device.id)

        val editor = p.edit().putStringSet(KEY_TV_IDS, ids)
        if (p.getString(KEY_ACTIVE_TV_ID, null) == null) {
            editor.putString(KEY_ACTIVE_TV_ID, device.id)
        }
        editor.apply()
    }

    /**
     * Met à jour uniquement l'adresse d'une TV déjà mémorisée (ex: après
     * confirmation d'identité par empreinte), sans changer son id ni son
     * statut actif.
     */
    fun updateDeviceAddress(context: Context, id: String, newAddress: String) {
        val existing = getDevice(context, id) ?: return
        writeDevice(prefs(context), existing.copy(address = newAddress))
    }

    /**
     * Enregistre l'empreinte de certificat serveur pour une TV. Ne doit
     * être appelé qu'après un nouvel appairage PIN réussi, ou après
     * confirmation explicite de l'utilisateur — jamais automatiquement
     * après une simple connexion réussie (voir TvIdentityVerifier).
     */
    fun updateFingerprint(context: Context, id: String, fingerprint: String) {
        val existing = getDevice(context, id) ?: return
        writeDevice(prefs(context), existing.copy(certificateFingerprint = fingerprint))
    }

    fun setActiveDevice(context: Context, id: String) {
        if (getDevice(context, id) == null) return
        prefs(context).edit().putString(KEY_ACTIVE_TV_ID, id).apply()
    }

    /**
     * Dissocie une TV (pas encore exposé par l'interface). Si elle était
     * active, une autre TV mémorisée devient active automatiquement s'il
     * en reste une ; sinon plus aucune TV n'est active.
     */
    fun removePairedDevice(context: Context, id: String) {
        val p = prefs(context)
        val ids = p.getStringSet(KEY_TV_IDS, emptySet()).orEmpty().toMutableSet()
        if (!ids.remove(id)) return

        val editor = p.edit().putStringSet(KEY_TV_IDS, ids)
        removeDeviceFields(editor, id)

        if (p.getString(KEY_ACTIVE_TV_ID, null) == id) {
            val newActive = ids.firstOrNull()
            if (newActive != null) {
                editor.putString(KEY_ACTIVE_TV_ID, newActive)
            } else {
                editor.remove(KEY_ACTIVE_TV_ID)
            }
        }

        editor.apply()
    }

    // -----------------------------------------------------------------
    // Migration (idempotente)
    // -----------------------------------------------------------------

    private fun migrateLegacyIfNeeded(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_MIGRATION_DONE, false)) return

        val legacyIp = p.getString(LEGACY_KEY_TV_IP, null)?.takeIf { it.isNotBlank() }
        if (legacyIp == null) {
            // Rien à migrer : on marque quand même la migration comme
            // terminée pour ne plus la retenter à chaque appel.
            p.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
            return
        }

        val legacyId = p.getString(LEGACY_KEY_TV_ID, null) ?: generateStableId()
        val legacyName = p.getString(LEGACY_KEY_TV_NAME, null) ?: "TV mémorisée"
        val legacyPort = p.getInt(LEGACY_KEY_TV_PORT, 6467)

        val migrated = TvDevice(
            id = legacyId,
            name = legacyName,
            address = legacyIp,
            port = legacyPort,
            // Jamais renseignée automatiquement lors d'une migration :
            // l'utilisateur devra confirmer explicitement sa première TV
            // migrée avant qu'une empreinte ne soit enregistrée (voir
            // consigne — pas encore implémenté à l'étape A, prévu à
            // l'étape B avec l'UI de confirmation).
            certificateFingerprint = null
        )

        writeDevice(p, migrated)

        val ids = p.getStringSet(KEY_TV_IDS, emptySet()).orEmpty().toMutableSet()
        ids.add(migrated.id)

        val editor = p.edit().putStringSet(KEY_TV_IDS, ids)
        if (p.getString(KEY_ACTIVE_TV_ID, null) == null) {
            editor.putString(KEY_ACTIVE_TV_ID, migrated.id)
        }
        editor.apply()

        // Relecture de vérification AVANT de supprimer les anciennes clés :
        // si l'écriture a échoué, on ne supprime rien et on retentera la
        // migration au prochain appel (idempotent).
        val reread = prefs(context)
        val verifiedDevice = readDevice(reread, migrated.id)
        val verifiedIds = reread.getStringSet(KEY_TV_IDS, emptySet()).orEmpty()

        if (verifiedDevice == migrated && migrated.id in verifiedIds) {
            reread.edit()
                .remove(LEGACY_KEY_TV_IP)
                .remove(LEGACY_KEY_TV_ID)
                .remove(LEGACY_KEY_TV_NAME)
                .remove(LEGACY_KEY_TV_PORT)
                .putBoolean(KEY_MIGRATION_DONE, true)
                .apply()

            FoxLogger.i(
                "FOX-TV | Migration TV unique -> liste multi-TV réussie (${migrated.name})"
            )
        } else {
            FoxLogger.e(
                "FOX-TV | Migration TV unique : échec de vérification, nouvelle tentative au prochain appel"
            )
        }
    }

    // -----------------------------------------------------------------
    // Lecture/écriture bas niveau d'une entrée
    // -----------------------------------------------------------------

    private fun readDevice(p: SharedPreferences, id: String): TvDevice? {
        val address = p.getString(fieldName(id, "address"), null) ?: return null
        return TvDevice(
            id = id,
            name = p.getString(fieldName(id, "name"), null) ?: "TV mémorisée",
            address = address,
            port = p.getInt(fieldName(id, "port"), 6467),
            certificateFingerprint = p.getString(fieldName(id, "fingerprint"), null)
        )
    }

    private fun writeDevice(p: SharedPreferences, device: TvDevice) {
        val editor = p.edit()
            .putString(fieldName(device.id, "name"), device.name)
            .putString(fieldName(device.id, "address"), device.address)
            .putInt(fieldName(device.id, "port"), device.port)

        if (device.certificateFingerprint != null) {
            editor.putString(fieldName(device.id, "fingerprint"), device.certificateFingerprint)
        } else {
            editor.remove(fieldName(device.id, "fingerprint"))
        }

        editor.apply()
    }

    private fun removeDeviceFields(editor: SharedPreferences.Editor, id: String) {
        editor.remove(fieldName(id, "name"))
            .remove(fieldName(id, "address"))
            .remove(fieldName(id, "port"))
            .remove(fieldName(id, "fingerprint"))
    }
}
