package com.hitachi.kiosk.feature.activation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-02 wired to its ViewModel (FR-003). */
@Composable
fun SignInRoute(
    onNavigateToMerchants: () -> Unit,
    onNavigateToIdle: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toMerchants by rememberUpdatedState(onNavigateToMerchants)
    val toIdle by rememberUpdatedState(onNavigateToIdle)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SignInEffect.NavigateToMerchants -> toMerchants()
                SignInEffect.NavigateToIdle -> toIdle()
            }
        }
    }
    SignInScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
