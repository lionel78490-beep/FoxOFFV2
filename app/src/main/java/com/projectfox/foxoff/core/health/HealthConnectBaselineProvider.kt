package com.projectfox.foxoff.core.health

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.projectfox.foxoff.core.logging.FoxLogger
import java.time.Duration
import java.time.Instant

/**
 * Calibration du BPM de repos (FoxBrainState.restingBpmBaseline) à partir
 * des données de santé déjà présentes sur l'appareil via Health Connect —
 * remplace la moyenne générique par défaut (70 bpm, voir FoxBrainState) par
 * une vraie référence personnelle dès qu'elle est disponible. Générique
 * côté source : lit Health Connect sans se soucier de quelle app y a écrit
 * les données (Samsung Health pour Wear OS, Garmin Connect pour Garmin —
 * les deux ont leur propre intégration Health Connect native, voir
 * SettingsScreen.kt pour le libellé adapté à la marque active). Lecture
 * SEULE, aucune permission d'écriture demandée, aucune donnée envoyée hors
 * de l'appareil (voir HealthConnectPermissionsRationaleActivity).
 *
 * Signatures vérifiées par décompilation du vrai AAR
 * `androidx.health.connect:connect-client:1.1.0` (javap), pas devinées.
 */
/**
 * Résultat de computeRestingBaseline() — distingue explicitement "aucune
 * donnée" de "la lecture a échoué" (ex: permission révoquée après coup,
 * Health Connect indisponible ponctuellement). Avant cette distinction,
 * toute exception était avalée silencieusement et rapportée comme "aucune
 * donnée exploitable" à l'utilisateur, masquant la vraie cause — bug réel
 * constaté (2026-08-09) : l'utilisateur voyait des données réelles dans
 * Health Connect (vérifié dans l'app système elle-même) alors que FoxOFF
 * rapportait "aucune donnée".
 */
sealed class RestingBaselineResult {
    data class Success(val bpm: Int) : RestingBaselineResult()
    object NoData : RestingBaselineResult()
    data class ReadFailed(val cause: String) : RestingBaselineResult()
}

object HealthConnectBaselineProvider {

