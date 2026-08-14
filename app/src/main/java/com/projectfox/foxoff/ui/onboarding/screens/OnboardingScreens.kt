package com.projectfox.foxoff.ui.onboarding.screens

import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.projectfox.foxoff.ui.onboarding.components.*

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    // Tout premier écran affiché au lancement de l'app (2026-08-14 :
    // l'écran "splash" animé qui s'affichait avant a été retiré du flux
    // de navigation — plus de délai/pause noire avant d'arriver ici).
    // FoxOnboardingBackground (image ciel étoilé + forêt, initialement
    // ajoutée ici) est désormais partagée par tout le flux d'onboarding.
    FoxOnboardingBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FoxProgressConnection(step = 1)
            // Illustration "renard endormi sur la lune" fournie par
            // l'utilisateur (2026-08-14, remplace le badge circulaire) —
            // fox_on_moon_blended.png est dérivé de cette image (recadrée
            // sous la lune pour retirer le ciel vide en bas, qui gonflait
            // inutilement la hauteur de la boîte et empêchait "FoxOFF" de
            // remonter juste en dessous) avec un dégradé alpha elliptique
            // (opaque au centre, transparent vers les bords) généré par
            // script, pour qu'elle se fonde dans bg_welcome_night.jpg
            // plutôt que d'apparaître comme un rectangle collé par-dessus.
            // Recadré une 2e fois (2026-08-14, "encore" plus haut) : haut
            // ET bas rognés au plus près de la lune (511:700 désormais),
            // pour que la boîte n'ait plus de marge étoilée inutile ni en
            // haut ni en bas.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.projectfox.foxoff.R.drawable.fox_on_moon_blended),
                contentDescription = "Fox endormi sur la lune",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .width(270.dp)
                    .height(370.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            FoxWordmark(fontSize = 60.sp)
            Spacer(modifier = Modifier.height(16.dp))
            // Reformulé le 2026-08-14 : le sommeil en sujet principal, la
            // pause TV citée comme une des actions plutôt que LE sujet du
            // message (demande explicite : "le message ne doit pas être
            // principalement la télévision").
            FoxSubtitle(text = "FoxOFF veille sur votre sommeil grâce à votre montre connectée, et met en pause ce que vous regardez dès que vous vous endormez.")
            Spacer(modifier = Modifier.weight(1f))
            FoxButton(
                text = "Commencer",
                onClick = onNext,
                gradient = listOf(Color(0xFFFFA000), Color(0xFFFF6D00))
            )
        }
    }
}

