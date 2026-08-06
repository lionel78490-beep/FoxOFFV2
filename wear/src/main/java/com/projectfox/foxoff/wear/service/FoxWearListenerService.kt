package com.projectfox.foxoff.wear.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.projectfox.foxoff.wear.core.FoxWearLogger

/**
 * Handles incoming messages and system events from the phone.
 */
class FoxWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        FoxWearLogger.i("Message received from phone: ${messageEvent.path}")
    }
}