    private val READ_HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val READ_RESTING_HEART_RATE = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    private val READ_SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)

    val requiredPermissions: Set<String> = setOf(READ_HEART_RATE, READ_RESTING_HEART_RATE, READ_SLEEP)

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /**
     * Ouvre directement l'écran de données de Health Connect — sur beaucoup
     * d'appareils (dont Samsung), Health Connect n'a pas d'icône dans le
     * tiroir d'applications, seulement accessible via ce genre de lien ou
     * les réglages système. Utile pour vérifier concrètement quelles
     * données Samsung Health y a effectivement écrites.
     */
    fun manageDataIntent(context: Context): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)

    suspend fun hasPermission(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        } catch (e: Exception) {
            FoxLogger.e("FOX-HEALTH-CONNECT | Échec vérification permissions", e)
            false
        }
    }

    /**
     * Calcule une référence BPM de repos à partir de l'historique
     * Health Connect, par ordre de priorité :
     *
     * 1. BPM mesuré PENDANT de vraies sessions de sommeil enregistrées
     *    (`SleepSessionRecord`, croisé avec `HeartRateRecord` par
     *    horodatage) — la référence la plus précise possible : le BPM
     *    pendant le sommeil confirmé, pas juste "au repos" en général (qui
     *    inclut des moments calmes éveillé). Ajouté le 2026-08-10.
     * 2. `RestingHeartRateRecord` — déjà "au repos" par construction, tels
     *    qu'écrits par Samsung Health/Garmin Connect, mais moins précis que
     *    (1) car ce n'est pas nécessairement mesuré pendant le sommeil.
     * 3. 10e percentile des échantillons `HeartRateRecord` bruts — dernier
     *    recours si aucune des deux sources précédentes n'existe.
     *
     * Chaque source est essayée avant d'abandonner — un échec (exception)
     * sur l'une n'empêche pas d'essayer les suivantes ; le résultat n'est
     * `ReadFailed` que si TOUTES échouent avec une vraie erreur, `NoData` si
     * toutes renvoient simplement zéro résultat.
     *
     * Simplification assumée : une seule page de résultats par type (pas de
     * pagination) — largement suffisant sur ~60 jours.
     */
    suspend fun computeRestingBaseline(
        context: Context,
        lookback: Duration = Duration.ofDays(60)
    ): RestingBaselineResult {
        if (!isAvailable(context)) return RestingBaselineResult.ReadFailed("Health Connect indisponible")

        val client = HealthConnectClient.getOrCreate(context)
        val range = TimeRangeFilter.between(Instant.now().minus(lookback), Instant.now())
        var firstFailure: String? = null

        // 1) BPM croisé avec les sessions de sommeil réelles.
        val sleepSessions = try {
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records
        } catch (e: Exception) {
            FoxLogger.e("FOX-HEALTH-CONNECT | Échec lecture SleepSessionRecord", e)
            firstFailure = firstFailure ?: "${e.javaClass.simpleName}: ${e.message}"
            emptyList()
        }

        if (sleepSessions.isNotEmpty()) {
            val fromSleep = try {
                client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range))
                    .records
                    .flatMap { it.samples }
                    .filter { sample -> sleepSessions.any { it.startTime <= sample.time && sample.time < it.endTime } }
                    .map { it.beatsPerMinute }
            } catch (e: Exception) {
                FoxLogger.e("FOX-HEALTH-CONNECT | Échec lecture HeartRateRecord (croisement sommeil)", e)
                firstFailure = firstFailure ?: "${e.javaClass.simpleName}: ${e.message}"
                emptyList()
            }

            if (fromSleep.isNotEmpty()) {
                val average = fromSleep.average().toInt()
                FoxLogger.i(
                    "FOX-HEALTH-CONNECT | Référence calculée depuis ${fromSleep.size} échantillons BPM " +
                            "pendant ${sleepSessions.size} session(s) de sommeil : $average bpm"
                )
                return RestingBaselineResult.Success(average)
            }
        }

        // 2) RestingHeartRateRecord.
        val fromResting = try {
            client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range))
                .records
                .map { it.beatsPerMinute }
        } catch (e: Exception) {
            FoxLogger.e("FOX-HEALTH-CONNECT | Échec lecture RestingHeartRateRecord", e)
            firstFailure = firstFailure ?: "${e.javaClass.simpleName}: ${e.message}"
            emptyList()
        }

        if (fromResting.isNotEmpty()) {
            val average = fromResting.average().toInt()
            FoxLogger.i("FOX-HEALTH-CONNECT | Référence calculée depuis ${fromResting.size} RestingHeartRateRecord : $average bpm")
            return RestingBaselineResult.Success(average)
        }

        // 3) Repli : 10e percentile des échantillons bruts.
        val fromRawSamples = try {
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range))
                .records
                .flatMap { it.samples }
                .map { it.beatsPerMinute }
                .sorted()
        } catch (e: Exception) {
            FoxLogger.e("FOX-HEALTH-CONNECT | Échec lecture HeartRateRecord", e)
            firstFailure = firstFailure ?: "${e.javaClass.simpleName}: ${e.message}"
            emptyList()
        }

        if (fromRawSamples.isEmpty()) {
            // Une VRAIE exception (pas juste une liste vide) sur l'une des
            // trois sources doit être rapportée distinctement de "aucune
            // donnée" — sinon un échec de lecture (permission révoquée
            // après coup, Health Connect en cours de mise à jour...) se
            // déguise en "aucune donnée exploitable" alors que l'utilisateur
            // voit pourtant des données dans l'app Health Connect elle-même.
            if (firstFailure != null) {
                return RestingBaselineResult.ReadFailed(firstFailure)
            }
            FoxLogger.w("FOX-HEALTH-CONNECT | Aucune donnée BPM exploitable sur les ${lookback.toDays()} derniers jours")
            return RestingBaselineResult.NoData
        }

        // 10e percentile comme proxy du "repos" — pas le minimum absolu
        // (trop sensible à un artefact capteur isolé).
        val percentileIndex = (fromRawSamples.size * 0.10).toInt().coerceIn(0, fromRawSamples.size - 1)
        val baseline = fromRawSamples[percentileIndex].toInt()
        FoxLogger.i("FOX-HEALTH-CONNECT | Référence calculée depuis ${fromRawSamples.size} échantillons bruts (10e percentile) : $baseline bpm")
        return RestingBaselineResult.Success(baseline)
    }
}
