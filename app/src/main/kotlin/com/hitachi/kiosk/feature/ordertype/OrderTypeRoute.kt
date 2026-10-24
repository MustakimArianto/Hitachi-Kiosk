package com.hitachi.kiosk.feature.ordertype

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-05 wired to its ViewModel (FR-007). */
@Composable
fun OrderTypeRoute(
    onNavigateToMenu: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderTypeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toMenu by rememberUpdatedState(onNavigateToMenu)
    LaunchedEffect(viewModel) {
        // Runs each time the screen comes back, e.g. from Menu "Change": the cards are fresh again.
        viewModel.onEvent(OrderTypeEvent.Shown)
        viewModel.effect.collect { effect ->
            when (effect) {
                OrderTypeEffect.NavigateToMenu -> toMenu()
            }
        }
    }
    OrderTypeScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