@Composable
fun PermissionScreen(onNext: () -> Unit) {
    val context = LocalContext.current

    // POST_NOTIFICATIONS n'est PAS incluse ici : elle est demandée à part,
    // à la suite de cette première demande (voir resolveBackgroundStep),
    // jamais mélangée dans le même appel système.
    val permissions = remember {
        mutableListOf<String>().apply {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    var permissionStatus by remember {
        mutableStateOf(permissions.associateWith {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    // null = pas encore résolu (ni activé, ni décliné) ; true/false = choix final.
    // Repris depuis le stockage au cas où l'écran serait recréé après que le
    // choix ait déjà été fait (ex: rotation, recréation de l'Activity).
    var backgroundChoice by remember {
        mutableStateOf(
            if (com.projectfox.foxoff.core.application.BackgroundServiceSettings.hasSeenPrompt(context)) {
                com.projectfox.foxoff.core.application.BackgroundServiceSettings.isEnabled(context)
            } else {
                null
            }
        )
    }
    var backgroundDenied by remember { mutableStateOf(false) }

    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        com.projectfox.foxoff.core.application.BackgroundServiceSettings.setEnabled(context, granted)
        com.projectfox.foxoff.core.application.BackgroundServiceSettings.markPromptSeen(context)
        backgroundChoice = granted
        backgroundDenied = !granted
        if (granted) {
            com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-PERM | Notifications accordées -> surveillance activée")
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                android.content.Intent(context, com.projectfox.foxoff.core.service.FoxForegroundService::class.java)
            )
        } else {
            com.projectfox.foxoff.core.logging.FoxLogger.w("FOX-PERM | Notifications refusées -> surveillance non activée")
        }
    }

    // Deuxième étape de la séquence guidée : appelée juste après la
    // résolution de la demande Bluetooth/Wi-Fi. Ne redemande jamais si
    // l'utilisateur a déjà répondu (ex: "Continuer sans surveillance" tapé
    // avant, ou choix déjà fait lors d'une précédente composition).
    fun resolveBackgroundStep() {
        if (com.projectfox.foxoff.core.application.BackgroundServiceSettings.hasSeenPrompt(context)) {
            return
        }

        val notificationsNeeded = android.os.Build.VERSION.SDK_INT >= 33
        val alreadyGranted = !notificationsNeeded ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            // Rien à demander (version d'Android sans permission requise, ou
            // déjà accordée) : l'intention peut être enregistrée directement.
            com.projectfox.foxoff.core.application.BackgroundServiceSettings.setEnabled(context, true)
            com.projectfox.foxoff.core.application.BackgroundServiceSettings.markPromptSeen(context)
            backgroundChoice = true
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                android.content.Intent(context, com.projectfox.foxoff.core.service.FoxForegroundService::class.java)
            )
        } else {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val accessLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionStatus = results
        results.forEach { (perm, granted) ->
            if (granted) com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-PERM | Permission accordée : $perm")
            else com.projectfox.foxoff.core.logging.FoxLogger.w("FOX-PERM | Permission refusée : $perm")
        }
        // Séquence guidée : une fois Bluetooth/Wi-Fi résolus, on enchaîne
        // immédiatement sur la notification de surveillance.
        resolveBackgroundStep()
    }

    fun grantAllAccess() {
        if (permissions.isNotEmpty()) {
            com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-PERM | Lancement demande globale")
            accessLauncher.launch(permissions.toTypedArray())
        } else {
            // Rien à demander à cette étape (ex: SDK trop ancien) : passe
            // directement à la surveillance en arrière-plan.
            resolveBackgroundStep()
        }
    }

    fun declineBackgroundNow() {
        com.projectfox.foxoff.core.application.BackgroundServiceSettings.setEnabled(context, false)
        com.projectfox.foxoff.core.application.BackgroundServiceSettings.markPromptSeen(context)
        backgroundChoice = false
        backgroundDenied = false
    }

    val corePermissionsGranted = permissionStatus.values.all { it }
    // Le bouton ne devient "Continuer" qu'une fois TOUT résolu : les
    // permissions de base ET le choix de surveillance (explicite ou via la
    // séquence guidée) — jamais avant, sinon la notification ne serait
    // jamais demandée si Bluetooth/Wi-Fi étaient déjà accordés.
    val readyToContinue = corePermissionsGranted && backgroundChoice != null

    FoxOnboardingBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 2)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Autorisations")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(text = "FoxOFF nécessite ces accès pour fonctionner correctement.")

            Spacer(modifier = Modifier.height(32.dp))

            // Zone défilante : cartes de permissions + section surveillance.
            // Le titre/sous-titre et le bouton final restent fixes, pour que
            // le bouton reste toujours atteignable sur un petit écran même
            // avec cette section supplémentaire.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    PermissionStatusCard(
                        icon = "⌚",
                        title = "Appareils à proximité",
                        description = "Rechercher et se connecter à votre montre.",
                        isGranted = permissionStatus[android.Manifest.permission.BLUETOOTH_CONNECT] == true
                    )
                }

                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    PermissionStatusCard(
                        icon = "📡",
                        title = "Réseau Local",
                        description = "Détecter votre télévision sur le WiFi.",
                        isGranted = permissionStatus[android.Manifest.permission.NEARBY_WIFI_DEVICES] == true
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(16.dp)
                ) {
                    com.projectfox.foxoff.ui.settings.BackgroundMonitoringInfoSection(
                        resolvedChoice = backgroundChoice,
                        deniedMessage = backgroundDenied,
                        onDeclineNow = { declineBackgroundNow() }
                    )
                }
            }

            if (!readyToContinue) {
                FoxButton(
                    text = "Accorder les accès",
                    onClick = { grantAllAccess() }
                )
            } else {
                FoxButton(
                    text = "Continuer",
                    onClick = onNext
                )
            }
        }
    }
}

