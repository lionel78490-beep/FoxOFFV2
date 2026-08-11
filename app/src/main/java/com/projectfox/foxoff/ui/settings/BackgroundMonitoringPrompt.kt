package com.projectfox.foxoff.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.projectfox.foxoff.core.application.BackgroundServiceSettings
import com.projectfox.foxoff.core.service.FoxForegroundService
import com.projectfox.foxoff.ui.theme.FoxBlackSurface

/**
 * Contenu partagé de la proposition "Surveillance en arrière-plan" —
 * réutilisé tel quel comme section intégrée dans l'écran Permissions de
 * l'onboarding (nouveaux utilisateurs) et comme contenu d'une boîte de
 * dialogue ponctuelle sur le Dashboard (utilisateurs existants). Toute la
 * logique de permission/persistance vit ici, une seule fois.
 *
 * Ne prétend jamais que le service tourne réellement : ce choix
 * n'enregistre que l'INTENTION de l'utilisateur (BackgroundServiceSettings).
 * Le service de premier plan lui-même est construit dans une sous-étape
 * ultérieure de la tâche #13.
 */
@Composable
fun BackgroundMonitoringSection(
    modifier: Modifier = Modifier,
    onChoiceMade: () -> Unit = {}
) {
    val context = LocalContext.current
    var deniedMessage by remember { mutableStateOf(false) }
    var choiceMade by remember { mutableStateOf<Boolean?>(null) } // null = pas encore choisi, true = activée, false = plus tard/refusée

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        BackgroundServiceSettings.setEnabled(context, granted)
        BackgroundServiceSettings.markPromptSeen(context)
        deniedMessage = !granted
        choiceMade = granted
        if (granted) {
            ContextCompat.startForegroundService(context, Intent(context, FoxForegroundService::class.java))
        }
        onChoiceMade()
    }

    fun activate() {
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (notificationsGranted) {
            BackgroundServiceSettings.setEnabled(context, true)
            BackgroundServiceSettings.markPromptSeen(context)
            deniedMessage = false
            choiceMade = true
            ContextCompat.startForegroundService(context, Intent(context, FoxForegroundService::class.java))
            onChoiceMade()
        } else {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun later() {
        BackgroundServiceSettings.setEnabled(context, false)
        BackgroundServiceSettings.markPromptSeen(context)
        deniedMessage = false
        choiceMade = false
        onChoiceMade()
    }

    Column(modifier = modifier) {
        Text(
            text = "Surveillance en arrière-plan",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Permet à FoxOFF de recevoir les informations de votre montre lorsque " +
                    "l'application n'est pas ouverte. Une notification discrète restera " +
                    "visible pendant la surveillance.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )

        if (deniedMessage) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Autorisation de notification refusée : la surveillance restera " +
                        "désactivée. Vous pourrez l'activer plus tard dans Réglages.",
                color = Color(0xFFFF5252),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (choiceMade) {
            null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { activate() }) {
                        Text("Activer la surveillance")
                    }
                    TextButton(onClick = { later() }) {
                        Text("Plus tard", color = Color.Gray)
                    }
                }
            }
            true -> {
                Text(
                    text = "✓ Surveillance activée",
                    color = Color.Green,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            false -> {
                Text(
                    text = "Non activée pour l'instant — modifiable dans Réglages.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Variante informationnelle utilisée dans l'écran Permissions de
 * l'onboarding : n'a PAS son propre bouton "Activer" — l'activation se
 * fait automatiquement à la suite de la demande Bluetooth/Wi-Fi (séquence
 * guidée pilotée par PermissionScreen, voir resolveBackgroundStep). Ne
 * garde que l'explication, le message de refus éventuel, et l'action
 * "Continuer sans surveillance" pour décliner par avance.
 */
@Composable
fun BackgroundMonitoringInfoSection(
    modifier: Modifier = Modifier,
    resolvedChoice: Boolean?,
    deniedMessage: Boolean,
    onDeclineNow: () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = "Surveillance en arrière-plan",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "FoxOFF demandera successivement l'accès aux appareils à proximité, " +
                    "puis l'autorisation d'afficher la notification nécessaire à la " +
                    "surveillance.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )

        if (deniedMessage) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Autorisation de notification refusée : la surveillance restera " +
                        "désactivée. Vous pourrez l'activer plus tard dans Réglages.",
                color = Color(0xFFFF5252),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (resolvedChoice) {
            null -> {
                TextButton(onClick = onDeclineNow, contentPadding = PaddingValues(0.dp)) {
                    Text("Continuer sans surveillance", color = Color.Gray)
                }
            }
            true -> {
                Text(
                    text = "✓ Surveillance activée",
                    color = Color.Green,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            false -> {
                Text(
                    text = "Non activée pour l'instant — modifiable dans Réglages.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Boîte de dialogue ponctuelle pour les utilisateurs ayant déjà terminé
 * l'onboarding (voir DashboardScreen). Se ferme automatiquement dès qu'un
 * choix explicite est fait.
 */
@Composable
fun BackgroundMonitoringPromptDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = FoxBlackSurface,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                BackgroundMonitoringSection(onChoiceMade = onDismiss)
            }
        }
    }
}
