package com.hitachi.kiosk.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.inactivity.InactivityEffect
import com.hitachi.kiosk.inactivity.InactivityEvent
import com.hitachi.kiosk.inactivity.InactivityViewModel
import com.hitachi.kiosk.inactivity.StillThereHost
import com.hitachi.kiosk.inactivity.inactivityZone

/**
 * The root of the UI (technical-design §10.2): the design frame, the [KioskNavHost] and the SCR-13 inactivity overlay
 * above it (FR-023, technical-design §10.3). A root pointer observer reports every touch to the inactivity timer
 * without consuming it, and the back stack tells the timer where the kiosk is. It also returns to Sign in whenever
 * the session ends ([SessionViewModel]).
 */
@Composable
fun KioskRoot(
    onBrandMarkClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
    inactivityViewModel: InactivityViewModel = hiltViewModel(),
) {
    val stillThere by inactivityViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(sessionViewModel, navController) {
        // HTTP 401 or Log out: back to Sign in from wherever the kiosk is (FR-003, TR-NET-06). Splash routes by
        // itself, and Sign in is already there.
        sessionViewModel.signedOut.collect {
            inactivityViewModel.onEvent(InactivityEvent.SignedOut)
            val current = navController.currentDestination
            if (current?.hasRoute<SplashRoute>() != true && current?.hasRoute<SignInRoute>() != true) {
                navController.replaceStack(SignInRoute)
            }
        }
    }
    LaunchedEffect(inactivityViewModel, navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            inactivityViewModel.onEvent(InactivityEvent.DestinationChanged(entry.destination.inactivityZone()))
        }
    }
    LaunchedEffect(inactivityViewModel, navController) {
        inactivityViewModel.effect.collect { effect ->
            when (effect) {
                // Idle's ViewModel ends the order (FR-006 AC5).
                InactivityEffect.GoToIdle -> navController.popBackStack(IdleRoute, false)
            }
        }
    }
    DesignFrame(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(inactivityViewModel) {
                        // The Initial pass sees every touch before the screens do; nothing is consumed.
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                inactivityViewModel.onEvent(InactivityEvent.UserActivity)
                            }
                        }
                    },
        ) {
            StillThereHost(
                state = stillThere,
                onContinue = { inactivityViewModel.onEvent(InactivityEvent.Continue) },
                onStartOver = { inactivityViewModel.onEvent(InactivityEvent.StartOver) },
            ) {
                KioskNavHost(
                    navController = navController,
                    onInactivity = { inactivityViewModel.onEvent(InactivityEvent.ShowNow) },
                    onBrandMarkClick = onBrandMarkClick,
                )
            }
        }
    }
}