@Composable
fun PermissionStatusCard(icon: String, title: String, description: String, isGranted: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(if (isGranted) Color.White.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = description, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            if (isGranted) {
                Text(text = "ACCORDÉ", color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Black)
            } else {
                Text(text = "REQUIS", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/**
 * Créneaux horaires prédéfinis dans lesquels la surveillance a le droit de
 * tourner (voir ActiveHoursSettings) — demande explicite de l'utilisateur :
 * la surveillance tournait "du matin au soir" sans utilité en dehors de
 * l'endormissement. "20h – 8h" pré-coché par défaut (cas d'usage principal,
 * modifiable avant de continuer et plus tard depuis Réglages).
 */
@Composable
fun ActiveHoursScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    var selectedSlots by remember {
        mutableStateOf(
            if (com.projectfox.foxoff.core.application.ActiveHoursSettings.hasSeenPrompt(context)) {
                com.projectfox.foxoff.core.application.ActiveHoursSettings.getSelectedSlots(context)
            } else {
                setOf(com.projectfox.foxoff.core.application.ActiveHoursSlot.EVENING_NIGHT)
            }
        )
    }

    fun toggle(slot: com.projectfox.foxoff.core.application.ActiveHoursSlot) {
        selectedSlots = if (slot in selectedSlots) selectedSlots - slot else selectedSlots + slot
    }

    fun confirmAndContinue() {
        com.projectfox.foxoff.core.application.ActiveHoursSettings.setSelectedSlots(context, selectedSlots)
        com.projectfox.foxoff.core.application.ActiveHoursSettings.markPromptSeen(context)
        onNext()
    }

    FoxOnboardingBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 3)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Créneaux horaires")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(text = "Choisissez quand FoxOFF doit surveiller votre sommeil, pour ne " +
                    "pas tourner inutilement le reste de la journée. Modifiable plus tard " +
                    "dans Réglages.")

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                com.projectfox.foxoff.core.application.ActiveHoursSlot.entries.forEach { slot ->
                    ActiveHoursSlotCard(
                        slot = slot,
                        isSelected = slot in selectedSlots,
                        onClick = { toggle(slot) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            FoxButton(text = "Continuer", onClick = { confirmAndContinue() })
        }
    }
}

@Composable
fun ActiveHoursSlotCard(
    slot: com.projectfox.foxoff.core.application.ActiveHoursSlot,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🌙", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = slot.label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * Demande de l'accès spécial "Accès aux notifications", requis pour mettre
 * en pause la lecture média du téléphone (vidéo/musique — YouTube,
 * Spotify...) en plus de la TV, voir PhoneMediaPauseController. Non
 * bloquant : "Continuer" reste toujours actif, l'utilisateur peut aussi
 * l'activer plus tard depuis Réglages — best-effort comme le reste des
 * fonctionnalités liées à la pause (rien ne casse si refusé).
 */
@Composable
fun PhoneMediaPauseScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    var hasAccess by remember {
        mutableStateOf(com.projectfox.foxoff.core.media.PhoneMediaPauseController.hasNotificationAccess(context))
    }

    // L'octroi de cet accès se fait dans une app Réglages externe (pas de
    // ActivityResultContract dédié pour ce type de permission spéciale) :
    // on ne peut détecter le retour qu'au prochain ON_RESUME du cycle de
    // vie, pas via un callback direct.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAccess = com.projectfox.foxoff.core.media.PhoneMediaPauseController.hasNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FoxOnboardingBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 4)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Pause du téléphone")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(
                text = "En plus de la TV, FoxOFF peut aussi mettre en pause la vidéo ou la " +
                        "musique en cours sur ce téléphone (YouTube, Spotify...) au moment de " +
                        "l'endormissement. Ceci nécessite l'accès spécial \"Accès aux " +
                        "notifications\" d'Android."
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (hasAccess) "✅" else "🔔", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasAccess) "Accès accordé" else "Accès non accordé",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Optionnel — la pause TV fonctionne dans tous les cas.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!hasAccess) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Activer l'accès aux notifications")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.weight(1f))
            FoxButton(text = "Continuer", onClick = onNext)
        }
    }
}

