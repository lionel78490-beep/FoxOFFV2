package com.projectfox.foxoff.tvlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectfox.foxoff.tv.FoxTvController
import com.projectfox.foxoff.tv.FoxTvSettings
import com.projectfox.foxoff.ui.components.HexPinKeyboard
import kotlinx.coroutines.launch

class TvLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var ip by remember { mutableStateOf("192.168.1.22") }
            var pin by remember { mutableStateOf("") }
            var status by remember { mutableStateOf("Déconnecté") }
            var pinReady by remember { mutableStateOf(false) }

            val scope = rememberCoroutineScope()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "FOX TV LAB",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Adresse IP de la TV") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        status = "Connexion et lancement du pairing…"
                        pinReady = false

                        scope.launch {
                            status = TvLabConnection.connect(
                                applicationContext,
                                ip.trim()
                            )

                            pinReady = status.contains("ConfigurationAck")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CONNECT")
                }

                Spacer(Modifier.height(24.dp))

                Text("État : $status")

                if (pinReady || status.contains("APPAIRAGE TERMINÉ")) {
                    Spacer(Modifier.height(24.dp))

                    HexPinKeyboard(
                        pin = pin,
                        onPinChanged = { newPin ->
                            pin = newPin
                        }
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                status = TvLabConnection.sendPin(pin.trim())

                                if (status.contains("APPAIRAGE TERMINÉ")) {
                                    FoxTvSettings.saveTvIp(
                                        applicationContext,
                                        ip.trim()
                                    )
                                }
                            }
                        },
                        enabled = pin.length == 6,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VALIDER LE PIN")
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                status = "Connexion Remote…"

                                val result = FoxTvController.togglePlayPause(
                                    context = applicationContext,
                                    tvIp = ip.trim()
                                )

                                status = result.fold(
                                    onSuccess = {
                                        "✅ CHAÎNE PRODUCTION OK\n⏯️ PLAY/PAUSE ENVOYÉ"
                                    },
                                    onFailure = { error ->
                                        "❌ ÉCHEC PRODUCTION\n${error.message}"
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TESTER PAUSE")
                    }
                }
            }
        }
    }
}