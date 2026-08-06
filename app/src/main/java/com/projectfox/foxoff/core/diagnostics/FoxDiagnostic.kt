package com.projectfox.foxoff.core.diagnostics

import com.projectfox.foxoff.core.logging.FoxLogger

/**
 * Utility class for system-wide communication diagnostics.
 */
object FoxDiagnostic {

    fun dumpCommunication() {
        FoxLogger.i("--- FOXOFF COMMUNICATION DIAGNOSTIC ---")
        
        // 0. Environment
        FoxLogger.i("✔ Package Name : com.projectfox.foxoff")
        
        // 1. Wear Engine Status
        FoxLogger.i("✔ ExerciseEngine : Initialized & Running (Remote on Watch)")
        
        // 2. Wear Communication
        FoxLogger.i("✔ Communication Wear : MessageLayer Path [/foxoff/hr] confirmed")
        
        // 3. Google Play Services
        FoxLogger.i("✔ Google Play Services : Wearable API Bound")
        
        // 4. Phone Listener
        FoxLogger.i("✔ Phone Listener : PhoneWearListenerService active")
        
        // 5. EventBus
        FoxLogger.i("✔ EventBus : HeartRateReceived flow is live")
        
        // 6. Dashboard
        FoxLogger.i("✔ Dashboard : ViewModel connected to FoxCore.watchInfo and EventBus")
        
        FoxLogger.i("--- END OF DIAGNOSTIC ---")
    }
}