/**
 * Étape insérée le 2026-08-13, avant WatchDetectionScreen — bug réel
 * signalé par un testeur Garmin : sans ce choix, WatchDetectionScreen
 * cherchait toujours une Galaxy Watch (WatchBrand.WEAR_OS, valeur par
 * défaut de WatchSettings.getWatchBrand() tant que rien n'a été
 * enregistré), donc ne trouvait jamais une montre Garmin. Réutilise
 * exactement le même mécanisme que "Changer de montre" dans Réglages
 * (WatchBrand/WatchSettings), y compris le redémarrage complet de l'app
 * si la marque change : FoxCore.initialize() ne lit
 * WatchSettings.getWatchBrand() qu'une seule fois au démarrage du
 * processus (voir FoxCore.kt), donc changer la préférence seule ne
 * suffit pas à faire prendre effet un nouveau WatchTransport tant que
 * le processus n'est pas relancé.
 */
@Composable
fun WatchBrandScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    var selectedBrand by remember {
        mutableStateOf(com.projectfox.foxoff.core.application.WatchSettings.getWatchBrand(context))
    }

    FoxOnboardingBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 5)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Quelle montre utilisez-vous ?")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(text = "FoxOFF adapte sa recherche selon la marque de votre montre connectée.")

            Spacer(modifier = Modifier.height(32.dp))

            com.projectfox.foxoff.core.watch.WatchBrand.entries.forEach { brand ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = if (selectedBrand == brand) 0.12f else 0.05f))
                        .selectable(
                            selected = selectedBrand == brand,
                            onClick = { selectedBrand = brand }
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedBrand == brand, onClick = { selectedBrand = brand })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (brand) {
                            com.projectfox.foxoff.core.watch.WatchBrand.WEAR_OS -> "Wear OS (Samsung Galaxy Watch et compatibles)"
                            com.projectfox.foxoff.core.watch.WatchBrand.GARMIN -> "Garmin (Connect IQ)"
                        },
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.weight(1f))
            FoxButton(text = "Continuer", onClick = {
                val activeBrand = com.projectfox.foxoff.core.application.WatchSettings.getWatchBrand(context)
                if (selectedBrand != activeBrand) {
                    // Changement de marque : le transport actif (résolu une
                    // seule fois par FoxCore.initialize() au démarrage du
                    // processus) ne peut être remplacé qu'en relançant l'app
                    // — même mécanisme que SettingsScreen "Changer de montre".
                    com.projectfox.foxoff.core.application.WatchSettings.saveWatchBrand(context, selectedBrand)
                    com.projectfox.foxoff.core.application.WatchSettings.clearKnownDevice(context)
                    val intent = android.content.Intent(context, com.projectfox.foxoff.MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                } else {
                    onNext()
                }
            })
        }
    }
}

