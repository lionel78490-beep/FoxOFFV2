package com.projectfox.foxoff.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectfox.foxoff.R
import com.projectfox.foxoff.tv.TvConnectionStatus
import com.projectfox.foxoff.ui.dashboard.components.*
import com.projectfox.foxoff.ui.onboarding.components.FoxGradientBackground

/**
 * Sous-écran affiché sous l'onglet Paramètres — Santé, Historique et TV
 * n'ont plus leur propre onglet dans la barre du bas (demande explicite du
 * 2026-08-14 : d'abord Santé/Historique, puis TV "il ne sert a rien ici"),
 * ils sont désormais atteints depuis Paramètres, avec un simple bouton
 * retour.
 */
private enum class SettingsSubScreen { MAIN, HEALTH, HISTORY, TV }

@Composable
fun DashboardScreen() {
    val viewModel: DashboardViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var settingsSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }
    val context = LocalContext.current

    BackHandler(enabled = selectedTab == 1 && settingsSubScreen != SettingsSubScreen.MAIN) {
        settingsSubScreen = SettingsSubScreen.MAIN
    }

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
        // Fond "renard sur la lune" de l'Accueil dessiné ICI (au-dessus de
        // FoxGradientBackground, en dehors du Scaffold) pour occuper tout
        // l'écran — statut de la barre système compris —, demande
        // explicite du 2026-08-14 ("le fond d'écran doit maintenant
        // prendre la totalité de l'écran") : à l'intérieur du Scaffold, la
        // zone de contenu exclut le topBar/bottomBar et les insets système.
        // L'onglet Paramètres (et ses sous-écrans Montre/Historique/TV) a
        // aussi eu le fond bg_home_* en plein écran (2026-08-16, "rajoute
        // le fond comme l'ecran d'acceuil dans les parametres" puis "je
        // veux que sa prenne tout l'ecran de l'onglet parametre"), mais le
        // renard gênait la lisibilité des tuiles/cartes/listes — demande
        // finale : le fond ciel étoilé + forêt (bg_welcome_night, déjà
        // utilisé par l'onboarding, sans le renard) en plein écran, avec le
        // renard réduit uniquement en médaillon haut-droite par-dessus.
        if (selectedTab == 0) {
            Image(
                painter = painterResource(
                    id = if (surveillanceActive) R.drawable.bg_home_active else R.drawable.bg_home_night
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        } else if (selectedTab == 1) {
            Image(
                painter = painterResource(id = R.drawable.bg_welcome_night),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
            Image(
                painter = painterResource(
                    id = if (surveillanceActive) R.drawable.badge_fox_moon_active else R.drawable.badge_fox_moon_night
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 20.dp)
                    .size(84.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
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
                        // Santé/Historique/TV n'ont plus d'onglet dédié
                        // (2026-08-14) — un bouton retour simple les
                        // ramène à la liste des Paramètres plutôt que de
                        // dupliquer un système de navigation complet.
                        if (selectedTab == 1 && settingsSubScreen != SettingsSubScreen.MAIN) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                IconButton(onClick = { settingsSubScreen = SettingsSubScreen.MAIN }) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Retour",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    text = when (settingsSubScreen) {
                                        SettingsSubScreen.HEALTH -> "SANTÉ"
                                        SettingsSubScreen.HISTORY -> "HISTORIQUE"
                                        SettingsSubScreen.TV -> "TV"
                                        SettingsSubScreen.MAIN -> ""
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp
                                )
                            }
                        } else {
                            Text(
                                text = "FOXOFF",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                        }
                    }
                }
            },
            bottomBar = {
                // Barre du bas réduite à 2 onglets (2026-08-14) : TV retiré
                // ("il ne sert a rien ici"), rejoint Santé/Historique comme
                // sous-écran de Paramètres — voir SettingsSubScreen.
                // Cartes-onglets (2026-08-14, "comme tu as fait avec
                // sommeil tv montre systeme") : remplace le NavigationBar
                // Material standard par le même style que la grille de
                // MiniStatCard de FoxHomeHero, plutôt que la barre pleine
                // largeur à indicateur pilule par défaut.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BottomTabCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Home,
                        label = "Accueil",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    BottomTabCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Settings,
                        label = "Paramètres",
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            // Retaper l'onglet Paramètres ramène toujours à
                            // sa liste principale, jamais au sous-écran où
                            // l'utilisateur se trouvait avant de le quitter.
                            settingsSubScreen = SettingsSubScreen.MAIN
                        }
                    )
                }
            }
        ) { padding ->
            if (selectedTab == 0) {
                // Dashboard principal permanent (2026-08-14, brief détaillé) :
                // FoxHomeHero affiche maintenant l'état réel montre/TV/
                // sommeil en plus du renard — toutes ces données existaient
                // déjà dans uiState (DashboardViewModel), simplement
                // branchées ici plutôt que dupliquées.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    FoxHomeHero(
                        surveillanceActive = surveillanceActive,
                        watchConnected = uiState.watchConnected,
                        watchKnown = uiState.watchKnown,
                        watchName = uiState.watchName,
                        tvName = uiState.tvName,
                        tvStatus = uiState.tvStatus,
                        sleepState = uiState.sleepState,
                        confidence = uiState.confidence,
                        onOpenTv = {
                            selectedTab = 1
                            settingsSubScreen = SettingsSubScreen.TV
                        },
                        onOpenWatch = {
                            selectedTab = 1
                            settingsSubScreen = SettingsSubScreen.HEALTH
                        },
                        onOpenSettings = {
                            selectedTab = 1
                            settingsSubScreen = SettingsSubScreen.MAIN
                        }
                    )
                }
            } else if (selectedTab == 1) {
                // Santé, Historique et TV n'ont plus d'onglet dédié
                // (2026-08-14) — sous-écrans de Paramètres, pilotés par
                // settingsSubScreen.
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (settingsSubScreen) {
                        SettingsSubScreen.MAIN -> com.projectfox.foxoff.ui.settings.SettingsScreen(
                            onOpenHistory = { settingsSubScreen = SettingsSubScreen.HISTORY },
                            onOpenTv = { settingsSubScreen = SettingsSubScreen.TV }
                        )
                        SettingsSubScreen.HEALTH -> HealthScreen(uiState = uiState)
                        SettingsSubScreen.HISTORY -> HistoryScreen()
                        SettingsSubScreen.TV -> RemoteScreen()
                    }
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

/**
 * Onglet du bas façon carte — même langage visuel que la grille d'état de
 * FoxHomeHero (Sommeil/TV/Montre/Système), demandé explicitement le
 * 2026-08-14 pour remplacer le NavigationBar Material standard.
 */
@Composable
private fun BottomTabCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (selected) 0.14f else 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color.Gray,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
