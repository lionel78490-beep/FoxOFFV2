package com.projectfox.foxoff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.projectfox.foxoff.ui.dashboard.DashboardScreen
import com.projectfox.foxoff.ui.onboarding.OnboardingNavigation
import com.projectfox.foxoff.ui.theme.FoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var showOnboarding by remember { mutableStateOf(true) }
            
            FoxTheme {
                if (showOnboarding) {
                    OnboardingNavigation(onFinish = {
                        showOnboarding = false
                    })
                } else {
                    DashboardScreen()
                }
            }
        }
    }
}