@Composable
fun WatchDetectionScreen(onNext: () -> Unit) {
    val core = com.projectfox.foxoff.core.application.FoxCore
    val context = LocalContext.current
    val watchInfo by core.watchInfo.collectAsState()
    val isSearching = watchInfo == null
    val watchBrand = remember {
        com.projectfox.foxoff.core.application.WatchSettings.getWatchBrand(context)
    }

    LaunchedEffect(Unit) {
        if (watchInfo == null) {
            core.discoverWatch(context)
        }
    }

    LaunchedEffect(watchInfo) {
        if (watchInfo != null) {
            com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-WATCH | Onboarding received watch: ${watchInfo?.name}")
            com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-WATCH | Continue enabled")
        }
    }

    FoxOnboardingBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 6)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Détection de votre montre")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(
                text = when (watchBrand) {
                    com.projectfox.foxoff.core.watch.WatchBrand.WEAR_OS -> "FoxOFF recherche votre Galaxy Watch."
                    com.projectfox.foxoff.core.watch.WatchBrand.GARMIN -> "FoxOFF recherche votre montre Garmin."
                }
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 6.dp)
                Spacer(modifier = Modifier.height(32.dp))
                FoxSubtitle(text = "Assurez-vous que la montre est allumée et à proximité.")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⌚", fontSize = 48.sp)
                        Spacer(modifier = Modifier.width(24.dp))
                        Column {
                            Text(text = watchInfo?.name ?: "Montre détectée", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Connectée via Bluetooth", color = Color.Green, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            FoxButton(text = "Continuer", onClick = onNext, enabled = !isSearching)
        }
    }
}

