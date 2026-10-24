package com.hitachi.kiosk.feature.upsell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-09 wired to its ViewModel (FR-014, DEV-039). Each lambda is one edge of PRD §5.1. */
@Composable
fun UpsellRoute(
    onNavigateToYourOrder: () -> Unit,
    onNavigateToMenu: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpsellViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toYourOrder by rememberUpdatedState(onNavigateToYourOrder)
    val toMenu by rememberUpdatedState(onNavigateToMenu)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                UpsellEffect.OpenYourOrder -> toYourOrder()
                UpsellEffect.BackToMenu -> toMenu()
            }
        }
    }
    UpsellScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
