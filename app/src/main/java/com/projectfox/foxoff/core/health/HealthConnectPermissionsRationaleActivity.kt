package com.projectfox.foxoff.core.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectfox.foxoff.ui.theme.FoxTheme

/**
 * Écran de politique de confidentialité affiché depuis le lien "privacy
 * policy" de l'écran de permissions Health Connect — requis par le SDK
 * (voir manifeste), même pour un usage strictement local. Explication
 * honnête, sans URL externe : FoxOFF est local-first, aucune donnée de
 * santé ne quitte l'appareil (voir VISION.md).
 */
class HealthConnectPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoxTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Utilisation des données Health Connect",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "FoxOFF lit uniquement votre fréquence cardiaque de repos " +
                                "et vos échantillons de fréquence cardiaque historiques via " +
                                "Health Connect, pour calculer une référence personnelle de " +
                                "BPM au repos utilisée dans la détection d'endormissement.",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ces données restent entièrement sur votre appareil : FoxOFF " +
                                "ne les envoie à aucun serveur, ne les partage avec aucun " +
                                "tiers, et ne les utilise que pour ce calcul local.",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Aucune permission d'écriture n'est demandée : FoxOFF ne " +
                                "modifie ni n'ajoute aucune donnée dans Health Connect.",
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
