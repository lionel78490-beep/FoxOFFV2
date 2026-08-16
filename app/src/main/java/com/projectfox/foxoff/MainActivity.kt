package com.projectfox.foxoff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.projectfox.foxoff.ui.dashboard.DashboardScreen
import com.projectfox.foxoff.ui.onboarding.OnboardingNavigation
import com.projectfox.foxoff.ui.onboarding.OnboardingSettings
import com.projectfox.foxoff.ui.theme.FoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Route de reprise après le redémarrage forcé par WatchBrandScreen
        // (changement de marque de montre en cours d'onboarding — FoxCore
        // ne résout son WatchTransport qu'une fois par processus, voir
        // FoxCore.initialize()). Sans elle, le redémarrage renvoyait
        // l'utilisateur tout au début de l'onboarding (bug signalé le
        // 2026-08-14 : "sa me remet a la toute premiere page"), perdant
        // 5 écrans déjà remplis pour un simple changement de marque.
        val onboardingStartRoute = intent.getStringExtra(EXTRA_ONBOARDING_START_ROUTE) ?: "welcome"
        // Changement de montre depuis Réglages (post-onboarding, voir
        // SettingsScreen.changeWatchBrand) : redémarrage nécessaire pour que
        // FoxCore résolve le nouveau WatchTransport (même contrainte que
        // WatchBrandScreen pendant l'onboarding), mais on ne veut PAS rejouer
        // tout l'onboarding — juste re-passer par l'écran de recherche de
        // montre (2026-08-16 : "sa me remet a la page d'acceuil je voudrais
        // que sa me mette en recherche d'une nouvelle montre a connecté").
        val forceWatchSetup = intent.getBooleanExtra(EXTRA_WATCH_ONLY_SETUP, false)

        setContent {
            var showOnboarding by remember {
                mutableStateOf(!OnboardingSettings.isCompleted(this@MainActivity) || forceWatchSetup)
            }

            FoxTheme {
                if (showOnboarding) {
                    OnboardingNavigation(
                        startRoute = onboardingStartRoute,
                        watchOnlyMode = forceWatchSetup,
                        onFinish = {
                            OnboardingSettings.markCompleted(this@MainActivity)
                            showOnboarding = false
                        }
                    )
                } else {
                    DashboardScreen()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ONBOARDING_START_ROUTE = "onboarding_start_route"
        const val EXTRA_WATCH_ONLY_SETUP = "watch_only_setup"
    }
}
