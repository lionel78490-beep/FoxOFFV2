package com.projectfox.foxoff.ui.onboarding.setup

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfox.foxoff.ui.onboarding.components.*
import com.projectfox.foxoff.ui.theme.*

@Composable
fun FoxEnvironmentProgress(
    watchCompleted: Boolean,
    tvCompleted: Boolean,
    isAllCompleted: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Watch Icon
        Text(text = "⌚", fontSize = 28.sp)
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (watchCompleted) Color.Green else Color.Gray)
                .padding(start = 8.dp)
        )

        // Line 1
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (watchCompleted) Brush.horizontalGradient(FoxGradient)
                    else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
                )
        )

        // Fox & Heart
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🦊", fontSize = 32.sp)
            if (isAllCompleted) {
                Text(
                    text = "❤️",
                    fontSize = 12.sp,
                    modifier = Modifier.graphicsLayer(alpha = pulseAlpha)
                )
            }
        }

        // Line 2
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (tvCompleted) Brush.horizontalGradient(FoxGradient)
                    else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
                )
        )

        // TV Icon
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (tvCompleted) Color.Green else Color.Gray)
                .padding(end = 8.dp)
        )
        Text(text = "📺", fontSize = 28.sp)
    }
}

@Composable
fun FoxSetupCard(info: SetupStepInfo) {
    FoxCard(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = info.icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(20.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = info.currentMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (info.status == SetupStepStatus.COMPLETED) Color.Green else Color.Gray
                )
                
                if (info.status == SetupStepStatus.IN_PROGRESS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { info.progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = FoxElectricBlue,
                        trackColor = Color.White.copy(alpha = 0.1f),
                    )
                }
            }

            Crossfade(targetState = info.status, label = "statusIcon") { status ->
                when (status) {
                    SetupStepStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    SetupStepStatus.IN_PROGRESS -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = FoxElectricBlue
                        )
                    }
                    else -> {
                         Box(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
