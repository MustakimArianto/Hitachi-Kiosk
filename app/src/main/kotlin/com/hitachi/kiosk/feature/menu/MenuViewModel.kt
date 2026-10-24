package com.hitachi.kiosk.feature.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.cart.Cart
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLimit
import com.hitachi.kiosk.core.domain.cart.CartLine
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.availableCombos
import com.hitachi.kiosk.core.domain.model.combo
import com.hitachi.kiosk.core.domain.model.featuredProducts
import com.hitachi.kiosk.core.domain.model.needsChoice
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.model.sortedCategories
import com.hitachi.kiosk.core.domain.model.toneOf
import com.hitachi.kiosk.core.domain.model.visibleProducts
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderState
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.core.domain.time.DayPart
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
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaInstant

/** How long the "+" shows the accent check after a quick add (`Menu.dc.html`, FR-009). */
private val ADDED_CHECK_DURATION = 1_400.milliseconds

/** How long "Maximum 20 per item" stays on screen (`Menu.dc.html`, scenario `maxQty`). */
private val MAX_TOAST_DURATION = 2_400.milliseconds

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * SCR-06 Menu (FR-009, DEV-027) and quick add (DEV-028). Browses the cached catalog in memory, so a category switch
 * is a pure filter (TC-034), and adds one unit with the default options to the current order (FR-009 AC2). Offline,
 * adds and "View order" do nothing (FR-009 AC3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MenuViewModel
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        private val sessions: OrderSessionHolder,
        private val connectivity: ConnectivityMonitor,
        private val clock: Clock,
        private val zone: ZoneId,
    ) : ViewModel() {
        private val catalog: StateFlow<Catalog?> =
            catalogRepository.catalog.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val local = MutableStateFlow(MenuLocalState())

        val state: StateFlow<MenuUiState> =
            combine(
                catalog,
                sessions.current.flatMapLatest { session -> session?.state ?: flowOf(null) },
                connectivity.connectivity,
                local,
            ) { catalog, order, connectivity, local ->
                menuUiState(catalog, order, connectivity, local, dayPart())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MenuUiState())

        private val effects = Channel<MenuEffect>(Channel.BUFFERED)
        val effect: Flow<MenuEffect> = effects.receiveAsFlow()

        private var checkJob: Job? = null
        private var toastJob: Job? = null

        init {
            // Architecture §5.1: reaching the Menu moves the order from TypeChosen to Building.
            val session = sessions.current.value
            if (session != null) session.startBuilding() else KioskLog.w("menu_without_order")
        }

        fun onEvent(event: MenuEvent) {
            when (event) {
                is MenuEvent.Select -> local.update { it.copy(selection = event.selection) }
                is MenuEvent.QuickAdd -> quickAdd(event.productId)
                is MenuEvent.OpenProduct -> effects.trySend(MenuEffect.OpenProduct(event.productId))
                MenuEvent.OpenCombos -> effects.trySend(MenuEffect.OpenCombos)
                MenuEvent.OpenBudgetPlan -> effects.trySend(MenuEffect.OpenBudgetPlan)
                MenuEvent.ChangeOrderType -> effects.trySend(MenuEffect.ChangeOrderType)
                MenuEvent.Cancel -> effects.trySend(MenuEffect.Cancel)
                MenuEvent.ViewOrder -> if (!isOffline()) effects.trySend(MenuEffect.ViewOrder)
            }
        }

        private fun quickAdd(productId: String) {
            if (isOffline()) return
            val menu = catalog.value?.menu ?: return
            // A product that needs a choice opens Item detail instead (A-08); a sold-out one is not on screen.
            val product = menu.product(productId)?.takeIf { it.available && !menu.needsChoice(it) } ?: return
            val session = sessions.current.value
            if (session == null) {
                KioskLog.w("menu_quick_add_without_order")
                return
            }
            val custom = ProductCustomization.defaults(product, menu)
            when (val result = session.add(custom.toCartItem(), 1, custom.unitPrice, custom.choices())) {
                is CartAddResult.Added -> {
                    if (result.capped) showMaxToast()
                    showAdded(productId)
                }

                is CartAddResult.LimitReached -> {
                    when (result.limit) {
                        CartLimit.MAX_PER_LINE -> showMaxToast()
                        CartLimit.MAX_LINES -> KioskLog.i("menu_quick_add_max_lines")
                    }
                }
            }
        }

        private fun showAdded(productId: String) {
            local.update {
                it.copy(justAddedProductId = productId, addSerial = it.addSerial + 1, lastAddedProductId = productId)
            }
            checkJob?.cancel()
            checkJob =
                viewModelScope.launch {
                    delay(ADDED_CHECK_DURATION)
                    local.update { it.copy(justAddedProductId = null) }
                }
        }

        private fun showMaxToast() {
            local.update { it.copy(showMaxToast = true) }
            toastJob?.cancel()
            toastJob =
                viewModelScope.launch {
                    delay(MAX_TOAST_DURATION)
                    local.update { it.copy(showMaxToast = false) }
                }
        }

        private fun isOffline(): Boolean = connectivity.connectivity.value == Connectivity.OFFLINE

        private fun dayPart(): DayPart =
            DayPart.at(
                clock
                    .now()
                    .toJavaInstant()
                    .atZone(zone)
                    .hour,
            )
    }

