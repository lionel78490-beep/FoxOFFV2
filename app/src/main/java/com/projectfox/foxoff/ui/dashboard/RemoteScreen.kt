package com.projectfox.foxoff.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.tv.FoxTvEngine
import com.projectfox.foxoff.tv.TvConnectionStatus
import com.projectfox.foxoff.tv.TvDevice
import com.projectfox.foxoff.tv.TvSelectDeviceOutcome
import com.projectfox.foxoff.ui.onboarding.components.FoxButton
import com.projectfox.foxoff.ui.onboarding.components.FoxCard
import com.projectfox.foxoff.ui.onboarding.components.FoxGradientBackground

private sealed class TvScreenView {
    object List : TvScreenView()
    object AddDevice : TvScreenView()
    data class Detail(val deviceId: String) : TvScreenView()
}

@Composable
fun RemoteScreen() {
    val tvEngine = FoxCore.tvEngine
    val pairedDevices by tvEngine?.pairedDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val activeDeviceId by tvEngine?.activeDeviceId?.collectAsState() ?: remember { mutableStateOf(null) }
    val discoveredDevices by tvEngine?.discoveredDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val pendingConfirmation by tvEngine?.pendingConfirmation?.collectAsState() ?: remember { mutableStateOf(null) }
    val pendingNameMatch by tvEngine?.pendingNameMatch?.collectAsState() ?: remember { mutableStateOf(null) }

    var currentView by remember { mutableStateOf<TvScreenView>(TvScreenView.List) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    FoxGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            infoMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { infoMessage = null }) { Text("OK", color = Color.Gray) }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            when (val view = currentView) {
                TvScreenView.List -> TvListView(
                    devices = pairedDevices,
                    activeDeviceId = activeDeviceId,
                    onAddDevice = {
                        infoMessage = null
                        currentView = TvScreenView.AddDevice
                        tvEngine?.initialize()
                    },
                    onUseDevice = { id ->
                        tvEngine?.useDevice(id)
                        currentView = TvScreenView.Detail(id)
                    },
                    onRefreshActive = { tvEngine?.refreshActiveDevice() }
                )

                TvScreenView.AddDevice -> TvAddDeviceView(
                    discoveredDevices = discoveredDevices,
                    onBack = {
                        tvEngine?.stopDiscovery()
                        currentView = TvScreenView.List
                    },
                    onRescan = { tvEngine?.initialize() },
                    onDeviceClick = { device ->
                        when (val outcome = tvEngine?.selectDevice(device)) {
                            is TvSelectDeviceOutcome.NewPairingStarted -> {
                                currentView = TvScreenView.Detail(outcome.deviceId)
                            }
                            is TvSelectDeviceOutcome.AlreadyAssociated -> {
                                infoMessage = "« ${outcome.deviceName} » est déjà associée. " +
                                        "Utilisez « Utiliser cette TV » depuis la liste pour la reconnecter."
                            }
                            TvSelectDeviceOutcome.AmbiguousNameMatch, null -> Unit
                        }
                    }
                )

                is TvScreenView.Detail -> {
                    val device = pairedDevices.firstOrNull { it.id == view.deviceId }
                    TvDetailView(
                        device = device,
                        engine = tvEngine,
                        onBack = { currentView = TvScreenView.List }
                    )
                }
            }
        }
    }

    // Dialogues globaux : une vérification peut avoir été déclenchée depuis
    // l'écran de scan (correspondance par adresse/nom), donc affichés
    // indépendamment de l'écran actuellement visible.
    pendingConfirmation?.let { pending ->
        val deviceName = pairedDevices.firstOrNull { it.id == pending.deviceId }?.name ?: "cette TV"
        ConfirmIdentityDialog(
            deviceName = deviceName,
            onConfirm = { tvEngine?.confirmPendingIdentity(pending.deviceId) },
            onDismiss = { tvEngine?.dismissPendingIdentity(pending.deviceId) }
        )
    }

    pendingNameMatch?.let { pending ->
        ConfirmNameMatchDialog(
            storedName = pending.storedName,
            discoveredAddress = pending.discoveredAddress,
            onConfirm = { tvEngine?.confirmNameMatch() },
            onDismiss = { tvEngine?.dismissNameMatch() }
        )
    }
}

