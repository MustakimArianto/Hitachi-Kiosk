package com.hitachi.kiosk.feature.merchant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-03 wired to its ViewModel (FR-004). */
@Composable
fun MerchantsRoute(
    onNavigateToIdle: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MerchantsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toIdle by rememberUpdatedState(onNavigateToIdle)
    val toSignIn by rememberUpdatedState(onNavigateToSignIn)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                MerchantsEffect.NavigateToIdle -> toIdle()
                MerchantsEffect.NavigateToSignIn -> toSignIn()
            }
        }
    }
    MerchantsScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
