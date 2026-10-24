package com.hitachi.kiosk.feature.checkout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.devtools.CheckoutDebugTools

/**
 * SCR-11 wired to its ViewModel (FR-016–FR-019). Each lambda is one edge of PRD §5.1: back to Your order, on to
 * Complete once the backend says `PAID`, and Idle after "Cancel order".
 */
@Composable
fun CheckoutRoute(
    onNavigateToYourOrder: () -> Unit,
    onNavigateToComplete: () -> Unit,
    onNavigateToIdle: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toYourOrder by rememberUpdatedState(onNavigateToYourOrder)
    val toComplete by rememberUpdatedState(onNavigateToComplete)
    val toIdle by rememberUpdatedState(onNavigateToIdle)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                CheckoutEffect.ToYourOrder -> toYourOrder()
                CheckoutEffect.ToComplete -> toComplete()
                CheckoutEffect.ToIdle -> toIdle()
            }
        }
    }
    val tools = viewModel.simulateTools
    val method = state.stage.method
    val simulateBar = remember(tools, method) { tools?.let { simulateBarOf(it, method) } }
    CheckoutScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier, simulateBar = simulateBar)
}

/** The debug simulate bar as a slot of [CheckoutScreen]; release builds have no [CheckoutDebugTools]. */
private fun simulateBarOf(
    tools: CheckoutDebugTools,
    method: PaymentMethod,
): @Composable (Modifier) -> Unit = { modifier -> tools.SimulateBar(method, modifier) }