/** What only the Menu screen holds: the active pill and the quick-add feedback. */
internal data class MenuLocalState(
    val selection: MenuSelection = MenuSelection.Popular,
    val justAddedProductId: String? = null,
    val showMaxToast: Boolean = false,
    val addSerial: Int = 0,
    val lastAddedProductId: String? = null,
)

/**
 * Maps the catalog, the order and connectivity to what SCR-06 draws (FR-009). Pure, so a category switch costs one
 * in-memory filter (TC-034), and the screenshot tests build their boards from the real sample data.
 */
internal fun menuUiState(
    catalog: Catalog?,
    order: OrderState?,
    connectivity: Connectivity,
    local: MenuLocalState,
    dayPart: DayPart,
): MenuUiState {
    val menu = catalog?.menu
    val cart = order?.cart ?: Cart()
    val selection = local.selection.takeIf { menu != null && menu.has(it) } ?: MenuSelection.Popular
    val products =
        when {
            menu == null -> emptyList()
            selection is MenuSelection.Category -> menu.visibleProducts(selection.id)
            else -> menu.featuredProducts()
        }
    return MenuUiState(
        loaded = true,
        brandName = catalog?.config?.brandName.orEmpty(),
        orderType = order?.orderType ?: MenuUiState().orderType,
        dayPart = dayPart,
        pills = menu?.pills(selection) ?: listOf(MenuPillUi.Popular(active = true)),
        selection = selection,
        showBudgetPlan = selection == MenuSelection.Popular && catalog?.config?.budgetPlanner?.enabled == true,
        sectionTitle =
            (selection as? MenuSelection.Category)?.let { category ->
                menu
                    ?.categories
                    ?.firstOrNull { it.id == category.id }
                    ?.name
                    ?.resolve()
            },
        products = menu?.let { products.map { product -> it.card(product, cart, local) } }.orEmpty(),
        dock =
            CartDockUi(
                itemCount = cart.itemCount,
                total = IdrFormatter.format(cart.subtotal),
                thumbnails =
                    cart.lines
                        .takeLast(DOCK_THUMBNAILS)
                        .asReversed()
                        .map { it.thumbnail(menu) },
            ),
        offline = connectivity == Connectivity.OFFLINE,
        showMaxToast = local.showMaxToast,
        addSerial = local.addSerial,
        lastAddedProductId = local.lastAddedProductId,
    )
}

/** The dock shows the last three lines (FR-009). */
private const val DOCK_THUMBNAILS = 3

private fun Menu.has(selection: MenuSelection): Boolean =
    when (selection) {
        MenuSelection.Popular -> true
        is MenuSelection.Category -> categories.any { it.id == selection.id }
    }

/** "Popular", then the categories and the "Meals" link in `sort` order (FR-009). */
private fun Menu.pills(selection: MenuSelection): List<MenuPillUi> {
    val categories =
        sortedCategories().map { category ->
            category.sort to
                MenuPillUi.Category(
                    id = category.id,
                    name = category.name.resolve(),
                    active = selection == MenuSelection.Category(category.id),
                )
        }
    // The Meals link is hidden when the menu has no combos to open (FR-011).
    val meals =
        combosCategory
            ?.takeIf { availableCombos().isNotEmpty() }
            ?.let { listOf(it.sort to MenuPillUi.Meals(it.name.resolve())) }
            .orEmpty()
    return listOf(MenuPillUi.Popular(active = selection == MenuSelection.Popular)) +
        (categories + meals).sortedBy { it.first }.map { it.second }
}

private fun Menu.card(
    product: Product,
    cart: Cart,
    local: MenuLocalState,
): MenuProductUi {
    val needsChoice = needsChoice(product)
    val quickAddItem = if (needsChoice) null else ProductCustomization.defaults(product, this).toCartItem()
    return MenuProductUi(
        id = product.id,
        name = product.name.resolve(),
        price = IdrFormatter.format(product.price),
        imageUrl = product.imageUrl,
        tone = toneOf(product),
        badge = product.badge?.let { MenuBadgeUi(it.type, it.label?.resolve()?.takeIf(String::isNotBlank)) },
        needsChoice = needsChoice,
        justAdded = local.justAddedProductId == product.id,
        atMax = quickAddItem != null && cart.quantityOf(quickAddItem) >= Cart.MAX_PER_LINE,
    )
}

private fun CartLine.thumbnail(menu: Menu?): DockThumbnailUi =
    when (val item = item) {
        is CartItem.ProductItem -> {
            val product = menu?.product(item.productId)
            DockThumbnailUi(lineId, product?.imageUrl, product?.let { menu.toneOf(it) })
        }

        is CartItem.ComboItem -> {
            val combo = menu?.combo(item.comboId)
            DockThumbnailUi(lineId, combo?.imageUrl, combo?.tone)
        }
    }
