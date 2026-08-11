package com.projectfox.foxoff.wear.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import com.projectfox.foxoff.wear.core.FoxWearApplication
import com.projectfox.foxoff.wear.core.FoxWearLogger

/**
 * Handles incoming messages and system events from the phone.
 */
class FoxWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        FoxWearLogger.i("Message received from phone: ${messageEvent.path}")

        if (messageEvent.path == "/foxoff/request_watch_info") {
            FoxWearLogger.i("FOX-WEAR | Demande d'infos immédiate reçue du téléphone")
            FoxWearApplication.core.sendDeviceInfoNow()
        } else if (messageEvent.path == "/foxoff/confirm_awake") {
            FoxWearLogger.i("FOX-WEAR | Demande de confirmation avant pause reçue du téléphone")
            ConfirmAwakeNotifier.show(applicationContext)
        } else if (messageEvent.path == "/foxoff/movement_low_power") {
            FoxWearLogger.i("FOX-WEAR | TV en pause -> passage du mouvement en basse fréquence")
            FoxWearApplication.core.setMovementLowPowerMode(true)
        } else if (messageEvent.path == "/foxoff/movement_normal_power") {
            FoxWearLogger.i("FOX-WEAR | TV rallumée -> retour du mouvement en fréquence normale")
            FoxWearApplication.core.setMovementLowPowerMode(false)
        }
    }

    /**
     * Reconnexion détectée au téléphone (ex: retour du Bluetooth après une
     * extinction/rallumage de la montre) : renvoie immédiatement watch_info
     * plutôt que d'attendre le prochain envoi périodique (jusqu'à 60s), pour
     * que le téléphone puisse repasser "Connectée" au plus vite.
     */
    override fun onPeerConnected(node: Node) {
        super.onPeerConnected(node)
        FoxWearLogger.i("FOX-WEAR | onPeerConnected : ${node.displayName} -> renvoi immédiat de watch_info")
        FoxWearApplication.core.sendDeviceInfoNow()
    }
}
