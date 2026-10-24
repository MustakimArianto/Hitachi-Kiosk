package com.hitachi.kiosk.feature.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLimit
import com.hitachi.kiosk.core.domain.cart.CartLine
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Totals
import com.hitachi.kiosk.core.domain.model.combo
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.model.toneOf
import com.hitachi.kiosk.core.domain.model.visiblePaymentMethods
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderState
import com.hitachi.kiosk.core.domain.order.ReviewIssues
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.domain.pricing.PromoHint
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
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

/** A removed line slides out over 460 ms before it leaves the list (`Payment.dc.html` `.row`, FR-012). */
private val REMOVE_SLIDE_DURATION = 460.milliseconds

/** How long "Maximum 20 per item" stays on screen (`Menu.dc.html`, scenario `maxQty`). */
private val MAX_TOAST_DURATION = 2_400.milliseconds

private const val STOP_TIMEOUT_MILLIS = 5_000L

private const val PERCENT = 100f

/**
 * SCR-10 Your order (FR-012, FR-013, FR-015, FR-016; DEV-037, DEV-037a, DEV-038, DEV-046, DEV-051). Shows the current
 * order priced by the [PricingEngine] with the device's rounding step, lets the customer change quantities and remove
 * lines, and offers the payment methods that both merchant config and Payment settings allow. Opening the screen
 * moves the order to Reviewing; "Add more" moves it back to Building (architecture §5.1).
 *
 * The review states come from the order session: E7's `PRICE_CHANGED` and `ITEM_UNAVAILABLE` answers land in
 * [OrderState.issues] (`SubmitOrder`, DEV-046), and "Review" and "Remove" clear them through the session, so the
 * banner, the line notes and the Pay label always follow the domain. The re-priced cart and the refreshed catalog
 * arrive the same way, so the totals shown are the ones E7 will accept.
 *
 * "Pay Rp X" is the Pay gate (DEV-051): disabled while an issue blocks or the kiosk is [Connectivity.OFFLINE]
 * ([Connectivity.CHECKING] does not block, as on the Menu). It emits one navigation to Checkout per visit to this
 * screen (TC-069); Checkout runs `SubmitOrder` and the payment, and handles an offline failure itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YourOrderViewModel
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        private val sessions: OrderSessionHolder,
        private val connectivity: ConnectivityMonitor,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val catalog: StateFlow<Catalog?> =
            catalogRepository.catalog.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val settings: StateFlow<KioskSettings?> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val local = MutableStateFlow(YourOrderLocalState())

        val state: StateFlow<YourOrderUiState> =
            combine(
                catalog,
                sessions.current.flatMapLatest { session -> session?.state ?: flowOf(null) },
                settings,
                connectivity.connectivity,
                local,
            ) { catalog, order, settings, connectivity, local ->
                yourOrderUiState(catalog, order, settings ?: KioskSettings(), connectivity, local)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), YourOrderUiState())

        private val effects = Channel<YourOrderEffect>(Channel.BUFFERED)
        val effect: Flow<YourOrderEffect> = effects.receiveAsFlow()

        private var toastJob: Job? = null

        /** A Pay navigation went out and this screen has not been shown again, so further taps change nothing. */
        private var payEmitted = false

        init {
            // Architecture §5.1: Upsell or Your order moves the order from Building to Reviewing.
            val session = sessions.current.value
            if (session != null) session.startReviewing() else KioskLog.w("your_order_without_order")
        }

        fun onEvent(event: YourOrderEvent) {
            when (event) {
                YourOrderEvent.AddMore, YourOrderEvent.BackToMenu -> {
                    backToMenu()
                }

                is YourOrderEvent.Increment -> {
                    increment(event.lineId)
                }

                is YourOrderEvent.Decrement -> {
                    removeOrDecrement(event.lineId, remove = false)
                }

                is YourOrderEvent.Remove -> {
                    removeOrDecrement(event.lineId, remove = true)
                }

                YourOrderEvent.ReviewPrices -> {
                    session()?.reviewPrices()
                }

                YourOrderEvent.RemoveUnavailable -> {
                    removeUnavailable()
                }

                YourOrderEvent.Shown -> {
                    payEmitted = false
                }

                YourOrderEvent.ToggleDetails -> {
                    local.update { it.copy(detailsOpen = !it.detailsOpen) }
                }

                is YourOrderEvent.SelectMethod -> {
                    local.update { it.copy(selectedMethod = event.method) }
                }

                YourOrderEvent.Pay -> {
                    pay()
                }
            }
        }

        private fun backToMenu() {
            // Architecture §5.1: "Add more" moves the order from Reviewing back to Building.
            sessions.current.value?.startBuilding()
            effects.trySend(YourOrderEffect.BackToMenu)
        }

        private fun increment(lineId: String) {
            val session = session() ?: return
            val result = session.increment(lineId)
            if (result is CartAddResult.LimitReached && result.limit == CartLimit.MAX_PER_LINE) showMaxToast()
            if (result is CartAddResult.Added && result.capped) showMaxToast()
        }

        /** "−" (removes at quantity 1), the trash icon or "Remove": a removed line slides out (FR-012). */
        private fun removeOrDecrement(
            lineId: String,
            remove: Boolean,
        ) {
            val session = session() ?: return
            val lines = session.state.value.cart.lines
            val index = lines.indexOfFirst { it.lineId == lineId }
            val line = lines.getOrNull(index) ?: return
            val removed = remove || line.quantity <= 1
            val unavailable = lineId in session.state.value.issues.unavailableLineIds
            val changed = if (remove) session.remove(lineId) else session.decrement(lineId)
            if (!changed || !removed) return
            slideOut(listOf(LeavingLine(index, line, unavailable)))
        }

        /** "Remove it" on the unavailable banner: every flagged line leaves the cart and slides out (FR-016). */
        private fun removeUnavailable() {
            val session = session() ?: return
            val state = session.state.value
            val flagged = state.issues.unavailableLineIds
            val leaving =
                state.cart.lines
                    .withIndex()
                    .filter { it.value.lineId in flagged }
                    .map { LeavingLine(it.index, it.value, unavailable = true) }
            if (session.removeUnavailable()) slideOut(leaving)
        }

        private fun slideOut(lines: List<LeavingLine>) {
            if (lines.isEmpty()) return
            local.update { it.copy(leaving = it.leaving + lines) }
            val ids = lines.mapTo(mutableSetOf()) { it.line.lineId }
            viewModelScope.launch {
                delay(REMOVE_SLIDE_DURATION)
                local.update { state -> state.copy(leaving = state.leaving.filterNot { it.line.lineId in ids }) }
            }
        }

        private fun pay() {
            // Built from the current values, so a method picked in the same frame counts.
            val ui =
                yourOrderUiState(
                    catalog.value,
                    sessions.current.value
                        ?.state
                        ?.value,
                    settings.value ?: KioskSettings(),
                    connectivity.connectivity.value,
                    local.value,
                )
            val method = ui.selectedMethod ?: return
            if (ui.pay !is PayUi.Ready) return
            // TC-069: one navigation per visit. Checkout's SubmitOrder moves the order past Reviewing, and the
            // session refuses a second submit, so a late tap can never send a second E7 either.
            if (payEmitted || sessions.current.value
                    ?.state
                    ?.value
                    ?.phase != OrderPhase.REVIEWING
            ) {
                return
            }
            payEmitted = true
            effects.trySend(YourOrderEffect.Pay(method))
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

        private fun session(): OrderSession? =
            sessions.current.value ?: run {
                KioskLog.w("your_order_edit_without_order")
                null
            }
    }

