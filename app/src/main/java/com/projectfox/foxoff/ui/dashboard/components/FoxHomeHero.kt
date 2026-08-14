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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.projectfox.foxoff.R
import com.projectfox.foxoff.core.application.BackgroundServiceSettings
import com.projectfox.foxoff.core.service.BackgroundServiceDisableReason
import com.projectfox.foxoff.core.service.FoxForegroundService
import com.projectfox.foxoff.ui.theme.FoxElectricBlue

/**
 * Centre de contrôle de l'Accueil — révisé le 2026-08-14 pour reproduire
 * fidèlement une maquette de référence fournie par l'utilisateur : header
 * centré ("Fox⏻FF" + tagline), illustration CONTENUE dans un cadre (plus
 * de fond plein écran), statut/description sous l'illustration, bouton
 * rond à contour lumineux (icône seule) avec légende séparée en dessous.
 * Demande explicite : aucune info montre/TV/plage horaire sur cet écran
 * (contredit une demande précédente du même jour — celle-ci, plus récente
 * et plus explicite, prime).
 *
 * Deux écarts assumés par rapport à la maquette, pour ne rien casser :
 * - Navigation du bas conservée telle quelle (5 onglets réels de l'app —
 *   Accueil/Santé/TV/Paramètres/Historique — pas les 4 de la maquette,
 *   qui feraient perdre l'accès à deux écrans fonctionnels).
 * - Le bouton "Reconnecter la montre" (DeviceCard à l'origine) n'a plus
 *   d'emplacement sur cet écran, la maquette n'ayant aucune info montre —
 *   la reconnexion automatique au démarrage du Dashboard reste active.
 *
 * Illustrations réutilisées telles quelles depuis le module montre
 * (`fox_watching_hero`/`fox_sleeping_hero`, copiées dans ce module — les
 * modules Android ne partagent pas leurs ressources) : même mascotte que
 * la montre et le Character Bible (`docs/FOX_CHARACTER_BIBLE.md`).
 */
@Composable
fun FoxHomeHero(
    surveillanceActive: Boolean,
    modifier: Modifier = Modifier
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

    val accentColor = if (surveillanceActive) FoxElectricBlue else FoxHomeOrange

    val breathPeriodMs = if (surveillanceActive) 2200 else 4200
    val breathAmplitude = if (surveillanceActive) 0.02f else 0.01f
    val infiniteTransition = rememberInfiniteTransition(label = "foxHeroBreath")
    val breath by infiniteTransition.animateFloat(
        initialValue = 1f - breathAmplitude,
        targetValue = 1f + breathAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(breathPeriodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "foxHeroBreathScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header centré — réutilise FoxWordmark (ui/onboarding/components/
        // FoxComponents.kt), le même composable "Fox⏻FF" que l'écran de
        // bienvenue (police Outfit, symbole power en relief dessiné à la
        // main) — harmonisé le 2026-08-14 pour un logo identique partout
        // dans l'app plutôt qu'une implémentation dupliquée ici avec
        // l'ancienne icône Material en anneau plat.
        com.projectfox.foxoff.ui.onboarding.components.FoxWordmark(fontSize = 30.sp)
        Text(
            text = "Votre sommeil, notre mission.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Illustration CONTENUE dans un cadre (pas de fond plein écran) —
        // l'élément visuel principal, mais pas la totalité de la page.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = if (surveillanceActive) R.drawable.fox_watching_hero else R.drawable.fox_sleeping_hero
                ),
                contentDescription = if (surveillanceActive) {
                    "Fox veille sur votre sommeil"
                } else {
                    "Fox se repose, surveillance inactive"
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = breath
                        scaleY = breath
                    }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FoxPulsingDot(color = accentColor)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (surveillanceActive) "Surveillance active" else "Surveillance inactive",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (surveillanceActive) {
                "FoxOFF surveille votre sommeil et se chargera d'éteindre votre TV au bon moment."
            } else {
                "FoxOFF ne surveille pas votre sommeil. Activez la surveillance pour protéger vos nuits."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        if (notificationDenied) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permission de notification refusée : la surveillance ne peut pas démarrer sans notification visible.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF5252),
                textAlign = TextAlign.Center
            )
        }

        if (bluetoothDenied) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permission Bluetooth refusée : nécessaire pour communiquer avec la montre pendant la surveillance.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF5252),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bouton rond à contour lumineux, icône seule — comme la maquette.
        // Le halo est un vrai Modifier.shadow() coloré (pas un dégradé
        // approximatif), sûr sur toutes les API du projet (minSdk 30).
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = accentColor,
                    spotColor = accentColor
                )
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .border(2.dp, accentColor, CircleShape)
                .clickable { onToggleClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = if (surveillanceActive) "Désactiver la surveillance" else "Activer la surveillance",
                tint = accentColor,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (surveillanceActive) "Appuyez pour désactiver" else "Appuyez pour activer",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun FoxPulsingDot(color: Color) {
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
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

// Amber déjà utilisé partout ailleurs dans l'app pour les états "attention/
// inactif" (DeviceCard, TvDashboardCard, SettingsScreen...) — réutilisé ici
// pour rester cohérent avec la palette existante plutôt que d'introduire un
// nouvel orange.
private val FoxHomeOrange = Color(0xFFFFA000)
