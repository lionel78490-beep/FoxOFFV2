package com.projectfox.foxoff.sensors.events

import com.projectfox.foxoff.core.events.FoxEvent
import com.projectfox.foxoff.sensors.model.HeartRateSample

sealed interface SensorEvent : FoxEvent {
    data class HeartRateReceived(val sample: HeartRateSample) : SensorEvent
    data class WatchInfoReceived(val name: String, val battery: Int, val isConnected: Boolean) : SensorEvent
}
