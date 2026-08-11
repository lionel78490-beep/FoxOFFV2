package com.projectfox.foxoff.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfox.foxoff.core.automation.SleepDetectionHistory
import com.projectfox.foxoff.core.automation.SleepDetectionOutcome
import com.projectfox.foxoff.core.automation.SleepDetectionRecord
import com.projectfox.foxoff.ui.dashboard.components.FoxAnalysisCard
import com.projectfox.foxoff.ui.dashboard.components.PremiumDashboardCard
import com.projectfox.foxoff.ui.dashboard.components.PremiumHeartRateCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Onglet Santé — réutilise les mêmes cartes que l'Accueil (source de
 * vérité unique : DashboardUiState, alimenté par FoxBrainState) pour
 * donner une vue dédiée aux données cardiaques et à l'analyse de sommeil,
 * sans dupliquer la logique de calcul. L'historique de détection
 * d'endormissement (SleepDetectionHistory) n'est encore alimenté que par
 * FoxOFF lui-même — la comparaison avec Samsung Health (Health Connect)
 * n'est pas encore branchée.
 */
@Composable
fun HealthScreen(uiState: DashboardUiState) {
    val detectionHistory by SleepDetectionHistory.records.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SANTÉ",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 24.dp)
        )

        PremiumHeartRateCard(
            bpm = uiState.currentBpm,
            min = uiState.minBpmToday,
            max = uiState.maxBpmToday,
            time = uiState.lastBpmTime
        )

        FoxAnalysisCard(
            state = uiState.sleepState,
            confidence = uiState.confidence,
            explanation = uiState.analysisExplanation
        )

        Text(
            text = "L'historique et les tendances sur plusieurs jours ne sont pas " +
                    "encore disponibles pour le BPM — seules les valeurs du jour en " +
                    "cours sont affichées ci-dessus.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )

        SleepDetectionHistoryCard(records = detectionHistory)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SleepDetectionHistoryCard(records: List<SleepDetectionRecord>) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()) }

    PremiumDashboardCard {
        Text(
            text = "Historique de détection d'endormissement",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Chaque détection par FoxOFF, avec le score au moment déclencheur — " +
                    "servira à comparer avec Samsung Health.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (records.isEmpty()) {
            Text(
                text = "Aucune détection pour l'instant.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            records.asReversed().take(10).forEach { record ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatter.format(record.detectedAt),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${(record.sleepProbability * 100).toInt()}% · " +
                                when (record.outcome) {
                                    SleepDetectionOutcome.PAUSED -> "Pause"
                                    SleepDetectionOutcome.CANCELLED -> "Annulée"
                                },
                        color = when (record.outcome) {
                            SleepDetectionOutcome.PAUSED -> Color.Green
                            SleepDetectionOutcome.CANCELLED -> Color(0xFFFFA000)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
