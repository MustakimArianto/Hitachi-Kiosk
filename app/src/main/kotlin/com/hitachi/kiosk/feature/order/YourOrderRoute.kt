package com.hitachi.kiosk.feature.order

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitachi.kiosk.core.domain.model.PaymentMethod

/** SCR-10 wired to its ViewModel (FR-012, FR-013, FR-015; DEV-037, DEV-038). Each lambda is one edge of PRD §5.1. */
@Composable
fun YourOrderRoute(
    onBackToMenu: () -> Unit,
    onPay: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YourOrderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backToMenu by rememberUpdatedState(onBackToMenu)
    val pay by rememberUpdatedState(onPay)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                YourOrderEffect.BackToMenu -> backToMenu()
                is YourOrderEffect.Pay -> pay(effect.method)
            }
        }
    }
    YourOrderScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
