package com.projectfox.foxoff.wear.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.projectfox.foxoff.wear.BuildConfig
import com.projectfox.foxoff.wear.R
import com.projectfox.foxoff.wear.core.FoxWearApplication
import com.projectfox.foxoff.wear.core.FoxWearCore
import com.projectfox.foxoff.wear.core.PassiveEngineDiagnostic
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Même couleur de fond que l'icône/l'image de présentation générées cette
// session (#00000A) — cohérence de marque avec le téléphone.
private val FoxWatchBackground = Color(0xFF00000A)

/**
 * Demande explicite de l'utilisateur (2026-08-12) : à l'ouverture, l'écran
 * principal ne doit montrer QUE le renard et un indicateur simple que la
 * surveillance de l'endormissement est active — plus le tableau de bord
 * technique (BPM brut, connexion, diagnostic moteur passif) qui s'y
 * trouvait avant. Ce diagnostic reste utile (dépannage réel, notamment la
 * note Samsung permanente) donc pas supprimé, mais déplacé sur un second
 * onglet "Réglages" (glissement horizontal, motif standard sur Wear OS —
 * voir HorizontalPager/HorizontalPageIndicator ci-dessous), plutôt que
 * d'imposer un tap ambigu sur l'écran par défaut.
 */
@Composable
fun WearHomeScreen(isPermissionGranted: Boolean) {
    val core = FoxWearApplication.core

    LaunchedEffect(isPermissionGranted) {
        com.projectfox.foxoff.wear.core.FoxWearLogger.i("FOX-WEAR | UI | WearHomeScreen affiché. isPermissionGranted=$isPermissionGranted")
    }

    val isMonitoring by core.isMonitoring.collectAsState(initial = false)

    Scaffold(modifier = Modifier.fillMaxSize()) {
        if (!isPermissionGranted) {
            Box(
                modifier = Modifier.fillMaxSize().background(FoxWatchBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Permission requise",
                    textAlign = TextAlign.Center,
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { 2 })
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize().background(FoxWatchBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        when (page) {
                            0 -> WearIdleScreen(isMonitoring = isMonitoring)
                            else -> WearDiagnosticsView(core)
                        }
                    }
                }
                HorizontalPageIndicator(
                    pageIndicatorState = remember(pagerState) { PagerIndicatorState(pagerState) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                )
            }
        }
    }
}

/** Adapte androidx.compose.foundation.pager.PagerState au contrat attendu par HorizontalPageIndicator (Wear Compose Material). */
private class PagerIndicatorState(private val pagerState: PagerState) : PageIndicatorState {
    override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
    override val selectedPage: Int get() = pagerState.currentPage
    override val pageCount: Int get() = pagerState.pageCount
}

/**
 * Quatrième version (2026-08-12, même jour) : le renard en plein écran
 * (`ContentScale.Crop`) plutôt qu'une petite icône centrée — demande
 * explicite. `fox_watching_hero`/`fox_sleeping_hero` sont déjà des badges
 * circulaires sur fond sombre assorti à `FoxWatchBackground` : en plein
 * écran, le cercle déborde naturellement jusqu'aux bords d'un cadran rond,
 * sans bande de fond visible. Le texte reste incrusté par-dessus, sur un
 * fond semi-opaque pour rester lisible quel que soit ce qu'il y a dessous.
 */
@Composable
private fun WearIdleScreen(isMonitoring: Boolean) {
    val breathPeriodMs = if (isMonitoring) 1500 else 4200
    val breathAmplitude = if (isMonitoring) 0.035f else 0.02f
    val infiniteTransition = rememberInfiniteTransition(label = "foxBreath")
    val breath by infiniteTransition.animateFloat(
        initialValue = 1f - breathAmplitude,
        targetValue = 1f + breathAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(breathPeriodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "foxBreathScale"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Image(
            painter = painterResource(
                if (isMonitoring) R.drawable.fox_watching_hero else R.drawable.fox_sleeping_hero
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                }
        )
        Text(
            text = if (isMonitoring) "Fox veille sur vous" else "Fox dort",
            textAlign = TextAlign.Center,
            color = Color.White,
            style = MaterialTheme.typography.caption1,
            modifier = Modifier
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun WearDiagnosticsView(core: FoxWearCore) {
    val status by core.status.collectAsState(initial = "READY")
    val isMonitoring by core.isMonitoring.collectAsState(initial = false)
    val sample by core.heartRateSamples.collectAsState(initial = null)
    val connectionState by core.connectionState.collectAsState(initial = com.projectfox.foxoff.wear.communication.model.ConnectionState.DISCONNECTED)
    val batteryPercent by core.batteryPercent.collectAsState(initial = null)
    val passiveDiagnostic by core.passiveDiagnostic.collectAsState(initial = PassiveEngineDiagnostic())

    // Même valeur réellement envoyée au téléphone (voir FoxWearCore.sendDeviceInfo) —
    // "--%" tant que la première lecture n'est pas encore arrivée.
    val battery = batteryPercent?.let { "$it%" } ?: "--%"
    val version = BuildConfig.VERSION_NAME

    @Suppress("DEPRECATION")
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp)
    ) {
        item {
            Text(
                text = "🦊 Réglages",
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

        // Diagnostic TEMPORAIRE du moteur passif (BPM écran éteint) —
        // à retirer une fois ce comportement validé sur matériel réel.
        item {
            Text(
                text = "— Diagnostic passif —",
                style = MaterialTheme.typography.caption3,
                color = Color.Gray,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item {
            StatusItem(
                label = "Supporté",
                value = when (passiveDiagnostic.supported) {
                    true -> "Oui"
                    false -> "Non"
                    null -> "?"
                }
            )
        }
        item {
            StatusItem(label = "Démarré", value = if (passiveDiagnostic.started) "Oui" else "Non")
        }
        item {
            StatusItem(
                label = "Dernier point",
                value = passiveDiagnostic.lastSampleAt?.let {
                    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(it)
                } ?: "jamais"
            )
        }
        if (passiveDiagnostic.lastError != null) {
            item {
                Text(
                    text = passiveDiagnostic.lastError ?: "",
                    style = MaterialTheme.typography.caption3,
                    color = Color.Red,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Note PERMANENTE (contrairement au diagnostic ci-dessus) : sur
        // certaines montres Samsung, des réglages système propres à
        // One UI Watch peuvent couper la remontée du BPM écran éteint
        // même quand le canal passif est correctement démarré côté
        // app — confirmé sur matériel réel (Galaxy Watch8). FoxOFF ne
        // peut pas modifier ces réglages à la place de l'utilisateur.
        item {
            Text(
                text = "Si le BPM s'arrête écran éteint (Samsung) :",
                style = MaterialTheme.typography.caption3,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp)
            )
        }
        item {
            Text(
                text = "1. Batterie → Veille : retirer FoxOFF Watch\n" +
                        "2. App FoxOFF Watch → désactiver « Suspendre l'activité si inutilisée »\n" +
                        "3. App FoxOFF Watch → Accès autorisé : « Tout le temps »",
                style = MaterialTheme.typography.caption3,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        item {
            Text(
                text = "(Glisser vers la droite pour revenir à l'accueil)",
                style = MaterialTheme.typography.caption3,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp)
            )
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
