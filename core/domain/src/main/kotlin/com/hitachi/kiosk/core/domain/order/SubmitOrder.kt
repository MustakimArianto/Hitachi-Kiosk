package com.hitachi.kiosk.core.domain.order

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLine
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.CreatedOrder
import com.hitachi.kiosk.core.domain.model.OrderLine
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.Totals
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/** What "Pay" on Your order led to (FR-016). */
sealed interface SubmitResult {
    /** E7 created the order and the session is Paying: Checkout starts the payment (FR-017, FR-018). */
    data class Created(
        val order: CreatedOrder,
    ) : SubmitResult

    /**
     * E7 answered `PRICE_CHANGED`: the catalog was refreshed, the cart re-priced and the session is back on Reviewing
     * with the prices to review ([ReviewIssues.pricesChanged]). No payment was started (FR-016 AC3).
     */
    data object PriceChanged : SubmitResult

    /** E7 answered `ITEM_UNAVAILABLE`: the session is back on Reviewing with [lineIds] flagged (FR-016). */
    data class ItemUnavailable(
        val lineIds: Set<String>,
    ) : SubmitResult

    /**
     * E7 or the catalog refresh after `PRICE_CHANGED` failed (network, server, rate limit after the retries,
     * `MERCHANT_CLOSED`, a bad answer), or there was no catalog to build the order from. The session is back on
     * Reviewing with its cart and key unchanged, so "Try again" resends the same body with the same key (FR-016).
     */
    data class Failed(
        val error: DomainError,
    ) : SubmitResult

    /** The tap changed nothing: no order, one is already submitting, or Pay is blocked (FR-016 AC1). */
    data object Ignored : SubmitResult
}

/**
 * "Pay" on Your order, the order half (FR-016, DEV-045, DEV-046). A use case because it combines the session, the
 * pricing rules, the catalog, the settings and the repository:
 * 1. [OrderSession.submit] moves Reviewing → Submitting and settles the request and its `Idempotency-Key` atomically,
 *    so 10 rapid taps send exactly one E7 (AC1) and a retry of the same body reuses the key (technical-design §5.3).
 * 2. [OrderRepository.create] sends E7 (with its retries) and saves the pending order.
 *    The backend total must equal the total the kiosk sent: another total is never paid (cancelled with E8, Failed).
 * 3. The session then moves on: Paying on success, Reviewing on `PRICE_CHANGED` (after a catalog refresh and a
 *    re-price, the one exception to A-29), on `ITEM_UNAVAILABLE` (lines flagged) and on any other failure.
 *
 * It never starts a payment; Checkout does that from the [SubmitResult.Created] order (DEV-047).
 */
