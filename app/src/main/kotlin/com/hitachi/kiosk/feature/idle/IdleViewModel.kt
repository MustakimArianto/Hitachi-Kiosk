package com.hitachi.kiosk.feature.idle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.recovery.RecoveryGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SCR-04 Idle (FR-006, DEV-024). Shows the cached merchant's name and promo images, ends the order whenever Idle comes
 * on screen (FR-006 AC5, BR-09; which also lets a held-back catalog sync apply, A-29), and leaves for Order type or
 * the staff PIN once per visit, so a double tap never opens two screens. A tap to order does nothing while the pending
 * order of a process death is being recovered, so a new order never shares the pending row with the old one (FR-016,
 * DEV-052).
 */
@HiltViewModel
class IdleViewModel
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        private val orderSessionHolder: OrderSessionHolder,
        private val recovery: RecoveryGate,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(IdleUiState())
        val state: StateFlow<IdleUiState> = mutableState.asStateFlow()

        private val effects = Channel<IdleEffect>(Channel.BUFFERED)
        val effect: Flow<IdleEffect> = effects.receiveAsFlow()

        private var leaving = false

        init {
            viewModelScope.launch {
                catalogRepository.catalog.collect { catalog -> mutableState.update { it.showing(catalog) } }
            }
        }

        fun onEvent(event: IdleEvent) {
            when (event) {
                IdleEvent.Shown -> shown()
                IdleEvent.TapToOrder -> if (!recovery.blocksNewOrder.value) leave(IdleEffect.StartOrder)
                IdleEvent.StaffHold -> leave(IdleEffect.OpenStaffSettings)
            }
        }

        private fun shown() {
            leaving = false
            orderSessionHolder.endOrder()
        }

        private fun leave(effect: IdleEffect) {
            if (leaving) return
            leaving = true
            effects.trySend(effect)
        }

        private fun IdleUiState.showing(catalog: Catalog?): IdleUiState =
            copy(
                brandName = catalog?.config?.brandName,
                slides = slidesOf(catalog?.config?.idlePromoImageUrls.orEmpty()),
            )

        internal companion object {
            /** One slide per promo image (a `null` entry is a tone slide); no images is one tone slide (TC-024). */
            fun slidesOf(imageUrls: List<String?>): List<IdleSlideUi> =
                if (imageUrls.isEmpty()) listOf(IdleSlideUi(imageUrl = null)) else imageUrls.map(::IdleSlideUi)
        }
    }
