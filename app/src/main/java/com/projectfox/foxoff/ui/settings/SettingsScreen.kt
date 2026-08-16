package com.projectfox.foxoff.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.projectfox.foxoff.core.application.ActiveHoursSettings
import com.projectfox.foxoff.core.application.ActiveHoursSlot
import com.projectfox.foxoff.core.application.BackgroundServiceSettings
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.application.RestingBpmSettings
import com.projectfox.foxoff.core.application.WatchSettings
import com.projectfox.foxoff.core.health.HealthConnectBaselineProvider
import com.projectfox.foxoff.core.health.RestingBaselineResult
import com.projectfox.foxoff.core.service.BackgroundServiceDisableReason
import com.projectfox.foxoff.core.service.FoxForegroundService
import com.projectfox.foxoff.core.service.FoxForegroundServiceState
import com.projectfox.foxoff.core.service.FoxForegroundServiceStatus
import com.projectfox.foxoff.core.service.FoxServiceReconciliation
import com.projectfox.foxoff.core.watch.WatchBrand
import com.projectfox.foxoff.MainActivity
import com.projectfox.foxoff.ui.onboarding.OnboardingSettings
import com.projectfox.foxoff.ui.onboarding.components.FoxCard
import com.projectfox.foxoff.ui.onboarding.components.FoxGradientBackground
import com.projectfox.foxoff.ui.theme.FoxElectricBlue
import com.projectfox.foxoff.ui.theme.FoxElectricViolet
import kotlinx.coroutines.launch