class SubmitOrder(
    private val sessions: OrderSessionHolder,
    private val catalogs: CatalogRepository,
    private val catalogSync: CatalogSyncRepository,
    private val settings: SettingsRepository,
    private val orders: OrderRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(): SubmitResult {
        val session = sessions.current.value ?: return SubmitResult.Ignored
        if (session.state.value.phase != OrderPhase.REVIEWING) return SubmitResult.Ignored
        val catalog = catalogs.catalog.first() ?: return SubmitResult.Failed(DomainError.Unknown)
        val rules = PricingRules.of(catalog.config, settings.settings.first().roundingStep)
        val submission =
            session.submit(
                buildRequest = { it.toRequest(catalog.config.merchantId, rules) },
                newKey = ids::newIdempotencyKey,
            ) ?: return SubmitResult.Ignored
        return try {
            when (val outcome = orders.create(submission.request, submission.key)) {
                is Outcome.Success -> created(session, outcome.value, submission.request.clientTotals)
                is Outcome.Failure -> rejected(session, catalog, outcome.error)
            }
        } finally {
            // A cancelled or crashed submission must not leave the order locked in Submitting; the key is kept.
            if (session.state.value.phase == OrderPhase.SUBMITTING) session.submissionFailed()
        }
    }

    private suspend fun created(
        session: OrderSession,
        order: CreatedOrder,
        sent: Totals,
    ): SubmitResult {
        if (order.totals.total != sent.total) return totalDiffers(session, order, sent)
        session.orderCreated()
        KioskLog.i("order_created", "menuVersion" to session.state.value.menuVersion)
        return SubmitResult.Created(order)
    }

    /**
     * The backend order has another total than the kiosk showed (FR-016, TR-INT-04): nothing is paid. The order is
     * cancelled (E8; if that fails the saved pending order is cancelled by the recovery) and the session returns to
     * Reviewing, as for any failure with no payment.
     */
    private suspend fun totalDiffers(
        session: OrderSession,
        order: CreatedOrder,
        sent: Totals,
    ): SubmitResult {
        KioskLog.w(
            "order_total_differs",
            "sent" to sent.total.value,
            "backend" to order.totals.total.value,
        )
        orders.cancel(order.orderId, CancelOrderReason.PAYMENT_FAILED)
        session.submissionFailed()
        return SubmitResult.Failed(DomainError.Unknown)
    }

    private suspend fun rejected(
        session: OrderSession,
        catalog: Catalog,
        error: DomainError,
    ): SubmitResult =
        when (error) {
            is DomainError.PriceChanged -> {
                priceChanged(session, catalog.config.merchantId, error)
            }

            is DomainError.ItemUnavailable -> {
                itemUnavailable(session, error)
            }

            else -> {
                KioskLog.w("order_submit_failed", "error" to error)
                session.submissionFailed()
                SubmitResult.Failed(error)
            }
        }

    /** Refreshes the catalog now, re-prices the cart from it and returns to Reviewing (FR-016, DEV-046). */
    private suspend fun priceChanged(
        session: OrderSession,
        merchantId: String,
        error: DomainError.PriceChanged,
    ): SubmitResult {
        KioskLog.i(
            "order_price_changed",
            "menuVersion" to error.menuVersion,
            "configVersion" to error.configVersion,
        )
        val refresh = catalogSync.syncNow(merchantId)
        if (refresh is Outcome.Failure) {
            KioskLog.w("order_reprice_refresh_failed", "error" to refresh.error)
            session.submissionFailed()
            return SubmitResult.Failed(refresh.error)
        }
        val fresh = catalogs.catalog.first()
        if (fresh == null) {
            session.submissionFailed()
            return SubmitResult.Failed(DomainError.Unknown)
        }
        val repriced = CartRepricer.reprice(session.state.value.cart, fresh.menu)
        session.priceChanged(repriced.cart, fresh.menu.menuVersion, fresh.config.configVersion, repriced.issues)
        return SubmitResult.PriceChanged
    }

    /** Flags the lines E7 named, by line ID and by product or combo ID (FR-016). */
    private fun itemUnavailable(
        session: OrderSession,
        error: DomainError.ItemUnavailable,
    ): SubmitResult {
        val named =
            session.state.value.cart.lines
                .filter { it.lineId in error.lineIds || it.item.catalogId() in error.productIds }
                .mapTo(mutableSetOf()) { it.lineId }
        KioskLog.i("order_item_unavailable", "lines" to named.size)
        session.itemsUnavailable(named)
        return SubmitResult.ItemUnavailable(named)
    }

    private fun CartItem.catalogId(): String =
        when (this) {
            is CartItem.ProductItem -> productId
            is CartItem.ComboItem -> comboId
        }
}

/** The E7 body of [this] order: the cart's lines and the totals the kiosk computed with [rules] (FR-013, FR-016). */
private fun OrderState.toRequest(
    merchantId: String,
    rules: PricingRules,
): OrderRequest =
    OrderRequest(
        merchantId = merchantId,
        orderType = orderType,
        menuVersion = menuVersion,
        configVersion = configVersion,
        roundingStep = rules.roundingStep,
        lines = cart.lines.map { it.toOrderLine() },
        clientTotals = PricingEngine.price(cart.lines, rules),
    )

private fun CartLine.toOrderLine(): OrderLine =
    when (val cartItem = item) {
        is CartItem.ProductItem -> {
            OrderLine.ProductLine(
                lineId = lineId,
                productId = cartItem.productId,
                quantity = quantity,
                selections = cartItem.selections,
                unitPrice = unitPrice,
                lineTotal = lineTotal,
            )
        }

        is CartItem.ComboItem -> {
            OrderLine.ComboLine(
                lineId = lineId,
                comboId = cartItem.comboId,
                quantity = quantity,
                selection = cartItem.selection,
                unitPrice = unitPrice,
                lineTotal = lineTotal,
            )
        }
    }
