package com.projectfox.foxoff.ui.onboarding.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectfox.foxoff.ui.onboarding.components.*
import com.projectfox.foxoff.ui.onboarding.setup.*

@Composable
fun EnvironmentSetupScreen(onNext: () -> Unit) {
    val viewModel: EnvironmentSetupViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isAllCompleted) {
        if (state.isAllCompleted) {
            com.projectfox.foxoff.core.logging.FoxLogger.i("FOX-SETUP | Conditions minimales atteintes -> ouverture Setup Complete")
            onNext()
        }
    }

    FoxGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FoxEnvironmentProgress(
                watchCompleted = state.watchStep.status == SetupStepStatus.COMPLETED,
                tvCompleted = state.tvStep.status == SetupStepStatus.COMPLETED,
                isAllCompleted = state.isAllCompleted
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            FoxTitle(text = "Initialisation Fox Brain")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            FoxSubtitle(
                text = "FoxOFF prépare votre intelligence artificielle de sommeil."
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                FoxSetupCard(state.watchStep)
                FoxSetupCard(state.tvStep)
                FoxSetupCard(state.sensorsStep)
                FoxSetupCard(state.brainStep)
            }

            AnimatedVisibility(
                visible = state.isAllCompleted,
                enter = fadeIn() + expandVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "FoxOFF est maintenant prêt à surveiller votre sommeil et à contrôler automatiquement votre télévision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    FoxButton(
                        text = "Accéder au tableau de bord",
                        onClick = onNext
                    )
                }
            }
            
            if (!state.isAllCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { state.overallProgress },
                        modifier = Modifier.weight(1f).height(4.dp).padding(end = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(state.overallProgress * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