// ---------------------------------------------------------------------
// Écran principal : liste des TV associées
// ---------------------------------------------------------------------

@Composable
private fun TvListView(
    devices: List<TvDevice>,
    activeDeviceId: String?,
    onAddDevice: () -> Unit,
    onUseDevice: (String) -> Unit,
    onRefreshActive: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MES TV",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        FoxButton(text = "+ Ajouter une TV", onClick = onAddDevice)

        Spacer(modifier = Modifier.height(24.dp))

        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aucune TV associée", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Utilisez « Ajouter une TV » pour appairer votre premier appareil.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                devices.forEach { device ->
                    val isActive = device.id == activeDeviceId
                    TvPairedCard(
                        device = device,
                        isActive = isActive,
                        onUse = { onUseDevice(device.id) },
                        onRefresh = onRefreshActive
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPairedCard(
    device: TvDevice,
    isActive: Boolean,
    onUse: () -> Unit,
    onRefresh: () -> Unit
) {
    val (statusLabel, statusColor) = tvStatusLabelAndColor(device.status, isActive)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = device.name, color = Color.White, fontWeight = FontWeight.Bold)
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00E5A0).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "UTILISÉE",
                            color = Color(0xFF00E5A0),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(text = device.address, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text(text = statusLabel, color = statusColor, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isActive) {
                    OutlinedButton(onClick = onUse, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("Ouvrir", fontSize = 12.sp)
                    }
                    TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Actualiser", fontSize = 12.sp, color = Color.White)
                    }
                } else {
                    OutlinedButton(onClick = onUse, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("Utiliser cette TV", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Une TV non utilisée affiche TOUJOURS "Associée", quel que soit son
 * statut interne (une TV mémorisée ne se connecte jamais toute seule et ne
 * doit donc jamais apparaître "Connectée" tant qu'elle n'est pas utilisée).
 */
private fun tvStatusLabelAndColor(status: TvConnectionStatus, isActive: Boolean): Pair<String, Color> {
    if (!isActive) {
        return "Associée" to Color.Gray
    }
    return when (status) {
        TvConnectionStatus.CONNECTED -> "Utilisée • Connectée" to Color.Green
        TvConnectionStatus.OFFLINE, TvConnectionStatus.ERROR, TvConnectionStatus.DISCONNECTED -> "Utilisée • Hors ligne" to Color(0xFFFFA000)
        TvConnectionStatus.CONNECTING, TvConnectionStatus.SEARCHING -> "Utilisée • Connexion..." to Color.Yellow
        TvConnectionStatus.PAIRING_REQUIRED -> "Utilisée • Appairage requis" to Color(0xFFFF5252)
        TvConnectionStatus.PAIRING_SENT -> "Utilisée • En attente du code PIN" to Color.Yellow
        TvConnectionStatus.IDENTITY_MISMATCH -> "Utilisée • Identité différente" to Color(0xFFFF5252)
        TvConnectionStatus.CONFIRMATION_REQUIRED -> "Utilisée • Vérification requise" to Color(0xFFFFA000)
    }
}

// ---------------------------------------------------------------------
// Écran de scan : "Ajouter une TV"
// ---------------------------------------------------------------------

@Composable
private fun TvAddDeviceView(
    discoveredDevices: List<TvDevice>,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onDeviceClick: (TvDevice) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Color.White)
            }
            Text(
                text = "AJOUTER UNE TV",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Appareils trouvés (${discoveredDevices.size})", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            if (discoveredDevices.isNotEmpty()) {
                TextButton(onClick = onRescan) {
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
                TvDiscoveryCard(device) { onDeviceClick(device) }
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

// ---------------------------------------------------------------------
// Écran de détail : télécommande / appairage de la TV utilisée
// ---------------------------------------------------------------------

@Composable
private fun TvDetailView(
    device: TvDevice?,
    engine: FoxTvEngine?,
    onBack: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Color.White)
        }
        Text(
            text = device?.name ?: "TV",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Black
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (device == null) {
        Text("Cette TV n'est plus disponible.", color = Color.Gray)
        return
    }

    FoxCard {
        Column {
            Text(
                text = "${device.address} • ${device.manufacturer ?: "Inconnu"}",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            if (device.openPorts.isNotEmpty()) {
                Text(
                    text = "Ports: ${device.openPorts.joinToString(", ")}",
                    color = Color.Green,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    when (device.status) {
        TvConnectionStatus.PAIRING_REQUIRED -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Une TV déjà appairée par le passé (empreinte connue) qui
                // revient sur cet état l'a fait suite à un rejet TLS confirmé
                // — pas un simple clic dans la liste. Un nouvel appairage
                // remplacera ses informations : le rendre explicite ici.
                if (device.certificateFingerprint != null) {
                    Text(
                        "Cette TV ne reconnaît plus l'identité de FoxOFF.",
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Un nouvel appairage remplacera les informations actuelles de « ${device.name} ».",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                FoxButton(text = "Démarrer l'appairage", onClick = { engine?.startPairing(device) })
            }
        }

        TvConnectionStatus.PAIRING_SENT -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Saisissez le code affiché sur la TV", color = Color.White)
                Text(
                    text = "Code à 6 caractères (0-9, A-F)",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { input ->
                        pinValue = input
                            .uppercase()
                            .filter { it in '0'..'9' || it in 'A'..'F' }
                            .take(6)
                    },
                    label = { Text("Code PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )
                FoxButton(
                    text = "Valider le code",
                    onClick = { engine?.submitPin(pinValue, device.id) },
                    enabled = pinValue.length == 6
                )
            }
        }

        TvConnectionStatus.CONNECTED -> {
            RemoteControls(engine!!)
        }

        TvConnectionStatus.CONNECTING -> {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Connexion...", color = Color.Gray)
        }

        TvConnectionStatus.OFFLINE -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hors ligne", color = Color(0xFFFFA000), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "TV utilisée, actuellement injoignable sur le réseau.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                FoxButton(text = "Réessayer", onClick = { engine?.refreshActiveDevice() })
            }
        }

        TvConnectionStatus.IDENTITY_MISMATCH -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Identité différente", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Un autre appareil répond à cette adresse. Rien n'a été modifié.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                FoxButton(text = "Nouvel appairage", onClick = { engine?.startPairing(device) })
                TextButton(onClick = { engine?.refreshActiveDevice() }) {
                    Text("Réessayer", color = Color.Gray)
                }
            }
        }

        TvConnectionStatus.CONFIRMATION_REQUIRED -> {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Vérification requise", color = Color(0xFFFFA000), fontWeight = FontWeight.Bold)
            Text(
                "Confirmez l'identité de cette TV dans la fenêtre affichée.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }

        else -> {
            CircularProgressIndicator(color = Color.White)
            Text("Action : ${device.status}", color = Color.Gray)
        }
    }
}

// ---------------------------------------------------------------------
// Dialogues de confirmation d'identité
// ---------------------------------------------------------------------

@Composable
private fun ConfirmIdentityDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vérification requise") },
        text = {
            Text(
                "FoxOFF s'est connecté à « $deviceName », mais n'a encore jamais confirmé son identité " +
                        "(TV associée avant cette fonctionnalité). Confirmez-vous qu'il s'agit bien de cette TV ?"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Oui, c'est ma TV") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ConfirmNameMatchDialog(
    storedName: String,
    discoveredAddress: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Est-ce la même TV ?") },
        text = {
            Text(
                "Un appareil nommé « $storedName » a été détecté à une nouvelle adresse " +
                        "($discoveredAddress). S'agit-il de la même TV que celle déjà associée ? " +
                        "Une tentative de connexion sera faite pour le vérifier avant toute mise à jour " +
                        "(cette TV ne devient pas utilisée pour autant)."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Oui, essayer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Non, ignorer") }
        }
    )
}

// ---------------------------------------------------------------------
// Télécommande Play/Pause
// ---------------------------------------------------------------------

@Composable
fun RemoteControls(engine: FoxTvEngine) {
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
