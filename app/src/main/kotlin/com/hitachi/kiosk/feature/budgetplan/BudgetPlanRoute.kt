package com.hitachi.kiosk.feature.budgetplan

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * SCR-19 wired to its ViewModel (FR-032). System Back follows the back pill: it closes an open sheet or keypad, then
 * returns from step 2 to step 1, then from step 1 to the Menu (architecture §11).
 */
@Composable
fun BudgetPlanRoute(
    onNavigateToMenu: () -> Unit,
    onNavigateToYourOrder: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toMenu by rememberUpdatedState(onNavigateToMenu)
    val toYourOrder by rememberUpdatedState(onNavigateToYourOrder)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                BudgetPlanEffect.NavigateToMenu -> toMenu()
                BudgetPlanEffect.NavigateToYourOrder -> toYourOrder()
            }
        }
    }
    BackHandler { viewModel.onEvent(BudgetPlanEvent.Back) }
    BudgetPlanScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
