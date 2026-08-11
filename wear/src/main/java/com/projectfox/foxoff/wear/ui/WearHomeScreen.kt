package com.projectfox.foxoff.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.projectfox.foxoff.wear.BuildConfig
import com.projectfox.foxoff.wear.core.FoxWearApplication
import com.projectfox.foxoff.wear.core.PassiveEngineDiagnostic
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WearHomeScreen(isPermissionGranted: Boolean) {
    val core = FoxWearApplication.core

    LaunchedEffect(isPermissionGranted) {
        com.projectfox.foxoff.wear.core.FoxWearLogger.i("FOX-WEAR | UI | WearHomeScreen affiché. isPermissionGranted=$isPermissionGranted")
    }
    val status by core.status.collectAsState(initial = "READY")
    val isMonitoring by core.isMonitoring.collectAsState(initial = false)
    val sample by core.heartRateSamples.collectAsState(initial = null)
    val connectionState by core.connectionState.collectAsState(initial = com.projectfox.foxoff.wear.communication.model.ConnectionState.DISCONNECTED)
    val batteryPercent by core.batteryPercent.collectAsState(initial = null)
    val passiveDiagnostic by core.passiveDiagnostic.collectAsState(initial = PassiveEngineDiagnostic())

    // Même valeur réellement envoyée au téléphone (voir FoxWearCore.sendDeviceInfo) —
    // "--%" tant que la première lecture n'est pas encore arrivée.
    val battery = batteryPercent?.let { "$it%" } ?: "--%"
    val version = BuildConfig.VERSION_NAME

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!isPermissionGranted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Permission Required",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            @Suppress("DEPRECATION")
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp)
            ) {
                item {
                    Text(
                        text = "🦊 FoxOFF",
                        color = Color.Yellow,
                        fontSize = 18.sp
                    )
                }
                
                item {
                    StatusItem(label = "Status", value = status)
                }
                
                item {
                    StatusItem(label = "Monitoring", value = if (isMonitoring) "ON" else "OFF")
                }
                
                item {
                    StatusItem(label = "Backend", value = "AUTO")
                }
                
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Heart Rate",
                            style = MaterialTheme.typography.caption2
                        )
                        Text(
                            text = sample?.bpm?.toInt()?.toString() ?: "--",
                            style = MaterialTheme.typography.display3,
                            color = if (isMonitoring) Color.Red else Color.Gray
                        )
                    }
                }
                
                item {
                    StatusItem(label = "Connection", value = connectionState.name)
                }
                
                item {
                    StatusItem(label = "Battery", value = battery)
                }
                
                item {
                    Text(
                        text = "v$version",
                        style = MaterialTheme.typography.caption3,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Diagnostic TEMPORAIRE du moteur passif (BPM écran éteint) —
                // à retirer une fois ce comportement validé sur matériel réel.
                item {
                    Text(
                        text = "— Diagnostic passif —",
                        style = MaterialTheme.typography.caption3,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                item {
                    StatusItem(
                        label = "Supporté",
                        value = when (passiveDiagnostic.supported) {
                            true -> "Oui"
                            false -> "Non"
                            null -> "?"
                        }
                    )
                }
                item {
                    StatusItem(label = "Démarré", value = if (passiveDiagnostic.started) "Oui" else "Non")
                }
                item {
                    StatusItem(
                        label = "Dernier point",
                        value = passiveDiagnostic.lastSampleAt?.let {
                            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(it)
                        } ?: "jamais"
                    )
                }
                if (passiveDiagnostic.lastError != null) {
                    item {
                        Text(
                            text = passiveDiagnostic.lastError ?: "",
                            style = MaterialTheme.typography.caption3,
                            color = Color.Red,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Note PERMANENTE (contrairement au diagnostic ci-dessus) : sur
                // certaines montres Samsung, des réglages système propres à
                // One UI Watch peuvent couper la remontée du BPM écran éteint
                // même quand le canal passif est correctement démarré côté
                // app — confirmé sur matériel réel (Galaxy Watch8). FoxOFF ne
                // peut pas modifier ces réglages à la place de l'utilisateur.
                item {
                    Text(
                        text = "Si le BPM s'arrête écran éteint (Samsung) :",
                        style = MaterialTheme.typography.caption3,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp)
                    )
                }
                item {
                    Text(
                        text = "1. Batterie → Veille : retirer FoxOFF Watch\n" +
                                "2. App FoxOFF Watch → désactiver « Suspendre l'activité si inutilisée »\n" +
                                "3. App FoxOFF Watch → Accès autorisé : « Tout le temps »",
                        style = MaterialTheme.typography.caption3,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusItem(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label : ",
            style = MaterialTheme.typography.caption1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.primary
        )
    }
}
