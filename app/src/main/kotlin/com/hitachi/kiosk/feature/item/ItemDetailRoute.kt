package com.hitachi.kiosk.feature.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-08 wired to its ViewModel (FR-010). */
@Composable
fun ItemDetailRoute(
    onNavigateToMenu: () -> Unit,
    onNavigateToYourOrder: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toMenu by rememberUpdatedState(onNavigateToMenu)
    val toYourOrder by rememberUpdatedState(onNavigateToYourOrder)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ItemDetailEffect.NavigateToMenu -> toMenu()
                ItemDetailEffect.NavigateToYourOrder -> toYourOrder()
            }
        }
    }
    ItemDetailScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