/**
 * A line removed from the cart that is still sliding out, at the [index] it had. [unavailable] keeps the "No longer
 * available" look while it leaves.
 */
internal data class LeavingLine(
    val index: Int,
    val line: CartLine,
    val unavailable: Boolean = false,
)

/** What only Your order holds. The review states live in the order session ([OrderState.issues]). */
internal data class YourOrderLocalState(
    val detailsOpen: Boolean = false,
    val selectedMethod: PaymentMethod? = null,
    val leaving: List<LeavingLine> = emptyList(),
    val showMaxToast: Boolean = false,
)

/**
 * Maps the catalog, the order, the settings and connectivity to what SCR-10 draws (FR-012, FR-013, FR-015). Pure, so
 * the screenshot tests build their boards from the real sample data. Unavailable lines are shown but not billed.
 */
internal fun yourOrderUiState(
    catalog: Catalog?,
    order: OrderState?,
    settings: KioskSettings,
    connectivity: Connectivity,
    local: YourOrderLocalState,
): YourOrderUiState {
    if (catalog == null) return YourOrderUiState()
    val menu = catalog.menu
    val config = catalog.config
    val review = order?.issues ?: ReviewIssues()
    val cartLines = order?.cart?.lines.orEmpty()
    val billable = cartLines.filterNot { it.lineId in review.unavailableLineIds }
    val rules = PricingRules.of(config, settings.roundingStep)
    val totals = PricingEngine.price(billable, rules)
    val methods = config.visiblePaymentMethods(settings)
    val blocks =
        PayBlocks(
            itemCount = billable.sumOf { it.quantity },
            offline = connectivity == Connectivity.OFFLINE,
            pricesChanged = review.pricesChanged,
            unavailableCount = cartLines.size - billable.size,
            noMethods = methods.isEmpty(),
        )
    val lines =
        cartLines.map { line ->
            menu.lineUi(
                line = line,
                previousUnitPrice = review.previousUnitPrices[line.lineId],
                unavailable = line.lineId in review.unavailableLineIds,
            )
        }
    return YourOrderUiState(
        loaded = true,
        orderType = order?.orderType ?: YourOrderUiState().orderType,
        itemCount = blocks.itemCount,
        planTag = order?.planTag?.let { PlanTagUi(it.adults, it.children, IdrFormatter.format(it.budget)) },
        banner = blocks.banner { PricingEngine.promoHint(totals.subtotal, rules) },
        lines =
            lines.withLeaving(
                local.leaving.map {
                    it.index to menu.lineUi(it.line, unavailable = it.unavailable).copy(leaving = true)
                },
            ),
        empty = cartLines.isEmpty() && local.leaving.isEmpty(),
        totals = config.totalsUi(totals),
        detailsOpen = local.detailsOpen,
        methods = methods,
        selectedMethod = local.selectedMethod?.takeIf { it in methods } ?: methods.firstOrNull(),
        pay = blocks.pay(IdrFormatter.format(totals.total)),
        showMaxToast = local.showMaxToast,
    )
}

