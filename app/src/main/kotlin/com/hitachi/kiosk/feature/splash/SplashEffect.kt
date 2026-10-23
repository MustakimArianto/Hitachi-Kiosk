package com.hitachi.kiosk.feature.splash

import com.hitachi.kiosk.navigation.StartDestination

/** One-off effects of SCR-01. Splash has no state of its own: its animation is fixed (FR-001). */
sealed interface SplashEffect {
    /** Startup routing is done (FR-001). */
    data class Navigate(
        val destination: StartDestination,
    ) : SplashEffect
}
