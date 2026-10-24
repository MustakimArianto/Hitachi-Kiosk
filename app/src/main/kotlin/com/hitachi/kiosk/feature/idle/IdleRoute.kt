package com.hitachi.kiosk.feature.idle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-04 wired to its ViewModel (FR-006). */
@Composable
fun IdleRoute(
    onStartOrder: () -> Unit,
    onStaffEntry: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IdleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startOrder by rememberUpdatedState(onStartOrder)
    val staffEntry by rememberUpdatedState(onStaffEntry)
    LaunchedEffect(viewModel) {
        // Runs each time Idle comes back on screen, including a pop back from an order (FR-006 AC5).
        viewModel.onEvent(IdleEvent.Shown)
        viewModel.effect.collect { effect ->
            when (effect) {
                IdleEffect.StartOrder -> startOrder()
                IdleEffect.OpenStaffSettings -> staffEntry()
            }
        }
    }
    IdleScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
