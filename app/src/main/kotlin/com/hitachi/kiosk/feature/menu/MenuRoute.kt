package com.hitachi.kiosk.feature.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-06 wired to its ViewModel (FR-009, DEV-027, DEV-028). Each lambda is one edge of PRD §5.1. */
@Composable
fun MenuRoute(
    onOpenProduct: (productId: String) -> Unit,
    onOpenCombos: () -> Unit,
    onOpenBudgetPlan: () -> Unit,
    onChangeOrderType: () -> Unit,
    onCancel: () -> Unit,
    onViewOrder: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openProduct by rememberUpdatedState(onOpenProduct)
    val openCombos by rememberUpdatedState(onOpenCombos)
    val openBudgetPlan by rememberUpdatedState(onOpenBudgetPlan)
    val changeOrderType by rememberUpdatedState(onChangeOrderType)
    val cancel by rememberUpdatedState(onCancel)
    val viewOrder by rememberUpdatedState(onViewOrder)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MenuEffect.OpenProduct -> openProduct(effect.productId)
                MenuEffect.OpenCombos -> openCombos()
                MenuEffect.OpenBudgetPlan -> openBudgetPlan()
                MenuEffect.ChangeOrderType -> changeOrderType()
                MenuEffect.Cancel -> cancel()
                MenuEffect.ViewOrder -> viewOrder()
            }
        }
    }
    MenuScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
