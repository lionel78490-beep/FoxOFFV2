package com.projectfox.foxoff.wear.communication.model

/**
 * Represents the connection status between the watch and the phone.
 */
enum class ConnectionState {
    /** Watch is connected to the phone app. */
    CONNECTED,
    /** Watch is disconnected from the phone app. */
    DISCONNECTED,
    /** Watch is attempting to re-establish connection. */
    RECONNECTING
}