/**
 * Écran Réglages : surveillance en arrière-plan (tâche #13), réinitialisation
 * de l'onboarding. La pause TV automatique liée au sommeil est un
 * comportement central de l'application, toujours actif — voir
 * SleepPauseCoordinator pour le compte à rebours annulable, non exposé ici
 * comme un réglage désactivable.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenHealth: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenTv: () -> Unit = {}
) {
    val context = LocalContext.current

    var notificationDenied by remember { mutableStateOf(false) }
    var bluetoothDenied by remember { mutableStateOf(false) }
    var showResetOnboardingConfirm by remember { mutableStateOf(false) }
    var showChangeWatchDialog by remember { mutableStateOf(false) }
    // Ne change que via cet écran, qui redémarre l'Activity juste après
    // (comme resetOnboarding()) — un simple remember suffit, pas besoin
    // d'un miroir StateFlow ici.
    var watchBrand by remember { mutableStateOf(WatchSettings.getWatchBrand(context)) }
    // État RÉEL du service, distinct de l'intention persistée ci-dessus —
    // voir FoxForegroundServiceState.
    val realStatus by FoxForegroundServiceState.status.collectAsState()
    // Intention persistée : observée via son miroir réactif
    // (BackgroundServiceSettings.enabledState), PAS un `remember` local lu
    // une seule fois à la composition — sans quoi un changement décidé en
    // dehors de cet écran (failAndStop() suite à un balayage de la
    // notification, réconciliation au retour au premier plan...) ne serait
    // visible qu'en quittant puis en rouvrant l'écran (bug déjà rencontré).
    val backgroundEnabled by BackgroundServiceSettings.enabledState.collectAsState()
    // Créneaux horaires dans lesquels la surveillance a le droit de tourner
    // (voir ActiveHoursSettings) — ensemble vide = aucune restriction.
    val selectedSlots by ActiveHoursSettings.selectedSlotsState.collectAsState()
    LaunchedEffect(Unit) {
        ActiveHoursSettings.getSelectedSlots(context)
    }
    // Accès "Accès aux notifications" (pause média téléphone, voir
    // PhoneMediaPauseController) — état système, pas de miroir StateFlow
    // dédié : revérifié à chaque retour au premier plan (l'octroi se fait
    // dans une app Réglages externe, pas de callback direct).
    var hasPhoneMediaAccess by remember {
        mutableStateOf(com.projectfox.foxoff.core.media.PhoneMediaPauseController.hasNotificationAccess(context))
    }
    val settingsLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(settingsLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPhoneMediaAccess = com.projectfox.foxoff.core.media.PhoneMediaPauseController.hasNotificationAccess(context)
            }
        }
        settingsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { settingsLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Raison d'une désactivation AUTOMATIQUE (notification balayée,
    // prérequis devenus invalides, réconciliation au retour au premier
    // plan...) — même logique d'état partagé que ci-dessus.
    val disableReason by BackgroundServiceDisableReason.reason.collectAsState()
    // Référence BPM de repos calibrée depuis Samsung Health (Health
    // Connect), null tant qu'elle n'a pas encore été calculée — voir
    // RestingBpmSettings.
    val calibratedBpm by RestingBpmSettings.calibratedBpm.collectAsState()
    var calibrationInProgress by remember { mutableStateOf(false) }
    var calibrationError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        RestingBpmSettings.getCalibratedBpm(context)
    }

    fun runCalibration() {
        calibrationError = null
        calibrationInProgress = true
        coroutineScope.launch {
            when (val result = HealthConnectBaselineProvider.computeRestingBaseline(context)) {
                is RestingBaselineResult.Success -> {
                    RestingBpmSettings.saveCalibratedBpm(context, result.bpm)
                    FoxCore.brain.setRestingBpmBaseline(result.bpm)
                }
                is RestingBaselineResult.NoData -> {
                    calibrationError = "Aucune donnée de fréquence cardiaque exploitable trouvée " +
                            "sur les 60 derniers jours dans Health Connect."
                }
                is RestingBaselineResult.ReadFailed -> {
                    // Distinct de "aucune donnée" : la lecture a réellement
                    // échoué (permission révoquée après coup, Health Connect
                    // indisponible...) — afficher la vraie cause plutôt que
                    // de la déguiser en "aucune donnée" (bug corrigé le
                    // 2026-08-09, voir HealthConnectBaselineProvider).
                    calibrationError = "Échec de lecture Health Connect : ${result.cause}"
                }
            }
            calibrationInProgress = false
        }
    }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        HealthConnectBaselineProvider.permissionRequestContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectBaselineProvider.requiredPermissions)) {
            runCalibration()
        } else {
            calibrationError = "Autorisation Health Connect refusée."
        }
    }

    fun startCalibration() {
        calibrationError = null
        if (!HealthConnectBaselineProvider.isAvailable(context)) {
            calibrationError = "Health Connect n'est pas installé sur cet appareil."
            return
        }
        coroutineScope.launch {
            if (HealthConnectBaselineProvider.hasPermission(context)) {
                runCalibration()
            } else {
                healthConnectPermissionLauncher.launch(HealthConnectBaselineProvider.requiredPermissions)
            }
        }
    }

    fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, FoxForegroundService::class.java))
    }

    fun stopService() {
        context.stopService(Intent(context, FoxForegroundService::class.java))
    }

    fun hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun startIfAllowed() {
        BackgroundServiceSettings.setEnabled(context, true)
        BackgroundServiceDisableReason.set(null)
        notificationDenied = false
        bluetoothDenied = false
        startService()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startIfAllowed()
        } else {
            // Le réglage reste désactivé : décision de transparence propre à
            // FoxOFF, pas une obligation technique Android (voir
            // FoxNotificationVisibility) — nous refusons volontairement une
            // surveillance dont la notification ne serait pas visible.
            BackgroundServiceSettings.setEnabled(context, false)
            notificationDenied = true
        }
    }

    // Corrige un vrai crash constaté le 2026-08-14 sur émulateur :
    // Context.startForegroundService() engage Android à ce que le service
    // appelle Service.startForeground() sous quelques secondes.
    // FoxForegroundService.prerequisitesMet() vérifie déjà BLUETOOTH_CONNECT
    // en plus de la visibilité de la notification — mais si cette
    // permission manque au moment de l'appel, le service s'arrête via
    // failAndStop() SANS avoir appelé startForeground(), et Android tue
    // alors TOUTE l'application (ForegroundServiceDidNotStartInTimeException),
    // pas juste ce service. La permission est normalement déjà accordée
    // pendant l'onboarding (écran Permissions), mais reste révocable à tout
    // moment depuis les réglages système — d'où cette vérification AVANT
    // startForegroundService(), jamais après. Même correctif appliqué à
    // FoxHomeHero (Accueil), qui appelle ce même service.
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (hasNotificationPermission()) {
                startIfAllowed()
            } else {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            BackgroundServiceSettings.setEnabled(context, false)
            bluetoothDenied = true
        }
    }

    fun enableBackgroundSurveillance() {
        if (!hasBluetoothPermission()) {
            bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else if (!hasNotificationPermission()) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startIfAllowed()
        }
    }

    fun disableBackgroundSurveillance() {
        BackgroundServiceSettings.setEnabled(context, false)
        BackgroundServiceDisableReason.set(null)
        notificationDenied = false
        bluetoothDenied = false
        stopService()
    }

    fun toggleActiveHoursSlot(slot: ActiveHoursSlot) {
        val updated = if (slot in selectedSlots) selectedSlots - slot else selectedSlots + slot
        ActiveHoursSettings.setSelectedSlots(context, updated)
        // Réconciliation immédiate : si le créneau qu'on vient de (dé)cocher
        // couvre l'heure actuelle, la surveillance démarre/s'arrête tout de
        // suite plutôt que d'attendre le prochain passage du worker
        // périodique (jusqu'à 15 min, voir FoxActiveHoursWorker).
        FoxServiceReconciliation.reconcileNow(context)
    }

    fun resetOnboarding() {
        OnboardingSettings.reset(context)
        // MainActivity ne lit OnboardingSettings.isCompleted() qu'une seule
        // fois, dans un `remember` au lancement (voir MainActivity.kt) — un
        // redémarrage complet de l'Activity est donc nécessaire pour que le
        // reset soit effectif immédiatement, plutôt que de faire remonter un
        // callback à travers plusieurs niveaux de composables.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }

    fun changeWatchBrand(newBrand: WatchBrand) {
        WatchSettings.saveWatchBrand(context, newBrand)
        // Efface l'ancienne montre connue : son identifiant (nœud Wear OS ou
        // appareil Garmin) n'a aucun sens pour l'autre marque.
        WatchSettings.clearKnownDevice(context)
        // Même raison que resetOnboarding() : FoxCore.initialize() ne lit le
        // transport qu'une fois au démarrage du processus, un redémarrage
        // complet est nécessaire pour qu'il prenne effet.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }

    FoxGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "RÉGLAGES",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // RACCOURCIS — Santé, Historique et TV n'ont plus leur propre
            // onglet dans la barre du bas (2026-08-14, "TV il ne sert a
            // rien ici") — mis en avant ici sous forme de 3 tuiles
            // (2026-08-16, "tout soit intuitif et beau visuellement") plutôt
            // qu'une liste générique à puces, pour un accès rapide en un
            // coup d'œil, cohérent avec le style de la grille de l'Accueil.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsShortcutTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Favorite,
                    label = "Santé",
                    accent = SettingsRed,
                    onClick = onOpenHealth
                )
                SettingsShortcutTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.History,
                    label = "Historique",
                    accent = FoxElectricBlue,
                    onClick = onOpenHistory
                )
                SettingsShortcutTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Tv,
                    label = "TV",
                    accent = FoxElectricViolet,
                    onClick = onOpenTv
                )
            }

            SettingsSectionLabel("SURVEILLANCE")

            FoxCard {
                Column {
                    SettingsCardHeader(icon = Icons.Rounded.Bedtime, accent = FoxElectricBlue, title = "Surveillance en arrière-plan") {
                        Switch(
                            checked = backgroundEnabled,
                            onCheckedChange = { checked ->
                                if (checked) enableBackgroundSurveillance() else disableBackgroundSurveillance()
                            }
                        )
                    }

                    val (statusLabel, statusColor) = when {
                        !backgroundEnabled -> "Arrêtée" to Color.Gray
                        realStatus == FoxForegroundServiceStatus.RUNNING -> "Active" to SettingsGreen
                        realStatus == FoxForegroundServiceStatus.ERROR -> "Erreur de démarrage" to SettingsRed
                        realStatus == FoxForegroundServiceStatus.STARTING -> "Démarrage en cours..." to SettingsOrange
                        else -> "Activée — en attente de démarrage" to SettingsOrange
                    }
                    SettingsStatusRow(statusLabel, statusColor)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Garde FoxOFF actif pour continuer à recevoir les données de la " +
                                "montre lorsque l'application est fermée. Nécessite l'affichage " +
                                "d'une notification permanente, pour que vous puissiez toujours " +
                                "voir qu'elle est active.",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (notificationDenied) {
                        SettingsInlineWarning("Autorisation de notification refusée : la surveillance permanente n'a pas été activée.")
                    }

                    if (bluetoothDenied) {
                        SettingsInlineWarning("Autorisation Bluetooth refusée : nécessaire pour communiquer avec la montre, la surveillance n'a pas été activée.")
                    }

                    // Désactivation AUTOMATIQUE décidée au retour au premier
                    // plan (voir FoxServiceReconciliation) — distincte du
                    // refus ponctuel ci-dessus, survient sans action de
                    // l'utilisateur sur cet écran.
                    disableReason?.let { reason -> SettingsInlineWarning(reason) }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FoxCard {
                Column {
                    SettingsCardHeader(icon = Icons.Rounded.RestartAlt, accent = FoxElectricViolet, title = "Créneaux horaires actifs")
                    Text(
                        text = "Limite la surveillance aux créneaux sélectionnés (ex. seulement " +
                                "le soir) plutôt qu'en continu toute la journée. Aucun créneau " +
                                "coché = aucune restriction.",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActiveHoursSlot.entries.forEach { slot ->
                            FilterChip(
                                selected = slot in selectedSlots,
                                onClick = { toggleActiveHoursSlot(slot) },
                                label = { Text(slot.label) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FoxCard {
                Column {
                    SettingsCardHeader(icon = Icons.Rounded.PhoneAndroid, accent = FoxElectricBlue, title = "Pause du téléphone")
                    SettingsStatusRow(
                        if (hasPhoneMediaAccess) "Accès accordé" else "Accès non accordé",
                        if (hasPhoneMediaAccess) SettingsGreen else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "En plus de la TV, met en pause la vidéo ou la musique en cours " +
                                "sur ce téléphone (YouTube, Spotify...) au moment de " +
                                "l'endormissement. Optionnel — nécessite l'accès spécial " +
                                "\"Accès aux notifications\" d'Android.",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!hasPhoneMediaAccess) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsActionButton(
                            text = "Activer l'accès aux notifications",
                            accent = FoxElectricBlue,
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            }
                        )
                    }
                }
            }

            SettingsSectionLabel("APPAREILS & DONNÉES")

            FoxCard {
                Column {
                    SettingsCardHeader(icon = Icons.Rounded.Watch, accent = FoxElectricBlue, title = "Montre")
                    Text(
                        text = "Marque active : " + when (watchBrand) {
                            WatchBrand.WEAR_OS -> "Wear OS (Samsung Galaxy Watch et compatibles)"
                            WatchBrand.GARMIN -> "Garmin (Connect IQ)"
                        },
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsActionButton(
                        text = "Changer de montre",
                        accent = FoxElectricBlue,
                        onClick = { showChangeWatchDialog = true }
                    )
                }
            }

            // Health Connect est alimenté par l'app compagnon de la montre
            // active — Samsung Health (Wear OS) ou Garmin Connect (Garmin,
            // qui a lui aussi une intégration native Health Connect depuis
            // ses réglages propres) — HealthConnectBaselineProvider lit
            // Health Connect de façon générique, sans se soucier de quelle
            // app y a écrit les données : seul le libellé change ici.
            run {
                val healthSourceName = when (watchBrand) {
                    WatchBrand.WEAR_OS -> "Samsung Health"
                    WatchBrand.GARMIN -> "Garmin Connect"
                }

                Spacer(modifier = Modifier.height(14.dp))

                FoxCard {
                    Column {
                        SettingsCardHeader(icon = Icons.Rounded.MonitorHeart, accent = SettingsRed, title = "BPM de repos")
                        SettingsStatusRow(
                            if (calibratedBpm != null) "$calibratedBpm bpm — calibré depuis $healthSourceName" else "70 bpm — valeur générique (non calibrée)",
                            if (calibratedBpm != null) SettingsGreen else Color.Gray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Utilise votre historique de fréquence cardiaque (Health " +
                                    "Connect, alimenté par $healthSourceName) pour remplacer la " +
                                    "moyenne générique par une référence propre à vous. Lecture " +
                                    "seule, aucune donnée n'est envoyée hors de l'appareil.",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall
                        )

                        calibrationError?.let { error -> SettingsInlineWarning(error) }

                        Spacer(modifier = Modifier.height(12.dp))
                        if (calibrationInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = FoxElectricBlue)
                        } else {
                            // Empilés verticalement plutôt que côte à côte :
                            // deux boutons pleine largeur dans un Row
                            // débordaient hors de l'écran sur téléphone (le
                            // second bouton, "Ouvrir Health Connect",
                            // devenait invisible et donc inatteignable — bug
                            // réel constaté le 2026-08-10 via capture
                            // d'écran).
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsActionButton(
                                    text = "Calibrer avec $healthSourceName",
                                    accent = SettingsRed,
                                    onClick = { startCalibration() }
                                )
                                SettingsActionButton(
                                    text = "Ouvrir Health Connect",
                                    accent = SettingsRed,
                                    filled = false,
                                    onClick = {
                                        context.startActivity(HealthConnectBaselineProvider.manageDataIntent(context))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            SettingsSectionLabel("APPLICATION")

            FoxCard {
                Column {
                    SettingsCardHeader(icon = Icons.Rounded.Settings, accent = Color.White.copy(alpha = 0.6f), title = "Paramètres Android")
                    Text(
                        text = "Certains téléphones (notamment Samsung) limitent les " +
                                "applications en arrière-plan sauf autorisation manuelle. " +
                                "FoxOFF ne peut pas activer cette autorisation à votre place.",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsActionButton(
                        text = "Ouvrir les paramètres de l'application",
                        accent = Color.White.copy(alpha = 0.7f),
                        filled = false,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FoxCard {
                Column {
                    SettingsCardHeader(icon = Icons.Rounded.SettingsBackupRestore, accent = Color.White.copy(alpha = 0.6f), title = "Onboarding")
                    Text(
                        text = "Repasse par les écrans de configuration initiale (permissions, " +
                                "montre, TV...) au prochain lancement, sans réinstaller l'application.",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsActionButton(
                        text = "Réinitialiser l'onboarding",
                        accent = Color.White.copy(alpha = 0.7f),
                        filled = false,
                        onClick = { showResetOnboardingConfirm = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FoxCard {
                Column {
                    SettingsCardHeader(icon = Icons.Rounded.Warning, accent = SettingsOrange, title = "Limite importante", titleColor = SettingsOrange)
                    Text(
                        text = "Si vous forcez l'arrêt de FoxOFF depuis les paramètres Android, " +
                                "la surveillance s'arrête complètement et ne reprendra qu'à la " +
                                "réouverture manuelle de l'application. C'est une protection du " +
                                "système qu'aucune application ne peut contourner.",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showResetOnboardingConfirm) {
        AlertDialog(
            onDismissRequest = { showResetOnboardingConfirm = false },
            title = { Text("Réinitialiser l'onboarding ?") },
            text = {
                Text(
                    "Vous repasserez par les écrans de configuration initiale au " +
                            "prochain lancement de l'application. Vos réglages actuels " +
                            "(surveillance, montre, TV) ne sont pas effacés."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetOnboardingConfirm = false
                    resetOnboarding()
                }) {
                    Text("Réinitialiser")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetOnboardingConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showChangeWatchDialog) {
        AlertDialog(
            onDismissRequest = { showChangeWatchDialog = false },
            title = { Text("Changer de montre") },
            text = {
                Column {
                    Text(
                        "L'application redémarre pour appliquer le changement. Vous " +
                                "devrez ré-appairer la montre choisie depuis l'onboarding " +
                                "ou l'écran d'accueil."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WatchBrand.entries.forEach { brand ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = watchBrand == brand,
                                    onClick = { watchBrand = brand }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = watchBrand == brand, onClick = { watchBrand = brand })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (brand) {
                                    WatchBrand.WEAR_OS -> "Wear OS (Samsung Galaxy Watch et compatibles)"
                                    WatchBrand.GARMIN -> "Garmin (Connect IQ)"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showChangeWatchDialog = false
                    changeWatchBrand(watchBrand)
                }) {
                    Text("Confirmer et redémarrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeWatchDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

// Palette sémantique (2026-08-16, refonte visuelle) — mêmes valeurs
// exactes que FoxHomeOrange/Red/Green dans FoxHomeHero.kt (accueil), pour
// que "attention"/"erreur"/"OK" se ressemblent d'un écran à l'autre de
// l'app plutôt que de dériver (l'accueil et les réglages utilisaient
// jusque-là des rouges/verts légèrement différents — voir doc de l'audit
// de cet écran). FoxElectricBlue/Violet réutilisées telles quelles depuis
// le thème (ui/theme/Color.kt), pas redéfinies.
private val SettingsOrange = Color(0xFFFFA000)
private val SettingsRed = Color(0xFFFF5252)
private val SettingsGreen = Color(0xFF4CAF50)

/**
 * Tuile de raccourci vers un sous-écran de Paramètres (Santé, Historique,
 * TV — 2026-08-14, ces écrans ont perdu leur onglet dédié dans la barre du
 * bas). Icône dans un disque teinté + libellé, format compact 3-par-ligne
 * — remplace l'ancienne liste à puces/chevrons (2026-08-16, "tout soit
 * intuitif et beau visuellement"), même esprit que MiniStatCard de
 * l'Accueil pour une cohérence visuelle entre les deux écrans.
 */
