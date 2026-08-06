package com.projectfox.foxoff.core.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.sensors.events.SensorEvent
import com.projectfox.foxoff.sensors.model.HeartRateSample
import com.projectfox.foxoff.sensors.model.SensorBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.time.Instant

/**
 * Listens for data coming from the Wear OS module.
 */
class PhoneWearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        FoxLogger.i("FOX-PHONE | Listener | Service CRÉÉ et prêt à recevoir des messages")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        
        if (messageEvent.path == "/foxoff/hr") {
            val bpm = ByteBuffer.wrap(messageEvent.data).float
            val now = Instant.now()
            FoxLogger.i("FOX-PHONE | Listener | Message REÇU [/foxoff/hr] : $bpm BPM | Source: ${messageEvent.sourceNodeId}")
            
            serviceScope.launch {
                val sample = HeartRateSample(
                    bpm = bpm,
                    accuracy = 3,
                    timestamp = now,
                    backend = SensorBackend.UNKNOWN
                )
                FoxLogger.i("FOX-PHONE | Listener | Publication EventBus BPM: $bpm")
                FoxCore.eventBus.publish(SensorEvent.HeartRateReceived(sample))
            }
        } else if (messageEvent.path == "/foxoff/watch_info") {
            val data = String(messageEvent.data).split("|")
            if (data.size >= 2) {
                val name = data[0]
                val battery = data[1].toIntOrNull() ?: 0
                FoxLogger.i("FOX-PHONE | Listener | Infos Montre REÇUES : $name | Batterie : $battery% | Source: ${messageEvent.sourceNodeId}")
                serviceScope.launch {
                    FoxCore.eventBus.publish(SensorEvent.WatchInfoReceived(name, battery, true))
                }
            }
        }
    }
}
