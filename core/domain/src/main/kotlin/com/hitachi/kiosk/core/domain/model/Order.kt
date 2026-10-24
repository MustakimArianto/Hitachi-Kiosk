package com.hitachi.kiosk.core.domain.model

import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import kotlin.time.Instant

/**
 * The order the kiosk submits with E7 (FR-016). The backend re-prices it and answers `PRICE_CHANGED` when its
 * total differs from [clientTotals]. The 1.0 app always orders in English (D-29), so no language is held here.
 */
data class OrderRequest(
    val merchantId: String,
    val orderType: OrderType,
    val menuVersion: String,
    val configVersion: String,
    val roundingStep: Long,
    val lines: List<OrderLine>,
    val clientTotals: Totals,
)

/** One line of an [OrderRequest]. */
sealed interface OrderLine {
    val lineId: String
    val quantity: Int
    val unitPrice: Rupiah
    val lineTotal: Rupiah

    data class ProductLine(
        override val lineId: String,
        val productId: String,
        override val quantity: Int,
        val selections: List<Selection>,
        override val unitPrice: Rupiah,
        override val lineTotal: Rupiah,
    ) : OrderLine

    data class ComboLine(
        override val lineId: String,
        val comboId: String,
        override val quantity: Int,
        val selection: ComboSelection,
        override val unitPrice: Rupiah,
        override val lineTotal: Rupiah,
    ) : OrderLine
}

/** A chosen modifier option. [level] is set only for `LEVEL` groups, and only when it differs from the default. */
data class Selection(
    val groupId: String,
    val optionId: String,
    val level: Level? = null,
)

data class ComboSelection(
    val sideId: String,
    val drinkId: String,
    val size: ComboSize,
)

enum class ComboSize { REGULAR, LARGE }

/** Order totals (FR-013): `total = subtotal − discount + tax + rounding`. */
data class Totals(
    val subtotal: Rupiah,
    val discount: Rupiah,
    val promoId: String?,
    val tax: Rupiah,
    val rounding: Rupiah,
    val total: Rupiah,
)

/** E7 result: the order number is shown on Complete and printed (FR-020, D-21). */
data class CreatedOrder(
    val orderId: String,
    val orderNumber: String,
    val status: OrderStatus,
    val createdAt: Instant,
    val totals: Totals,
    val lines: List<OrderLineSummary>,
)

data class OrderLineSummary(
    val lineId: String,
    val description: LocalizedText?,
    val lineTotal: Rupiah,
)

enum class OrderStatus { AWAITING_PAYMENT, PAID, CANCELLED }

/** Why an unpaid order is abandoned (E8). */
enum class CancelOrderReason { CUSTOMER_BACK, TIMEOUT, PAYMENT_FAILED, APP_RECOVERY }

/** E8 result. */
data class CancelledOrder(
    val orderId: String,
    val status: OrderStatus,
)

/**
 * The order and payment that survive a process death (architecture §5.3, FR-016): written when E7 succeeds and
 * updated when E9 starts a payment. [paymentId] and [method] are `null` until a payment exists. [idempotencyKey] is
 * the key that created the order. Nothing here is customer data or a payment secret (NFR-006).
 */
data class PendingOrder(
    val orderId: String,
    val orderNumber: String,
    val paymentId: String?,
    val method: PaymentMethod?,
    val idempotencyKey: IdempotencyKey,
    val totals: Totals,
    val createdAt: Instant,
)