@Composable
private fun SettingsShortcutTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Étiquette de section en petites capitales (SURVEILLANCE, APPAREILS &
 * DONNÉES, APPLICATION) — regroupe les cartes par thème plutôt que de les
 * empiler toutes au même niveau (2026-08-16, l'écran d'origine n'avait
 * aucune hiérarchie visuelle entre 9 cartes identiques d'affilée).
 */
@Composable
private fun SettingsSectionLabel(text: String) {
    Spacer(modifier = Modifier.height(28.dp))
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.4f),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
}

/**
 * En-tête de carte réutilisable : icône dans un disque teinté + titre, et
 * un slot optionnel à droite (ex. Switch) — donne à chaque carte un point
 * d'ancrage visuel fort plutôt qu'un simple Text en gras, cohérent avec le
 * langage iconographique des tuiles de raccourci ci-dessus.
 */
@Composable
private fun SettingsCardHeader(
    icon: ImageVector,
    accent: Color,
    title: String,
    titleColor: Color = Color.White,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
    Spacer(modifier = Modifier.height(10.dp))
}

/** Ligne de statut compacte (point coloré + texte) — même motif que MiniStatCard (Accueil). */
@Composable
private fun SettingsStatusRow(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/** Message d'avertissement inline (permission refusée, erreur de calibration...). */
@Composable
private fun SettingsInlineWarning(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = text, color = SettingsRed, style = MaterialTheme.typography.bodySmall)
}

/**
 * Bouton d'action pleine largeur, plus affirmé visuellement que le
 * `OutlinedButton` Material3 par défaut (contour fin, peu contrasté sur
 * fond sombre — voir audit de cet écran, 2026-08-16) sans pour autant
 * rivaliser avec `FoxButton` (dégradé plein, réservé aux CTA vraiment
 * principaux ailleurs dans l'app). `filled=true` (défaut) : fond teinté +
 * bordure pleine, pour l'action la plus probable de la carte.
 * `filled=false` : contour seul, pour une action secondaire.
 */
@Composable
private fun SettingsActionButton(
    text: String,
    accent: Color,
    filled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, accent.copy(alpha = if (filled) 0.4f else 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = text, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}