@Composable
fun TvDetectionScreen(onNext: (String) -> Unit, onSkip: () -> Unit) {
    val core = com.projectfox.foxoff.core.application.FoxCore
    val tvEngine = core.tvEngine
    val discoveredDevices by (tvEngine?.discoveredDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    var selectedDevice by remember { mutableStateOf<com.projectfox.foxoff.tv.TvDevice?>(null) }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }

    val isSearching = discoveredDevices.isEmpty() && selectedDevice == null

    LaunchedEffect(Unit) {
        tvEngine?.initialize()
    }

    FoxOnboardingBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FoxProgressConnection(step = 7)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Configuration TV")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(text = "Choisissez la télévision que FoxOFF doit contrôler.")
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (isSearching) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp), color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Recherche en cours...", color = Color.Gray)
                    }
                }
            } else if (discoveredDevices.isEmpty() && selectedDevice == null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aucune télévision détectée", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { tvEngine?.initialize() }) {
                            Text("Relancer la recherche")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    discoveredDevices.forEach { device ->
                        TvDiscoveryCard(
                            device = device,
                            isSelected = selectedDevice?.id == device.id,
                            onClick = {
                                if (selectedDevice?.id != device.id) {
                                    selectedDevice = device
                                    com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | TV sélectionnée : ${device.name}")
                                    selectedDeviceId = when (val outcome = tvEngine?.selectDevice(device)) {
                                        is com.projectfox.foxoff.tv.TvSelectDeviceOutcome.NewPairingStarted -> outcome.deviceId
                                        is com.projectfox.foxoff.tv.TvSelectDeviceOutcome.AlreadyAssociated -> outcome.deviceId
                                        else -> null
                                    }
                                    com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | TV enregistrée comme télévision principale")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FoxButton(
                    text = "Continuer",
                    onClick = { selectedDeviceId?.let(onNext) },
                    enabled = selectedDeviceId != null
                )

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Configurer plus tard", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun SetupCompleteScreen(onFinish: () -> Unit) {
    FoxOnboardingBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            FoxProgressConnection(step = 8)
            Spacer(modifier = Modifier.weight(1f))
            FoxAnimatedLogo()
            Spacer(modifier = Modifier.height(32.dp))
            FoxTitle(text = "Configuration terminée")
            Spacer(modifier = Modifier.height(24.dp))
            FoxSubtitle(text = "Votre installation est prête.\n\nFoxOFF surveillera votre montre et mettra automatiquement votre télévision en pause lorsque vous vous endormirez.")
            Spacer(modifier = Modifier.weight(1f))
            FoxButton(text = "Commencer", onClick = onFinish)
        }
    }
}

@Composable
fun TvPairingScreen(deviceId: String?, onNext: () -> Unit) {
    val core = com.projectfox.foxoff.core.application.FoxCore
    val tvEngine = core.tvEngine
    val pairedDevices by (tvEngine?.pairedDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val tvState = pairedDevices.firstOrNull { it.id == deviceId }
    var pinValue by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | UI | Pairing screen opened")
    }

    LaunchedEffect(tvState?.status) {
        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | UI | Current status = ${tvState?.status}")
        if (tvState?.status == com.projectfox.foxoff.tv.TvConnectionStatus.CONNECTED) {
            onNext()
        }
    }

    FoxOnboardingBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 8)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Appairage de la télévision")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(text = "Saisissez le code affiché sur votre écran ${tvState?.name ?: ""}")
            
            Spacer(modifier = Modifier.height(48.dp))

            when (tvState?.status) {
                com.projectfox.foxoff.tv.TvConnectionStatus.PAIRING_REQUIRED -> {
                    FoxButton(text = "Lancer l'appairage", onClick = { tvEngine?.startPairing(tvState!!) })
                }
                com.projectfox.foxoff.tv.TvConnectionStatus.PAIRING_SENT -> {
                    com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | UI | Display PIN input")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = pinValue,
                            onValueChange = { newValue ->
                                val cleaned = newValue
                                    .uppercase()
                                    .filter { it.isLetterOrDigit() }
                                    .take(6)

                                pinValue = cleaned
                            },
                            label = {
                                Text("Code PIN")
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                                autoCorrectEnabled = false
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                        FoxButton(text = "Valider", onClick = { tvState?.let { tvEngine?.submitPin(pinValue, it.id) } }, enabled = pinValue.length == 6)
                    }
                }
                com.projectfox.foxoff.tv.TvConnectionStatus.CONNECTING -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Connexion en cours...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }
                com.projectfox.foxoff.tv.TvConnectionStatus.ERROR -> {
                    Text(
                        text = "❌ Appairage échoué",
                        color = Color.Red
                    )

                    FoxButton(
                        text = "Réessayer",
                        onClick = { tvEngine?.startPairing(tvState!!) }
                    )
                }
                else -> {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onNext) {
                Text("Passer cette étape", color = Color.Gray)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewWelcome() { com.projectfox.foxoff.ui.theme.FoxTheme { WelcomeScreen {} } }

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewWatch() { com.projectfox.foxoff.ui.theme.FoxTheme { WatchDetectionScreen {} } }

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewTv() { com.projectfox.foxoff.ui.theme.FoxTheme { TvDetectionScreen(onNext = {}, onSkip = {}) } }

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewPermissions() { com.projectfox.foxoff.ui.theme.FoxTheme { PermissionScreen {} } }

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewSetup() { com.projectfox.foxoff.ui.theme.FoxTheme { SetupCompleteScreen {} } }

@Composable
fun TvDiscoveryCard(
    device: com.projectfox.foxoff.tv.TvDevice,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isAndroidTv = device.services.any { it.contains("androidtv") }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isAndroidTv) "🖥️" else "📺", fontSize = 24.sp)
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${device.address} • ${device.manufacturer ?: "Fabricant inconnu"}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    device.services.take(2).forEach { service ->
                        val label = when {
                            service.contains("androidtvremote2") -> "Remote v2"
                            service.contains("googlecast") -> "Cast"
                            else -> service.split(".").firstOrNull() ?: ""
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = label, color = Color.Cyan.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                }
                
                if (device.lastResponseTimeMs > 0) {
                    Text(text = "⚡ ${device.lastResponseTimeMs}ms", color = Color.Gray, fontSize = 10.sp)
                }
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "✅ Sélectionnée",
                    color = Color.Green,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Utiliser cette TV", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
