package com.projectfox.foxoff.ui.onboarding

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.projectfox.foxoff.ui.onboarding.screens.*

@Composable
fun OnboardingNavigation(onFinish: () -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(onAnimationFinished = {
                navController.navigate("welcome") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        
        composable(
            "welcome",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) }
        ) {
            WelcomeScreen(onNext = { navController.navigate("permissions") })
        }

        composable(
            "permissions",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) }
        ) {
            PermissionScreen(onNext = { navController.navigate("active_hours") })
        }

        composable(
            "active_hours",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) }
        ) {
            ActiveHoursScreen(onNext = { navController.navigate("watch_detection") })
        }

        composable(
            "watch_detection",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) }
        ) {
            WatchDetectionScreen(onNext = { navController.navigate("tv_detection") })
        }

        composable(
            "tv_detection",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) }
        ) {
            TvDetectionScreen(
                onNext = { deviceId -> navController.navigate("tv_pairing/$deviceId") },
                onSkip = { navController.navigate("validation") }
            )
        }

        composable(
            "tv_pairing/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) }
        ) { backStackEntry ->
            TvPairingScreen(
                deviceId = backStackEntry.arguments?.getString("deviceId"),
                onNext = { navController.navigate("validation") }
            )
        }

        composable(
            "validation",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) }
        ) {
            EnvironmentSetupScreen(onNext = { navController.navigate("setup_complete") })
        }

        composable(
            "setup_complete",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(700)) }
        ) {
            SetupCompleteScreen(onFinish = onFinish)
        }
    }
}
