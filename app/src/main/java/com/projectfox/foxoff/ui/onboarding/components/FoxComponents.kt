package com.projectfox.foxoff.ui.onboarding.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfox.foxoff.ui.theme.*

@Composable
fun FoxGradientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxBlack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(FoxElectricViolet.copy(alpha = 0.08f), Color.Transparent),
                        radius = 2500f
                    )
                )
        )
        content()
    }
}

/**
 * Fond "ciel étoilé + forêt" (bg_welcome_night.jpg, fourni par
 * l'utilisateur pour WelcomeScreen le 2026-08-14, étendu à tout le flux
 * d'onboarding sur demande explicite — "rajoute le fond forêt et ciel
 * étoilé sur toutes les pages"). Distinct de [FoxGradientBackground],
 * volontairement laissé inchangé pour Réglages/Accueil/Télécommande, hors
 * du périmètre de cette demande.
 */
@Composable
fun FoxOnboardingBackground(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = com.projectfox.foxoff.R.drawable.bg_welcome_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )
        // navigationBarsPadding() sur le contenu (pas sur l'image/le voile,
        // qui restent plein cadre derrière la barre système) — sans ça, le
        // bouton du bas de chaque écran est rogné par la barre de
        // navigation 3 boutons sur certains appareils (constaté par
        // l'utilisateur sur Galaxy Z Fold 6, 2026-08-14) : les 24dp de
        // marge fixe de chaque écran ne suffisent pas à eux seuls.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            content = content
        )
    }
}

@Composable
fun FoxAnimatedLogo(modifier: Modifier = Modifier, size: Int = 100) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Text(
        text = "🦊",
        fontSize = size.sp,
        modifier = modifier.scale(scale)
    )
}

// Orange plus vibrant que l'ancien FoxHomeOrange (0xFFFFA000) — demande
// explicite du brief typographique du 2026-08-14 ("vibrant warm orange",
// le symbole power doit être le point focal du wordmark).
private val FoxWordmarkOrange = Color(0xFFFF7A1A)

// Police géométrique "Outfit" (SIL OFL, voir docs/licenses/OUTFIT_FONT_OFL.txt)
// — remplace la police système par défaut pour coller au brief du
// 2026-08-14 : "modern premium sans-serif, clean geometric shapes,
// rounded but professional, slightly futuristic". Police variable :
// FontVariation.weight(...) sélectionne explicitement l'instance désirée,
// Compose ne la choisit pas automatiquement sans ça. Deux graisses
// distinctes ("La police de Fox est plus grasse que celle de OFF",
// 2e brief du 2026-08-14) : Black (900) pour "Fox", Bold (700) pour "FF".
@OptIn(ExperimentalTextApi::class)
private val OutfitBlack = FontFamily(
    Font(
        com.projectfox.foxoff.R.font.outfit_variable,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900))
    )
)

@OptIn(ExperimentalTextApi::class)
private val OutfitBold = FontFamily(
    Font(
        com.projectfox.foxoff.R.font.outfit_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

/**
 * Logo "Fox⏻FF" — reproduit fidèlement le brief typographique fourni par
 * l'utilisateur (2026-08-14) : "Fox" et "FF" en blanc (plus de bleu — la
 * version précédente colorait "FF" en FoxElectricBlue, changement
 * explicite ici), seul le symbole power est le point focal orange, avec
 * un léger halo ("subtle glow ... no excessive neon effect"). Halo
 * renforcé le même jour ("fait le logo plus lumineux") : léger glow blanc
 * sur le texte (`Shadow` avec blurRadius — fonctionne sur toutes les API,
 * contrairement à `Modifier.blur()` qui a besoin d'API 31+ alors que
 * minSdk = 30) et halo orange plus marqué autour du symbole power.
 */
@Composable
fun FoxWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 44.sp) {
    val textGlow = androidx.compose.ui.graphics.Shadow(
        color = Color.White.copy(alpha = 0.6f),
        offset = androidx.compose.ui.geometry.Offset.Zero,
        blurRadius = fontSize.value * 0.5f
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Fox",
            fontSize = fontSize,
            fontFamily = OutfitBlack,
            fontWeight = FontWeight.Black,
            color = Color.White,
            style = androidx.compose.ui.text.TextStyle(shadow = textGlow)
        )
        PowerGlyph(
            size = (fontSize.value * 1.0f).dp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = "FF",
            fontSize = fontSize,
            fontFamily = OutfitBold,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = androidx.compose.ui.text.TextStyle(shadow = textGlow)
        )
    }
}

/**
 * Symbole power ⏻ dessiné à la main (arc + trait vertical, `Canvas`) plutôt
 * que l'icône Material `PowerSettingsNew` : cette dernière a une épaisseur
 * de trait fixe, impossible à épaissir ("fait le plus épais", 2026-08-14).
 * Dessiner la forme nous-mêmes donne un contrôle direct sur `strokeWidthPx`.
 * Dégradé + copie sombre décalée pour le rendu "en relief" (même esprit
 * que l'ancienne version à base d'Icon, mais un Brush s'applique
 * nativement à drawArc/drawLine — plus besoin du hack BlendMode.SrcAtop).
 */
@Composable
private fun PowerGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(size)
            // Halo orange renforcé ("fait le logo plus lumineux",
            // 2026-08-14) — Modifier.shadow() plutôt que Modifier.blur()
            // (nécessiterait API 31+, minSdk du projet = 30).
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = FoxWordmarkOrange,
                spotColor = FoxWordmarkOrange
            )
    ) {
        val strokeWidthPx = this.size.minDimension * 0.17f
        val radius = this.size.minDimension * 0.34f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val lineTop = Offset(center.x, center.y - radius * 1.35f)
        val lineBottom = Offset(center.x, center.y - radius * 0.05f)

        translate(left = 1.dp.toPx(), top = 2.dp.toPx()) {
            drawArc(
                color = Color.Black.copy(alpha = 0.35f),
                startAngle = -55f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.35f),
                start = lineTop,
                end = lineBottom,
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        val brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFD54F), FoxWordmarkOrange, Color(0xFFB33D00))
        )
        drawArc(
            brush = brush,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
        drawLine(
            brush = brush,
            start = lineTop,
            end = lineBottom,
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun FoxCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                ),
                shape = RoundedCornerShape(32.dp)
            ),
        color = FoxBlackSurface.copy(alpha = 0.8f),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}

@Composable
fun FoxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradient: List<Color> = FoxGradient
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                if (enabled) Brush.horizontalGradient(gradient)
                else Brush.horizontalGradient(listOf(Color.DarkGray, Color.Gray))
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun FoxTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = Color.White,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun FoxSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        lineHeight = 28.sp,
        modifier = modifier
    )
}

@Composable
fun FoxProgressConnection(step: Int, totalSteps: Int = 9) {
    val progress = step.toFloat() / totalSteps.toFloat()
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = "⌚", fontSize = 24.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(FoxGradient))
            )
            if (step > 1 && step < 7) {
                 Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .scale(0.8f)
                ) {
                    Text(text = "🦊", fontSize = 14.sp)
                }
            }
        }
        Text(text = "📺", fontSize = 24.sp)
    }
}

@Composable
fun FoxDeviceCard(icon: String, name: String, status: String, isConnected: Boolean) {
    FoxCard(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(text = name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isConnected) Color.Green else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = status, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun FoxPermissionCard(icon: String, title: String, description: String) {
    FoxCard(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, color = if (icon == "❤️") FoxHeartRed else Color.White)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
