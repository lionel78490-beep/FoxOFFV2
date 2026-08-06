package com.projectfox.foxoff.core.events

import kotlinx.coroutines.flow.*

class FoxEventBus {
    private val _events = MutableSharedFlow<FoxEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    suspend fun publish(event: FoxEvent) {
        _events.emit(event)
    }

    inline fun <reified T : FoxEvent> subscribeAs(): Flow<T> {
        return events.filterIsInstance<T>()
    }
}
