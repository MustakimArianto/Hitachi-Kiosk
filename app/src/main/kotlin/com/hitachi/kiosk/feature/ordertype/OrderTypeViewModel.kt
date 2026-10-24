package com.hitachi.kiosk.feature.ordertype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SCR-05 Order type (FR-007, DEV-025). Shows the order types the merchant offers; the first pick starts the order (or,
 * back from Menu "Change", changes its type and keeps the cart, TC-028), lets the selection animation play and goes to
 * Menu after [KioskMotion.OrderType.ADVANCE_MILLIS] (FR-007 AC1). Later taps are ignored (FR-007 AC2). The advance
 * keeps its 950 ms under reduce motion: AC1 is about timing, not animation.
 */
@HiltViewModel
class OrderTypeViewModel
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        private val orderSessionHolder: OrderSessionHolder,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(OrderTypeUiState())
        val state: StateFlow<OrderTypeUiState> = mutableState.asStateFlow()

        private val effects = Channel<OrderTypeEffect>(Channel.BUFFERED)
        val effect: Flow<OrderTypeEffect> = effects.receiveAsFlow()

        private var catalog: Catalog? = null

        init {
            viewModelScope.launch {
                catalogRepository.catalog.collect { latest ->
                    catalog = latest
                    mutableState.update { it.showing(latest?.config) }
                }
            }
        }

        fun onEvent(event: OrderTypeEvent) {
            when (event) {
                OrderTypeEvent.Shown -> mutableState.update { it.copy(picked = null) }
                is OrderTypeEvent.Pick -> pick(event.orderType)
            }
        }

        private fun pick(orderType: OrderType) {
            val current = catalog ?: return
            val state = mutableState.value
            if (state.picked != null || orderType !in state.options) return
            mutableState.update { it.copy(picked = orderType) }
            orderSessionHolder.chooseOrderType(orderType, current.menu.menuVersion, current.config.configVersion)
            viewModelScope.launch {
                delay(KioskMotion.OrderType.ADVANCE_MILLIS.toLong())
                effects.send(OrderTypeEffect.NavigateToMenu)
            }
        }

        private fun OrderTypeUiState.showing(config: MerchantConfig?): OrderTypeUiState {
            val options = config?.let(::offeredTypes).orEmpty()
            return copy(
                brandName = config?.brandName.orEmpty(),
                options = options,
                unavailable = if (options.size == 1) OrderType.entries.first { it !in options } else null,
            )
        }

        internal companion object {
            /**
             * The cards to show, Dine in first: the types in the config. The Kiosk behaviour toggles of FR-026 (0.7.0)
             * will be one more intersection here.
             */
            fun offeredTypes(config: MerchantConfig): List<OrderType> =
                OrderType.entries.filter(config.orderTypes::contains)
        }
    }
