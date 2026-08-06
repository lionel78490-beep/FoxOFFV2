package com.projectfox.foxoff.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.projectfox.foxoff.wear.core.FoxWearApplication

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
    
    val battery = "XX%"
    val version = "0.0.1"

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
