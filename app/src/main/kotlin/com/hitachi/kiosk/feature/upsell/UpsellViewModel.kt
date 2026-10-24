package com.hitachi.kiosk.feature.upsell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.cart.Cart
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLine
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.cart.Upsell
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.combo
import com.hitachi.kiosk.core.domain.model.needsChoice
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.model.toneOf
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderState
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** How long the "+" shows the accent check after a quick add (`Upsell.dc.html`). */
private val ADDED_CHECK_DURATION = 1_400.milliseconds

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * SCR-09 Upsell (FR-014, DEV-039). Opening it marks the upsell as shown, so it is offered at most once per order
 * (FR-014 AC2), and moves the order to Reviewing (architecture §5.1). The suggestions are picked once, from the cart
 * as it was when the screen opened, so a card that was just added keeps its place and shows "N added". A quick add
 * puts one unit with the default options in the cart (FR-014 AC3), exactly like the Menu's "+".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UpsellViewModel
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        private val sessions: OrderSessionHolder,
    ) : ViewModel() {
        private val catalog: StateFlow<Catalog?> =
            catalogRepository.catalog.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** The cart when SCR-09 opened: what the suggestions leave out (FR-014 rule). */
        private val openingCart: Cart =
            sessions.current.value
                ?.state
                ?.value
                ?.cart ?: Cart()

        private val local = MutableStateFlow(UpsellLocalState())

        val state: StateFlow<UpsellUiState> =
            combine(
                catalog,
                sessions.current.flatMapLatest { session -> session?.state ?: flowOf(null) },
                local,
            ) { catalog, order, local ->
                upsellUiState(catalog, order, local)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UpsellUiState())

        private val effects = Channel<UpsellEffect>(Channel.BUFFERED)
        val effect: Flow<UpsellEffect> = effects.receiveAsFlow()

        private var checkJob: Job? = null

        init {
            val session = sessions.current.value
            if (session != null) {
                session.markUpsellShown()
                session.startReviewing()
            } else {
                KioskLog.w("upsell_without_order")
            }
            viewModelScope.launch {
                catalog.collect { catalog ->
                    if (catalog != null && local.value.suggestionIds == null) {
                        val ids = Upsell.suggestions(catalog.menu, catalog.config.upsell, openingCart).map { it.id }
                        local.update { it.copy(suggestionIds = ids) }
                    }
                }
            }
        }

        fun onEvent(event: UpsellEvent) {
            when (event) {
                is UpsellEvent.QuickAdd -> {
                    quickAdd(event.productId)
                }

                UpsellEvent.NoThanks, UpsellEvent.Continue -> {
                    effects.trySend(UpsellEffect.OpenYourOrder)
                }

                UpsellEvent.BackToMenu -> {
                    // Back on the Menu the order is being built again (architecture §5.1: Reviewing → Building).
                    sessions.current.value?.startBuilding()
                    effects.trySend(UpsellEffect.BackToMenu)
                }
            }
        }

        private fun quickAdd(productId: String) {
            if (productId !in local.value.suggestionIds.orEmpty()) return
            val menu = catalog.value?.menu ?: return
            val product = menu.product(productId)?.takeIf { it.available && !menu.needsChoice(it) } ?: return
            val session = sessions.current.value
            if (session == null) {
                KioskLog.w("upsell_quick_add_without_order")
                return
            }
            val custom = ProductCustomization.defaults(product, menu)
            when (val result = session.add(custom.toCartItem(), 1, custom.unitPrice, custom.choices())) {
                is CartAddResult.Added -> showAdded(productId)
                is CartAddResult.LimitReached -> KioskLog.i("upsell_quick_add_refused", "limit" to result.limit.name)
            }
        }

        private fun showAdded(productId: String) {
            local.update { it.copy(justAddedProductId = productId, addSerial = it.addSerial + 1) }
            checkJob?.cancel()
            checkJob =
                viewModelScope.launch {
                    delay(ADDED_CHECK_DURATION)
                    local.update { it.copy(justAddedProductId = null) }
                }
        }
    }

/**
 * What only SCR-09 holds.
 *
 * @property suggestionIds the products picked when the screen opened, in display order; `null` until the catalog is
 *   read.
 */
internal data class UpsellLocalState(
    val suggestionIds: List<String>? = null,
    val justAddedProductId: String? = null,
    val addSerial: Int = 0,
)

/** The strip shows the last three lines (`Upsell.dc.html`). */
private const val STRIP_THUMBNAILS = 3

/**
 * Maps the catalog, the order and the settings to what SCR-09 draws (FR-014). Pure, so the screenshot tests build
 * their boards from the real sample data.
 */
internal fun upsellUiState(
    catalog: Catalog?,
    order: OrderState?,
    local: UpsellLocalState,
): UpsellUiState {
    val ids = local.suggestionIds
    if (catalog == null || ids == null) return UpsellUiState(orderType = order?.orderType ?: UpsellUiState().orderType)
    val menu = catalog.menu
    val cart = order?.cart ?: Cart()
    return UpsellUiState(
        loaded = true,
        orderType = order?.orderType ?: UpsellUiState().orderType,
        suggestions = ids.mapNotNull { menu.product(it) }.map { menu.card(it, cart, local) },
        itemCount = cart.itemCount,
        subtotal = IdrFormatter.format(cart.subtotal),
        thumbnails =
            cart.lines
                .takeLast(STRIP_THUMBNAILS)
                .asReversed()
                .map { it.thumbnail(menu) },
        addSerial = local.addSerial,
    )
}

private fun Menu.card(
    product: Product,
    cart: Cart,
    local: UpsellLocalState,
): UpsellSuggestionUi =
    UpsellSuggestionUi(
        id = product.id,
        name = product.name.resolve(),
        role = product.role,
        description = product.description?.resolve()?.takeIf(String::isNotBlank),
        kcal = product.kcal,
        price = IdrFormatter.format(product.price),
        imageUrl = product.imageUrl,
        tone = toneOf(product),
        addedQuantity =
            cart.lines
                .filter { (it.item as? CartItem.ProductItem)?.productId == product.id }
                .sumOf { it.quantity },
        justAdded = local.justAddedProductId == product.id,
    )

private fun CartLine.thumbnail(menu: Menu): UpsellThumbnailUi =
    when (val item = item) {
        is CartItem.ProductItem -> {
            val product = menu.product(item.productId)
            UpsellThumbnailUi(lineId, product?.imageUrl, product?.let { menu.toneOf(it) })
        }

        is CartItem.ComboItem -> {
            val combo = menu.combo(item.comboId)
            UpsellThumbnailUi(lineId, combo?.imageUrl, combo?.tone)
        }
    }
