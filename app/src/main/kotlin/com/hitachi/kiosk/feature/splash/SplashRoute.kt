package com.hitachi.kiosk.feature.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hitachi.kiosk.navigation.StartDestination

/** SCR-01 wired to its ViewModel (FR-001). [onMarkClick] counts the Developer options taps (DEV-009). */
@Composable
fun SplashRoute(
    onNavigate: (StartDestination) -> Unit,
    onMarkClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val navigate by rememberUpdatedState(onNavigate)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SplashEffect.Navigate -> navigate(effect.destination)
            }
        }
    }
    SplashScreen(onMarkClick = onMarkClick, modifier = modifier)
}
