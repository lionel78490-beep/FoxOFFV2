package com.projectfox.foxoff.ui.dashboard.components

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.projectfox.foxoff.brain.SleepState
import com.projectfox.foxoff.core.application.BackgroundServiceSettings
import com.projectfox.foxoff.core.media.PhoneMediaPauseController
import com.projectfox.foxoff.core.service.BackgroundServiceDisableReason
import com.projectfox.foxoff.core.service.FoxForegroundService
import com.projectfox.foxoff.core.service.FoxForegroundServiceState
import com.projectfox.foxoff.core.service.FoxForegroundServiceStatus
import com.projectfox.foxoff.tv.TvConnectionStatus
import com.projectfox.foxoff.ui.theme.FoxElectricBlue

/**
 * Dashboard principal permanent — écran vu à CHAQUE ouverture de l'app une
 * fois l'onboarding terminé (pas confondre avec l'onboarding lui-même,
 * jamais modifié ici). Révisé le 2026-08-14 sur un brief détaillé de
 * l'utilisateur : le renard doit rester l'élément central, mais l'écran
 * doit maintenant aussi donner en un coup d'œil l'état réel de la
 * surveillance, de la montre, de la TV et du système — contrairement à la
 * version précédente (volontairement épurée, sans aucune info montre/TV)
 * qui répondait à un brief antérieur, plus ancien et donc remplacé par
 * celui-ci.
 *
 * Logique fonctionnelle (permissions Bluetooth/notifications, démarrage
 * réel du service) intégralement conservée de la version précédente — le
 * bouton principal agit réellement sur BackgroundServiceSettings +
 * FoxForegroundService, ce n'est pas un bouton décoratif.
 */
