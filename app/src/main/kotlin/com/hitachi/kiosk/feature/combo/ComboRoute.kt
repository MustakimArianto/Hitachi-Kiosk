package com.hitachi.kiosk.feature.combo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-07 wired to its ViewModel (FR-011). */
@Composable
fun ComboRoute(
    onNavigateToMenu: () -> Unit,
    onNavigateToYourOrder: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComboViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toMenu by rememberUpdatedState(onNavigateToMenu)
    val toYourOrder by rememberUpdatedState(onNavigateToYourOrder)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ComboEffect.NavigateToMenu -> toMenu()
                ComboEffect.NavigateToYourOrder -> toYourOrder()
            }
        }
    }
    ComboScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
