package com.hitachi.kiosk.core.domain.error

import com.hitachi.kiosk.core.domain.model.Totals

/**
 * Every failure a repository or port can report (architecture §13.3, technical-design §5.2 TD-04).
 * ViewModels map each case to PRD copy.
 */
sealed interface DomainError {
    /** No connection, or a timeout. [isTimeout] separates the two for logs and copy. */
    data class Network(
        val isTimeout: Boolean = false,
    ) : DomainError

    /** HTTP 401 `TOKEN_INVALID`: the device token was revoked (FR-003). */
    data object Unauthorized : DomainError

    /** HTTP 409 `PRICE_CHANGED` on E7 (FR-016). */
    data class PriceChanged(
        val menuVersion: String?,
        val configVersion: String?,
        val serverTotals: Totals?,
    ) : DomainError

    /** HTTP 409 `ITEM_UNAVAILABLE` on E7 (FR-016). */
    data class ItemUnavailable(
        val lineIds: List<String>,
        val productIds: List<String>,
    ) : DomainError

    /**
     * Any other HTTP 409: a state conflict such as `MERCHANT_NOT_LIVE`, `MERCHANT_CLOSED`, `ORDER_ALREADY_PAID`,
     * `ORDER_NOT_AWAITING_PAYMENT`, `PAYMENT_NOT_CANCELLABLE` or `PIN_INCORRECT` (api-spec §2.4).
     */
    data class Conflict(
        val code: String,
    ) : DomainError

    /**
     * Any other non-2xx answer. [code] is the error envelope `code`, or `HTTP_<status>` when there is no
     * envelope (api-spec §2.4).
     */
    data class Server(
        val code: String,
        val httpStatus: Int? = null,
    ) : DomainError

    /** A device failed: printer, terminal or identity (architecture §9). */
    data class Hardware(
        val device: String,
        val reason: String,
    ) : DomainError

    /** Anything else, for example a body that failed to parse. */
    data object Unknown : DomainError
}
