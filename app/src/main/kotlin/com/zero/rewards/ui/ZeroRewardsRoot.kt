package com.zero.rewards.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zero.rewards.AppContainer

private const val SLIDE_MS = 420

@Composable
fun ZeroRewardsRoot(container: AppContainer) {
    val nav = rememberNavController()
    Box(Modifier.fillMaxSize()) {
    // Shared-axis motion: the new screen glides in from the side as the old one recedes.
    NavHost(
        nav,
        startDestination = "dashboard",
        enterTransition = {
            fadeIn(tween(300, delayMillis = 60)) +
                slideInHorizontally(tween(SLIDE_MS, easing = ZeroMotion.EmphasizedDecelerate)) { it / 10 }
        },
        exitTransition = {
            fadeOut(tween(160, easing = ZeroMotion.EmphasizedAccelerate)) +
                slideOutHorizontally(tween(SLIDE_MS, easing = ZeroMotion.Emphasized)) { -it / 20 }
        },
        popEnterTransition = {
            fadeIn(tween(300, delayMillis = 60)) +
                slideInHorizontally(tween(SLIDE_MS, easing = ZeroMotion.EmphasizedDecelerate)) { -it / 20 }
        },
        popExitTransition = {
            fadeOut(tween(160, easing = ZeroMotion.EmphasizedAccelerate)) +
                slideOutHorizontally(tween(SLIDE_MS, easing = ZeroMotion.Emphasized)) { it / 10 }
        },
    ) {
        composable("dashboard") {
            DashboardScreen(
                container = container,
                onAddAccount = { nav.navigate("add-account") },
                onOpenSettings = { id -> nav.navigate("settings/$id") },
                onOpenAppearance = { nav.navigate("appearance") },
                onOpenReminders = { nav.navigate("reminders") },
                onOpenCredits = { nav.navigate("credits") },
            )
        }
        composable("appearance") {
            AppearanceScreen(onDone = { nav.popBackStack() })
        }
        composable("reminders") {
            StreakRemindersScreen(container = container, onDone = { nav.popBackStack() })
        }
        composable("credits") {
            CreditsScreen(onDone = { nav.popBackStack() })
        }
        composable("add-account") {
            AddAccountScreen(container = container, onDone = { nav.popBackStack() })
        }
        composable("settings/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            AccountSettingsScreen(
                container = container,
                accountId = id,
                onDone = { nav.popBackStack() },
            )
        }
    }
    StreakPromptHost(container = container)
    }
}
