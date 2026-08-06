package com.projectfox.foxoff.core.application

import android.app.Application
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FoxApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        FoxLogger.i("FoxApplication started")
        
        appScope.launch {
            FoxCore.initialize(this@FoxApplication)
            FoxCore.startModules()
        }
    }
}
