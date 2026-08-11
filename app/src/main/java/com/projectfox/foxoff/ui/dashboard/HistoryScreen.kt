package com.projectfox.foxoff.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfox.foxoff.core.automation.NightLog
import com.projectfox.foxoff.core.automation.NightLogEntry
import com.projectfox.foxoff.core.automation.NightLogType
import com.projectfox.foxoff.ui.dashboard.components.PremiumDashboardCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Journal chronologique de la nuit (voir NightLog) — pensé pour être
 * envoyé tel quel (bouton "Copier") le lendemain matin plutôt que de devoir
 * décrire ce qui s'est passé de mémoire.
 */
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val entries by NightLog.entries.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "HISTORIQUE",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 24.dp)
        )

        Text(
            text = "Journal de tout ce que FoxOFF a détecté et fait — utile pour " +
                    "revoir une nuit de test. \"Copier\" met tout le texte dans le " +
                    "presse-papier, prêt à coller dans une conversation.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { copyToClipboard(context, entries) }, enabled = entries.isNotEmpty()) {
                Text("Copier")
            }
            OutlinedButton(onClick = { showClearConfirm = true }, enabled = entries.isNotEmpty()) {
                Text("Effacer")
            }
        }

        if (entries.isEmpty()) {
            PremiumDashboardCard {
                Text(
                    text = "Aucun événement pour l'instant.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(entries.asReversed()) { entry ->
                    HistoryEntryRow(entry, formatter)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Repart de zéro — utile avant une nouvelle nuit de test.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    NightLog.clear(context)
                }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun HistoryEntryRow(entry: NightLogEntry, formatter: DateTimeFormatter) {
    val (label, color) = typeLabelAndColor(entry.type)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text(text = entry.detail, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = formatter.format(entry.timestamp),
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun typeLabelAndColor(type: NightLogType): Pair<String, Color> = when (type) {
    NightLogType.SLEEP_STATE_CHANGE -> "Analyse" to Color(0xFF64B5F6)
    NightLogType.MOVEMENT -> "Mouvement" to Color(0xFFBA68C8)
    NightLogType.TV_ON -> "TV allumée" to Color.Green
    NightLogType.TV_OFF -> "TV éteinte" to Color.Gray
    NightLogType.PAUSE_COUNTDOWN_STARTED -> "Compte à rebours démarré" to Color(0xFFFFA000)
    NightLogType.PAUSE_EXECUTED -> "Pause exécutée" to Color.Green
    NightLogType.PAUSE_CANCELLED -> "Pause annulée" to Color(0xFFFF5252)
    NightLogType.WATCH_CONNECTED -> "Montre connectée" to Color.Green
    NightLogType.WATCH_DISCONNECTED -> "Montre déconnectée" to Color(0xFFFF5252)
    NightLogType.HEART_RATE_TREND -> "BPM" to Color(0xFF4DD0E1)
}

private fun copyToClipboard(context: Context, entries: List<NightLogEntry>) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
    val text = entries.joinToString("\n") { entry ->
        "${formatter.format(entry.timestamp)} | ${entry.type} | ${entry.detail}"
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Historique FoxOFF", text))
}
