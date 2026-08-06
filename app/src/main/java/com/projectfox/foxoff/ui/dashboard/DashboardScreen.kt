package com.projectfox.foxoff.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
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
import com.projectfox.foxoff.ui.dashboard.components.*
import com.projectfox.foxoff.ui.onboarding.components.FoxGradientBackground

@Composable
fun DashboardScreen() {
    val viewModel: DashboardViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    FoxGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text(
                        text = "FOXOFF",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
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
                }
            }
        ) { padding ->
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MainStatusCard(
                        status = uiState.mainStatus,
                        color = uiState.mainStatusColor
                    )
                    
                    FoxAnalysisCard(
                        state = uiState.sleepState,
                        confidence = uiState.confidence,
                        explanation = uiState.analysisExplanation
                    )

                    PremiumHeartRateCard(
                        bpm = uiState.currentBpm,
                        min = uiState.minBpmToday,
                        max = uiState.maxBpmToday,
                        time = uiState.lastBpmTime
                    )
                    
                    DeviceCard(
                        icon = "⌚",
                        name = uiState.watchName,
                        isConnected = uiState.watchConnected,
                        battery = uiState.watchBattery,
                        type = uiState.watchConnectionType,
                        onAssociateClick = { viewModel.onAssociateClick(context) }
                    )
                    
                    TvDashboardCard(
                        name = uiState.tvName,
                        isConnected = uiState.tvConnected,
                        currentApp = uiState.tvCurrentApp,
                        lastCommand = uiState.tvLastCommand,
                        lastCommandTime = uiState.tvLastCommandTime
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else if (selectedTab == 2) {
                RemoteScreen()
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
}
