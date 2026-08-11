package com.projectfox.foxoff.ui.dashboard.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfox.foxoff.brain.SleepState
import com.projectfox.foxoff.core.application.ActiveHoursSlot
import com.projectfox.foxoff.tv.TvConnectionStatus
import com.projectfox.foxoff.ui.theme.FoxBlackSurface
import com.projectfox.foxoff.ui.theme.FoxGradient
import com.projectfox.foxoff.ui.theme.FoxHeartRed
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun PremiumDashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(FoxBlackSurface.copy(alpha = 0.8f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun MainStatusCard(status: String, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    PremiumDashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun PremiumHeartRateCard(
    bpm: Int?,
    min: Int?,
    max: Int?,
    time: LocalTime?
) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    val infiniteTransition = rememberInfiniteTransition(label = "heart")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    PremiumDashboardCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Rythme Cardiaque",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "❤️",
                        fontSize = 24.sp,
                        modifier = Modifier
                            .padding(bottom = 8.dp, end = 8.dp)
                            .scale(if (bpm != null) heartScale else 1f)
                    )
                    Text(
                        text = bpm?.toString() ?: "--",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = if (bpm != null) FoxHeartRed else Color.DarkGray,
                        lineHeight = 64.sp
                    )
                    Text(
                        text = " BPM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
            
            if (bpm == null) {
                Text(
                    text = "En attente...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        if (bpm != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color.White.copy(alpha = 0.05f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusInfoItem("Min", min?.toString() ?: "--")
                StatusInfoItem("Max", max?.toString() ?: "--")
                StatusInfoItem("Dernier", time?.format(formatter) ?: "--:--:--")
            }
        }
    }
}

@Composable
fun DeviceCard(
    icon: String,
    name: String,
    isConnected: Boolean,
    isKnown: Boolean = false,
    battery: String,
    type: String,
    // Remplace le libellé de statut calculé (Connectée/Hors ligne/Déconnectée)
    // quand une sous-phase est en cours (Vérification, Reconnexion) — voir
    // WatchConnectionState. null en fonctionnement normal.
    statusOverride: String? = null,
    onAssociateClick: () -> Unit = {}
) {
    PremiumDashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isConnected -> Color.Green
                                    isKnown -> Color(0xFFFFA000)
                                    else -> Color.Red
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusOverride ?: when {
                            isConnected -> "Connectée • $battery"
                            isKnown -> "Hors ligne"
                            else -> "Déconnectée"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            if (!isConnected) {
                Button(
                    onClick = onAssociateClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(if (isKnown) "Reconnecter" else "Associer", fontSize = 12.sp, color = Color.White)
                }
            } else {
                Text(text = type, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun TvDashboardCard(
    name: String,
    status: TvConnectionStatus?,
    currentApp: String,
    lastCommand: String,
    lastCommandTime: LocalTime?
) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val isConnected = status == TvConnectionStatus.CONNECTED
    val hasKnownDevice = status != null && status != TvConnectionStatus.DISCONNECTED
    val displayName = if (hasKnownDevice) name else "Aucune TV associée"
    // currentApp n'est jamais réellement renseigné par FoxTvEngine (aucun
    // chemin ne met à jour TvDevice.currentApp après connexion) : ne jamais
    // l'afficher en suffixe, ça produirait "Connectée • Aucune".
    val (statusLabel, statusColor) = when (status) {
        TvConnectionStatus.CONNECTED -> "Connectée" to Color.Green
        TvConnectionStatus.OFFLINE, TvConnectionStatus.ERROR -> "Hors ligne" to Color(0xFFFFA000)
        TvConnectionStatus.CONNECTING,
        TvConnectionStatus.SEARCHING -> "Connexion..." to Color.Yellow
        TvConnectionStatus.PAIRING_REQUIRED,
        TvConnectionStatus.PAIRING_SENT -> "Appairage requis" to Color(0xFFFF5252)
        TvConnectionStatus.IDENTITY_MISMATCH -> "Identité différente" to Color(0xFFFF5252)
        TvConnectionStatus.CONFIRMATION_REQUIRED -> "Vérification requise" to Color(0xFFFFA000)
        else -> null to Color.Gray
    }

    PremiumDashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "📺", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(text = displayName, fontWeight = FontWeight.Bold, color = Color.White)
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }
        }

        if (isConnected && lastCommand != "Aucune") {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dernière commande : $lastCommand (${lastCommandTime?.format(formatter) ?: "--:--"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun FoxAnalysisCard(
    state: SleepState,
    confidence: Int,
    explanation: String
) {
    PremiumDashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(FoxGradient)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🦊", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Analyse FoxOFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = when(state) {
                        SleepState.AWAKE -> "Vous êtes éveillé"
                        SleepState.DROWSY -> "Somnolence détectée"
                        SleepState.PRE_SLEEP -> "Pré-endormissement"
                        SleepState.ASLEEP -> "Endormi"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(
                progress = { confidence / 100f },
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                color = if (confidence > 80) Color.Green else Color.Yellow,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            lineHeight = 20.sp
        )
    }
}

/**
 * Créneau(x) horaire(s) dans lesquels la surveillance a le droit de tourner
 * (voir ActiveHoursSettings) — affiché sur l'Accueil pour que ce soit
 * visible en un coup d'œil, sans devoir aller dans Réglages. Ensemble vide
 * = aucune restriction configurée (comportement historique, surveillance
 * possible toute la journée). Cliquable : renvoie directement vers la
 * sélection des créneaux (onglet Réglages, voir DashboardScreen).
 */
@Composable
fun ActiveHoursInfoCard(selectedSlots: Set<ActiveHoursSlot>, onClick: () -> Unit = {}) {
    PremiumDashboardCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🕐", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Créneaux actifs",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = if (selectedSlots.isEmpty()) {
                        "Aucune restriction (toute la journée)"
                    } else {
                        selectedSlots.sortedBy { it.startHour }.joinToString(" • ") { it.label }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(text = "›", fontSize = 22.sp, color = Color.Gray)
        }
    }
}

@Composable
fun StatusInfoItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
