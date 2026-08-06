package com.projectfox.foxoff.wear.core

import android.app.Application
import android.content.Intent
import com.projectfox.foxoff.wear.service.FoxWearForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Main application class for the Wear OS module.
 */
class FoxWearApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        lateinit var core: FoxWearCore
            private set
    }

    override fun onCreate() {
        super.onCreate()
        FoxWearLogger.i("FoxWearApplication started")
        
        core = FoxWearCore(this)
        
        applicationScope.launch {
            try {
                core.initialize()
                
                // Start Foreground Service first
                startForegroundService(Intent(this@FoxWearApplication, FoxWearForegroundService::class.java))
                
                // Wait for service confirmation before starting sensors
                core.isForegroundServiceActive.filter { it }.first()
                
                // Now start monitoring
                core.startMonitoring()
            } catch (e: Exception) {
                FoxWearLogger.e("Critical error during application startup", e)
            }
        }
    }
}
