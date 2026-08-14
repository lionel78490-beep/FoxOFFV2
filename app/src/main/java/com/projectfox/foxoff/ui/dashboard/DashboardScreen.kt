package com.projectfox.foxoff.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectfox.foxoff.tv.TvConnectionStatus
import com.projectfox.foxoff.ui.dashboard.components.*
import com.projectfox.foxoff.ui.onboarding.components.FoxGradientBackground

@Composable
fun DashboardScreen() {
    val viewModel: DashboardViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Reconnexion automatique de la montre à l'ouverture du Dashboard (ex:
    // relance de l'app), une seule fois par session — pas à chaque
    // changement d'onglet. FoxCore.discoverWatch() a son propre verrou
    // anti-concurrence.
    LaunchedEffect(Unit) {
        viewModel.ensureWatchConnected(context)
    }

    // Proposition ponctuelle de la surveillance en arrière-plan pour les
    // utilisateurs ayant déjà terminé l'onboarding avant cette fonction
    // (voir BackgroundServiceSettings.hasSeenPrompt). Les nouveaux
    // utilisateurs y répondent déjà dans l'écran Permissions de
    // l'onboarding, qui marque aussi ce flag — jamais affichée deux fois.
    var showBackgroundMonitoringPrompt by remember {
        mutableStateOf(!com.projectfox.foxoff.core.application.BackgroundServiceSettings.hasSeenPrompt(context))
    }

    // Créneau(x) sélectionné(s) pour la surveillance (voir ActiveHoursSettings) —
    // miroir réactif, reflète immédiatement un changement fait depuis Réglages.
    val selectedActiveHoursSlots by com.projectfox.foxoff.core.application.ActiveHoursSettings.selectedSlotsState.collectAsState()
    LaunchedEffect(Unit) {
        com.projectfox.foxoff.core.application.ActiveHoursSettings.getSelectedSlots(context)
    }

    // Intention persistée de surveillance en arrière-plan (tâche #13/#15) —
    // même miroir réactif que celui déjà utilisé par SettingsScreen
    // (BackgroundServiceSettings.enabledState) : le nouveau bouton de
    // l'Accueil (2026-08-14, voir FoxHomeHero) reflète et pilote exactement
    // le même état, pas un nouveau réglage parallèle.
    val surveillanceActive by com.projectfox.foxoff.core.application.BackgroundServiceSettings.enabledState.collectAsState()
    LaunchedEffect(Unit) {
        com.projectfox.foxoff.core.application.BackgroundServiceSettings.isEnabled(context)
    }

    FoxGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // L'onglet Accueil (0) affiche désormais son propre header
                // centré "Fox⏻FF" + tagline, à l'intérieur de FoxHomeHero
                // (2026-08-14, calqué sur une maquette de référence) —
                // afficher aussi ce bandeau ici dupliquerait le nom de
                // l'app en haut de l'écran. Inchangé pour tous les autres
                // onglets.
                if (selectedTab != 0) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                        Text(
                            text = "FOXOFF",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF090909),
                    contentColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        label = { Text("Accueil") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White.copy(alpha = 0.1f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
                        label = { Text("Santé") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White.copy(alpha = 0.1f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                        label = { Text("TV") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White.copy(alpha = 0.1f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Paramètres") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White.copy(alpha = 0.1f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        label = { Text("Historique") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White.copy(alpha = 0.1f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        ) { padding ->
            if (selectedTab == 0) {
                // Demande explicite du 2026-08-14 (calquée sur une maquette
                // de référence) : header centré + illustration contenue +
                // statut + bouton rond, sans info montre/TV/plage horaire —
                // FoxHomeHero occupe toute la zone de contenu (plus de
                // verticalScroll : MainStatusCard/FoxAnalysisCard/
                // ActiveHoursInfoCard/DeviceCard/TvDashboardCard restent
                // définies dans DashboardComponents.kt, réutilisables
                // ailleurs, mais ne sont plus affichées ici).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    FoxHomeHero(surveillanceActive = surveillanceActive)
                }
            } else if (selectedTab == 1) {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    HealthScreen(uiState = uiState)
                }
            } else if (selectedTab == 2) {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    RemoteScreen()
                }
            } else if (selectedTab == 3) {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    com.projectfox.foxoff.ui.settings.SettingsScreen()
                }
            } else if (selectedTab == 4) {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    HistoryScreen()
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(text = "Écran en cours de développement", color = Color.Gray)
                }
            }
        }
    }

    if (showBackgroundMonitoringPrompt) {
        com.projectfox.foxoff.ui.settings.BackgroundMonitoringPromptDialog(
            onDismiss = { showBackgroundMonitoringPrompt = false }
        )
    }
}