@Composable
fun FoxHomeHero(
    surveillanceActive: Boolean,
    watchConnected: Boolean,
    watchKnown: Boolean,
    watchName: String,
    tvName: String,
    tvStatus: TvConnectionStatus?,
    sleepState: SleepState,
    confidence: Int,
    modifier: Modifier = Modifier,
    onOpenTv: () -> Unit = {},
    onOpenWatch: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    var notificationDenied by remember { mutableStateOf(false) }
    var bluetoothDenied by remember { mutableStateOf(false) }

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

    // Corrige un vrai crash constaté le 2026-08-14 sur émulateur :
    // Context.startForegroundService() engage Android à ce que le service
    // appelle Service.startForeground() sous quelques secondes.
    // FoxForegroundService.prerequisitesMet() vérifie déjà BLUETOOTH_CONNECT
    // en plus de la visibilité de la notification — mais si cette
    // permission manque au moment de l'appel, le service s'arrête via
    // failAndStop() SANS avoir appelé startForeground(), et Android tue
    // alors TOUTE l'application (ForegroundServiceDidNotStartInTimeException),
    // pas juste ce service. Vérification AVANT startForegroundService(),
    // jamais après — même correctif appliqué à
    // SettingsScreen.enableBackgroundSurveillance(), qui avait exactement
    // la même faille.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startIfAllowed()
        } else {
            BackgroundServiceSettings.setEnabled(context, false)
            notificationDenied = true
        }
    }

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

    fun onToggleClick() {
        if (surveillanceActive) {
            BackgroundServiceSettings.setEnabled(context, false)
            BackgroundServiceDisableReason.set(null)
            notificationDenied = false
            bluetoothDenied = false
            stopService()
        } else if (!hasBluetoothPermission()) {
            bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else if (!hasNotificationPermission()) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startIfAllowed()
        }
    }

    // État RÉEL du service (distinct de l'intention persistée
    // surveillanceActive — voir FoxForegroundServiceState) : permet de
    // distinguer "activé mais pas encore démarré/en erreur" de "actif et
    // opérationnel", demandé explicitement (carte Système, États 5/6).
    val realStatus by FoxForegroundServiceState.status.collectAsState()

    // Accès notifications (pause média téléphone) — même vérification que
    // SettingsScreen : état système, pas de StateFlow dédié, revérifié à
    // chaque retour au premier plan.
    var notificationsAccessGranted by remember {
        mutableStateOf(PhoneMediaPauseController.hasNotificationAccess(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAccessGranted = PhoneMediaPauseController.hasNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tvConnected = tvStatus == TvConnectionStatus.CONNECTED

    // État global dérivé par priorité — pilote couleur/illustration/
    // messages du haut de l'écran. Les mini-cartes ci-dessous affichent
    // toujours l'état réel de chaque brique individuellement, cette
    // priorité ne sert qu'au message d'ensemble ("quel est l'état
    // général", demande explicite du brief).
    val systemState = when {
        !surveillanceActive -> FoxSystemState.PAUSED
        !watchConnected -> FoxSystemState.WATCH_DISCONNECTED
        !tvConnected -> FoxSystemState.TV_DISCONNECTED
        realStatus != FoxForegroundServiceStatus.RUNNING -> FoxSystemState.ATTENTION
        else -> FoxSystemState.ACTIVE_OK
    }

    val accentColor = when (systemState) {
        FoxSystemState.PAUSED -> FoxHomeOrange
        FoxSystemState.WATCH_DISCONNECTED, FoxSystemState.TV_DISCONNECTED -> FoxHomeRed
        FoxSystemState.ATTENTION -> FoxHomeOrange
        FoxSystemState.ACTIVE_OK -> FoxElectricBlue
    }

    val (headline, description) = when (systemState) {
        FoxSystemState.PAUSED -> "Surveillance en pause" to
                "FoxOFF ne surveille pas votre sommeil pour l'instant."
        FoxSystemState.WATCH_DISCONNECTED -> "Montre déconnectée" to
                "FoxOFF ne peut pas détecter votre endormissement sans la montre."
        FoxSystemState.TV_DISCONNECTED -> "TV indisponible" to
                "Votre TV n'est plus disponible pour la mise en pause automatique."
        FoxSystemState.ATTENTION -> (
            if (realStatus == FoxForegroundServiceStatus.STARTING) "Démarrage en cours" else "Attention requise"
        ) to "Le service de surveillance rencontre un souci — vérifiez Paramètres."
        FoxSystemState.ACTIVE_OK -> "Surveillance active" to
                "FoxOFF veille sur votre sommeil et se chargera d'éteindre votre TV au bon moment."
    }

    val reassuranceMessage = when (systemState) {
        FoxSystemState.PAUSED -> "Activez la surveillance quand vous êtes prêt à dormir."
        FoxSystemState.WATCH_DISCONNECTED -> "Reconnectez votre montre pour que FoxOFF puisse veiller sur vous."
        FoxSystemState.TV_DISCONNECTED -> "Vérifiez que votre TV est allumée et sur le même réseau."
        FoxSystemState.ATTENTION -> "FoxOFF tente de se remettre en route automatiquement."
        FoxSystemState.ACTIVE_OK -> "Profitez de votre soirée, FoxOFF s'occupe du reste."
    }

    // Fond "renard sur la lune" (bg_home_night/bg_home_active selon
    // surveillanceActive) désormais dessiné par DashboardScreen, en dehors
    // de ce composable — pour occuper l'écran en entier, y compris sous la
    // barre de statut et la zone de gestes système, demande explicite du
    // 2026-08-14 ("le fond d'écran doit maintenant prendre la totalité de
    // l'écran"), ce que la zone de contenu du Scaffold ne permettait pas.

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HEADER — logo + statut bref + accès rapide aux Paramètres,
        // demandé explicitement ("bouton menu / paramètres" dans le header).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                com.projectfox.foxoff.ui.onboarding.components.FoxWordmark(fontSize = 24.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoxPulsingDot(color = accentColor, size = 8.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Paramètres", tint = Color.White.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ZONE PRINCIPALE — le renard est maintenant celui du fond
        // (bg_home_night.png, fixe), pas une illustration séparée : cet
        // espace réservé laisse simplement le renard du fond respirer,
        // sans rien superposer par-dessus.
        Spacer(modifier = Modifier.height(256.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )

        if (notificationDenied) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permission de notification refusée : la surveillance ne peut pas démarrer sans notification visible.",
                style = MaterialTheme.typography.labelSmall,
                color = FoxHomeRed,
                textAlign = TextAlign.Center
            )
        }

        if (bluetoothDenied) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permission Bluetooth refusée : nécessaire pour communiquer avec la montre pendant la surveillance.",
                style = MaterialTheme.typography.labelSmall,
                color = FoxHomeRed,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ÉTAT DU SYSTÈME — grille 2×2 compacte (pas les grandes
        // PremiumDashboardCard utilisées ailleurs, trop hautes pour 4
        // cartes sur un même écran sans scroll excessif). Chaque carte
        // montre l'état RÉEL, indépendamment de systemState ci-dessus.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniStatCard(
                modifier = Modifier.weight(1f),
                icon = "💤",
                title = "SOMMEIL",
                statusText = if (surveillanceActive) "Active" else "En pause",
                statusColor = if (surveillanceActive) FoxElectricBlue else Color.Gray,
                detail = if (surveillanceActive) "Confiance : $confidence %" else null
            )
            MiniStatCard(
                modifier = Modifier.weight(1f),
                icon = "📺",
                title = "TV",
                statusText = if (tvConnected) "Connectée" else "Indisponible",
                statusColor = if (tvConnected) FoxHomeGreen else FoxHomeRed,
                detail = tvName,
                onClick = onOpenTv
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniStatCard(
                modifier = Modifier.weight(1f),
                icon = "⌚",
                title = "MONTRE",
                statusText = when {
                    watchConnected -> "Connectée"
                    watchKnown -> "Hors ligne"
                    else -> "Non associée"
                },
                statusColor = when {
                    watchConnected -> FoxHomeGreen
                    watchKnown -> FoxHomeOrange
                    else -> FoxHomeRed
                },
                detail = watchName,
                onClick = onOpenWatch
            )
            MiniStatCard(
                modifier = Modifier.weight(1f),
                icon = "⚡",
                title = "SYSTÈME",
                statusText = when {
                    realStatus == FoxForegroundServiceStatus.ERROR -> "Attention requise"
                    realStatus == FoxForegroundServiceStatus.STARTING -> "Démarrage..."
                    realStatus == FoxForegroundServiceStatus.RUNNING -> "Normal"
                    else -> "En pause"
                },
                statusColor = when (realStatus) {
                    FoxForegroundServiceStatus.ERROR -> FoxHomeRed
                    FoxForegroundServiceStatus.STARTING -> FoxHomeOrange
                    FoxForegroundServiceStatus.RUNNING -> FoxHomeGreen
                    FoxForegroundServiceStatus.STOPPED -> Color.Gray
                },
                detail = if (notificationsAccessGranted) "Notifications activées" else "Notifications désactivées",
                onClick = onOpenSettings
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // MESSAGE RASSURANT — petit renard + phrase qui change selon
        // systemState, demandé explicitement, distinct du message
        // principal sous l'illustration.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🦊", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = reassuranceMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // BOUTON PRINCIPAL — libellé texte réel (pas une icône seule),
        // demandé explicitement : "Mettre en pause" / "Reprendre la
        // surveillance". Agit réellement sur BackgroundServiceSettings +
        // FoxForegroundService via onToggleClick(), pas un bouton
        // décoratif.
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(accentColor)
                .clickable { onToggleClick() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (surveillanceActive) "Mettre en pause" else "Reprendre la surveillance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * États dérivés (priorité) pour le message/illustration/couleur d'ensemble
 * du haut de l'écran — voir le brief : "Ne fais surtout pas une simple
 * maquette avec des textes codés en dur [...] prévoir au minimum ces
 * états". Les mini-cartes affichent en parallèle l'état réel de chaque
 * brique, indépendamment de cette priorité.
 */
private enum class FoxSystemState {
    PAUSED,
    WATCH_DISCONNECTED,
    TV_DISCONNECTED,
    ATTENTION,
    ACTIVE_OK
}

/**
 * Carte d'état compacte pour la grille 2×2 (Sommeil/TV/Montre/Système) —
 * distincte de PremiumDashboardCard (DashboardComponents.kt), pensée pour
 * tenir 4 par écran sans scroll excessif ni texte tronqué.
 */
@Composable
private fun MiniStatCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    statusText: String,
    statusColor: Color,
    detail: String? = null,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (detail != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FoxPulsingDot(color: Color, size: androidx.compose.ui.unit.Dp = 12.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

// Palette réutilisée telle quelle depuis le reste de l'app (DeviceCard,
// TvDashboardCard, SettingsScreen...) plutôt que d'introduire de nouvelles
// teintes.
private val FoxHomeOrange = Color(0xFFFFA000)
private val FoxHomeRed = Color(0xFFFF5252)
private val FoxHomeGreen = Color(0xFF4CAF50)