/** What can stop Pay on SCR-10, and the banner it shows (`Payment.dc.html` `ban` and `blockLabel`, FR-015). */
private data class PayBlocks(
    val itemCount: Int,
    val offline: Boolean,
    val pricesChanged: Boolean,
    val unavailableCount: Int,
    val noMethods: Boolean,
) {
    /** One banner, by priority: offline, prices changed, item unavailable, then the promo nudge (never blocking). */
    fun banner(promoHint: () -> PromoHint?): OrderBannerUi? =
        when {
            offline -> {
                OrderBannerUi.Offline
            }

            pricesChanged -> {
                OrderBannerUi.PriceChanged
            }

            unavailableCount > 0 -> {
                OrderBannerUi.ItemUnavailable(unavailableCount)
            }

            else -> {
                promoHint()?.let { hint ->
                    OrderBannerUi.PromoNudge(
                        remaining = IdrFormatter.format(hint.remaining),
                        discount = IdrFormatter.format(hint.discount),
                        progress = hint.progressPercent / PERCENT,
                    )
                }
            }
        }

    /** "Pay [total]", or the first reason it is disabled: empty, offline, prices, unavailable, no methods. */
    fun pay(total: String): PayUi =
        when {
            itemCount == 0 -> PayUi.Blocked(PayBlock.EMPTY)
            offline -> PayUi.Blocked(PayBlock.OFFLINE)
            pricesChanged -> PayUi.Blocked(PayBlock.PRICE_CHANGED)
            unavailableCount > 0 -> PayUi.Blocked(PayBlock.ITEM_UNAVAILABLE)
            noMethods -> PayUi.Blocked(PayBlock.NO_METHODS)
            else -> PayUi.Ready(total)
        }
}

/** The Details rows of [totals], with the promo and tax labels from config (FR-013 display). */
private fun MerchantConfig.totalsUi(totals: Totals): OrderTotalsUi =
    OrderTotalsUi(
        subtotal = IdrFormatter.format(totals.subtotal),
        discount = totals.discount.takeIf { it > Rupiah.Zero }?.let(IdrFormatter::format),
        promoLabel =
            promos
                .firstOrNull { it.id == totals.promoId }
                ?.label
                ?.resolve()
                ?.takeIf(String::isNotBlank),
        tax = IdrFormatter.format(totals.tax),
        taxLabel =
            tax.label
                ?.resolve()
                ?.takeIf(String::isNotBlank),
        rounding = totals.rounding.takeIf { it > Rupiah.Zero }?.let(IdrFormatter::format),
        total = IdrFormatter.format(totals.total),
    )

/** [this] with each leaving line put back at the index it had, in the order the lines left. */
private fun List<OrderLineUi>.withLeaving(leaving: List<Pair<Int, OrderLineUi>>): List<OrderLineUi> {
    val result = toMutableList()
    leaving.sortedBy { it.first }.forEach { (index, line) -> result.add(index.coerceIn(0, result.size), line) }
    return result
}

private fun Menu.lineUi(
    line: CartLine,
    previousUnitPrice: Rupiah? = null,
    unavailable: Boolean = false,
): OrderLineUi {
    val photo =
        when (val item = line.item) {
            is CartItem.ProductItem -> {
                product(item.productId).let { LinePhoto(it?.name?.resolve(), it?.imageUrl, it?.let(::toneOf)) }
            }

            is CartItem.ComboItem -> {
                combo(item.comboId).let { LinePhoto(it?.name?.resolve(), it?.imageUrl, it?.tone) }
            }
        }
    return OrderLineUi(
        lineId = line.lineId,
        name = photo.name.orEmpty(),
        choices = line.choices,
        imageUrl = photo.imageUrl,
        tone = photo.tone,
        quantity = line.quantity,
        total = IdrFormatter.format(line.lineTotal),
        previousTotal = previousUnitPrice?.let { IdrFormatter.format(it * line.quantity) },
        unavailable = unavailable,
    )
}

/** The name, photo and tone of a line's product or combo; `null`s when the menu no longer has it. */
private data class LinePhoto(
    val name: String?,
    val imageUrl: String?,
    val tone: String?,
)
