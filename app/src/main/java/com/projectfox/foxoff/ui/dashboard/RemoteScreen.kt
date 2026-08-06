package com.projectfox.foxoff.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.tv.TvConnectionStatus
import com.projectfox.foxoff.tv.TvDevice
import com.projectfox.foxoff.ui.onboarding.components.FoxButton
import com.projectfox.foxoff.ui.onboarding.components.FoxCard
import com.projectfox.foxoff.ui.onboarding.components.FoxGradientBackground

@Composable
fun RemoteScreen() {
    val tvEngine = FoxCore.tvEngine
    val tvState by tvEngine?.state?.collectAsState() ?: remember { mutableStateOf(null) }
    val discoveredDevices by tvEngine?.discoveredDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    var pinValue by remember { mutableStateOf("") }

    LaunchedEffect(discoveredDevices.size) {
        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | UI | RemoteScreen reçu : ${discoveredDevices.size} appareils")
    }

    FoxGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SCANNER RÉSEAU",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (tvState == null || tvState?.status == TvConnectionStatus.DISCONNECTED || tvState?.status == TvConnectionStatus.SEARCHING) {
                // Discovery List
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Appareils trouvés (${discoveredDevices.size})", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    if (discoveredDevices.isNotEmpty()) {
                         TextButton(onClick = { tvEngine?.initialize() }) {
                             Text("Relancer le scan", fontSize = 10.sp)
                         }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    discoveredDevices.forEach { device ->
                        TvDiscoveryCard(device) {
                            tvEngine?.selectDevice(device)
                        }
                    }
                    
                    if (discoveredDevices.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Analyse en cours...", color = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                // Connection / Control UI
                FoxCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = tvState?.name ?: "", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = "${tvState?.address} • ${tvState?.manufacturer ?: "Inconnu"}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text(text = "Ports: ${tvState?.openPorts?.joinToString(", ") ?: "Aucun"}", color = Color.Green, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { tvEngine?.connect(tvState!!) }) {
                            Icon(Icons.Default.Refresh, null, tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (tvState?.status) {
                    TvConnectionStatus.PAIRING_REQUIRED -> {
                        FoxButton(text = "Démarrer l'appairage", onClick = { tvEngine?.startPairing(tvState!!) })
                        TextButton(onClick = { /* Back to list logic if needed */ }) {
                            Text("Retour à la liste", color = Color.Gray)
                        }
                    }
                    TvConnectionStatus.PAIRING_SENT -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Saisissez le code affiché sur la TV", color = Color.White)
                            OutlinedTextField(
                                value = pinValue,
                                onValueChange = { if (it.length <= 4) pinValue = it },
                                label = { Text("Code PIN") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White
                                )
                            )
                            FoxButton(text = "Valider le code", onClick = { tvEngine?.submitPin(pinValue) })
                        }
                    }
                    TvConnectionStatus.CONNECTED -> {
                        RemoteControls(tvEngine!!)
                    }
                    else -> {
                        CircularProgressIndicator(color = Color.White)
                        Text("Action : ${tvState?.status}", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun TvDiscoveryCard(device: TvDevice, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (device.openPorts.contains(6466)) Color.Green.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = if (device.openPorts.contains(6466)) "🖥️" else "📺", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = device.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = "${device.address} • ${device.manufacturer ?: device.model ?: "Inconnu"}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    device.services.take(2).forEach { service ->
                         Text(text = service.split(".").firstOrNull() ?: "", color = Color.Cyan.copy(alpha = 0.7f), fontSize = 9.sp)
                    }
                    if (device.lastResponseTimeMs > 0) {
                         Text(text = "${device.lastResponseTimeMs}ms", color = Color.Gray, fontSize = 9.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (device.openPorts.contains(6466)) {
                 Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun RemoteControls(engine: com.projectfox.foxoff.tv.FoxTvEngine) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteRoundButton(Icons.Default.ArrowBack, "Back") { engine.back() }
            RemoteRoundButton(Icons.Default.Home, "Home") { engine.home() }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteRoundButton(Icons.Default.PlayArrow, "Play") { engine.play() }
            RemoteRoundButton(Icons.Default.Pause, "Pause") { engine.pause() }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { engine.volumeDown() }) { Icon(Icons.Default.VolumeDown, null, tint = Color.White) }
            Text("VOLUME", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            IconButton(onClick = { engine.volumeUp() }) { Icon(Icons.Default.VolumeUp, null, tint = Color.White) }
        }
    }
}

@Composable
fun RemoteRoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape).clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}
