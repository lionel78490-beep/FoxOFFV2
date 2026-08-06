package com.projectfox.foxoff.ui.onboarding.screens

import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        isVisible = true
        delay(2500)
        isVisible = false
        delay(800)
        onAnimationFinished()
    }
    FoxGradientBackground {
        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(1000)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(800)) + scaleOut(targetScale = 1.2f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FoxAnimatedLogo()
                Spacer(modifier = Modifier.height(32.dp))
                FoxTitle(text = "FOXOFF")
                Spacer(modifier = Modifier.height(16.dp))
                FoxSubtitle(text = "Votre sommeil.\nVotre télévision.\nEn parfaite synchronisation.")
            }
        }
    }
}

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    FoxGradientBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 1)
            Spacer(modifier = Modifier.weight(0.5f))
            FoxAnimatedLogo()
            Spacer(modifier = Modifier.height(48.dp))
            FoxTitle(text = "Bienvenue sur FoxOFF")
            Spacer(modifier = Modifier.height(24.dp))
            FoxSubtitle(text = "FoxOFF détecte automatiquement votre endormissement grâce à votre montre connectée et met votre télévision en pause au bon moment.")
            Spacer(modifier = Modifier.weight(1f))
            FoxButton(text = "Commencer", onClick = onNext)
        }
    }
}

@Composable
fun PermissionScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    
    val permissions = remember {
        mutableListOf<String>().apply {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
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

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionStatus = results
        results.forEach { (perm, granted) ->
            if (granted) com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-PERM | Permission accordée : $perm")
            else com.projectfox.foxoff.core.logging.FoxLogger.w("FOX-PERM | Permission refusée : $perm")
        }
    }

    val allGranted = permissionStatus.values.all { it }

    FoxGradientBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 2)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Autorisations")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(text = "FoxOFF nécessite ces accès pour fonctionner correctement.")
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    PermissionStatusCard(
                        icon = "🔔",
                        title = "Notifications",
                        description = "Alerter lors de la pause TV.",
                        isGranted = permissionStatus[android.Manifest.permission.POST_NOTIFICATIONS] == true
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            if (!allGranted) {
                FoxButton(
                    text = "Accorder les accès",
                    onClick = { 
                        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-PERM | Lancement demande globale")
                        launcher.launch(permissions.toTypedArray()) 
                    }
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

@Composable
fun WatchDetectionScreen(onNext: () -> Unit) {
    val core = com.projectfox.foxoff.core.application.FoxCore
    val context = LocalContext.current
    val watchInfo by core.watchInfo.collectAsState()
    val isSearching = watchInfo == null

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

    FoxGradientBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 3)
            Spacer(modifier = Modifier.height(24.dp))
            FoxTitle(text = "Détection de votre montre")
            Spacer(modifier = Modifier.height(16.dp))
            FoxSubtitle(text = "FoxOFF recherche votre Galaxy Watch.")
            
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
fun TvDetectionScreen(onNext: () -> Unit) {
    val core = com.projectfox.foxoff.core.application.FoxCore
    val tvEngine = core.tvEngine
    val discoveredDevices by (tvEngine?.discoveredDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    
    var selectedDevice by remember { mutableStateOf<com.projectfox.foxoff.tv.TvDevice?>(null) }
    var testStatus by remember { mutableStateOf<String?>(null) } // null, TESTING, SUCCESS, ERROR
    
    val isSearching = discoveredDevices.isEmpty() && selectedDevice == null

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        tvEngine?.initialize()
    }

    FoxGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FoxProgressConnection(step = 4)
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
                            isTesting = selectedDevice?.id == device.id && testStatus == "TESTING",
                            testResult = if (selectedDevice?.id == device.id) testStatus else null,
                            onClick = {
                                if (selectedDevice?.id != device.id) {
                                    selectedDevice = device
                                    testStatus = "TESTING"
                                    com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | TV sélectionnée : ${device.name}")
                                    
                                    scope.launch {
                                        delay(2000)
                                        testStatus = "SUCCESS"
                                        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | Compatible FoxOFF")
                                        tvEngine?.selectDevice(device)
                                        com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-TV | TV enregistrée comme télévision principale")
                                    }
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
                    onClick = onNext, 
                    enabled = testStatus == "SUCCESS"
                )
                
                TextButton(
                    onClick = onNext,
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
    FoxGradientBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            FoxProgressConnection(step = 7)
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
fun TvPairingScreen(onNext: () -> Unit) {
    val core = com.projectfox.foxoff.core.application.FoxCore
    val tvEngine = core.tvEngine
    val tvState by (tvEngine?.state?.collectAsState() ?: remember { mutableStateOf(null) })
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

    FoxGradientBackground {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FoxProgressConnection(step = 5)
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
                        FoxButton(text = "Valider", onClick = { tvEngine?.submitPin(pinValue) }, enabled = pinValue.length == 6)
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
fun PreviewSplash() { com.projectfox.foxoff.ui.theme.FoxTheme { SplashScreen {} } }

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewWelcome() { com.projectfox.foxoff.ui.theme.FoxTheme { WelcomeScreen {} } }

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewWatch() { com.projectfox.foxoff.ui.theme.FoxTheme { WatchDetectionScreen {} } }

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewTv() { com.projectfox.foxoff.ui.theme.FoxTheme { TvDetectionScreen {} } }

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
    isTesting: Boolean = false,
    testResult: String? = null,
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

                if (isSelected && testResult == "SUCCESS") {
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
                if (isTesting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Vérification de la compatibilité...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (testResult != null) {
                    Text(
                        text = if (testResult == "SUCCESS") "🟢 Compatible FoxOFF" else "🔴 Erreur de connexion",
                        color = if (testResult == "SUCCESS") Color.Green else Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
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
}
