package com.projectfox.foxoff.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.projectfox.foxoff.wear.core.FoxWearLogger
import com.projectfox.foxoff.wear.ui.WearHomeScreen

/**
 * Main Activity for the Wear OS application.
 */
class MainActivity : ComponentActivity() {

    // Official Google Health Services permissions for Wear OS 5 (API 35) and Wear OS 6 (API 36)
    private val heartRatePermission = if (Build.VERSION.SDK_INT >= 36) {
        "android.permission.health.READ_HEART_RATE"
    } else {
        Manifest.permission.BODY_SENSORS
    }

    private val backgroundHeartRatePermission = if (Build.VERSION.SDK_INT >= 36) {
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    } else if (Build.VERSION.SDK_INT >= 33) {
        "android.permission.BODY_SENSORS_BACKGROUND"
    } else {
        null
    }

    private val essentialPermissions = mutableListOf(
        heartRatePermission,
        Manifest.permission.ACTIVITY_RECOGNITION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        FoxWearLogger.i("FOX-WEAR | Permission callback:")
        results.forEach { (perm, granted) ->
            FoxWearLogger.i("FOX-WEAR |   $perm = ${if (granted) "GRANTED" else "DENIED"}")
        }
        performPermissionCheck(requestIfMissing = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoxWearLogger.i("FOX-WEAR | MainActivity onCreate")
        performPermissionCheck(requestIfMissing = true)
    }

    override fun onPause() {
        super.onPause()
        FoxWearLogger.i("FOX-WEAR | MainActivity onPause")
    }

    override fun onStop() {
        super.onStop()
        FoxWearLogger.i("FOX-WEAR | MainActivity onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        FoxWearLogger.i("FOX-WEAR | MainActivity onDestroy")
    }

    private fun performPermissionCheck(requestIfMissing: Boolean) {
        var allEssentialGranted = true
        val missingEssential = mutableListOf<String>()

        FoxWearLogger.i("--- FOX-WEAR PERMISSION AUDIT ---")
        
        essentialPermissions.forEach { perm ->
            val isGranted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            FoxWearLogger.i("FOX-WEAR | $perm = ${if (isGranted) "GRANTED" else "DENIED"}")
            if (!isGranted) {
                allEssentialGranted = false
                missingEssential.add(perm)
            }
        }

        // Background permission is required for sleep monitoring but often can't be requested 
        // in the same batch on some Wear OS versions. We log it here.
        backgroundHeartRatePermission?.let { perm ->
            val isGranted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            FoxWearLogger.i("FOX-WEAR | $perm (Background) = ${if (isGranted) "GRANTED" else "MISSING (Optional for UI)"}")
        }

        if (allEssentialGranted) {
            FoxWearLogger.i("FOX-WEAR | Toutes les permissions essentielles sont valides")
        } else {
            FoxWearLogger.w("FOX-WEAR | Manquant: ${missingEssential.joinToString()}")
            if (requestIfMissing) {
                FoxWearLogger.i("FOX-WEAR | Launching permission request for essential set")
                requestPermissionLauncher.launch(essentialPermissions)
            }
        }

        setContent {
            WearHomeScreen(isPermissionGranted = allEssentialGranted)
        }
    }
}
