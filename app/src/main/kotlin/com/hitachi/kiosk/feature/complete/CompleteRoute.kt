package com.hitachi.kiosk.feature.complete

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-12 wired to its ViewModel (FR-020). [onNavigateToIdle] is the one edge: "Done" and the 20 s timer. */
@Composable
fun CompleteRoute(
    onNavigateToIdle: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompleteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toIdle by rememberUpdatedState(onNavigateToIdle)
    LaunchedEffect(viewModel) {
        // The screen is composed: the paid payment is handed over and the pending order forgotten.
        viewModel.onEvent(CompleteEvent.Shown)
        viewModel.effect.collect { effect ->
            when (effect) {
                CompleteEffect.ToIdle -> toIdle()
            }
        }
    }
    CompleteScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
