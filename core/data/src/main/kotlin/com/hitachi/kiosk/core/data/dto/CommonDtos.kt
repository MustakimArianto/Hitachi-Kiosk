package com.hitachi.kiosk.core.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Wire types of docs/api/api-spec.md. Enums are kept as String here and mapped explicitly in mapper/, so an
// unknown value never parses into a silently wrong constant (technical-design §5.1).

/** `{ "en": …, "id": … }` (api-spec §2.3). */
@Serializable
internal data class LocalizedTextDto(
    val en: String? = null,
    val id: String? = null,
)

/** The body of every non-2xx answer (api-spec §2.4). */
@Serializable
internal data class ErrorEnvelopeDto(
    val code: String? = null,
    val message: String? = null,
    val details: JsonObject? = null,
)

/** `details` of E7 409 `PRICE_CHANGED`. */
@Serializable
internal data class PriceChangedDetailsDto(
    val menuVersion: String? = null,
    val configVersion: String? = null,
    val serverTotals: TotalsDto? = null,
)

/** `details` of E7 409 `ITEM_UNAVAILABLE`. */
@Serializable
internal data class ItemUnavailableDetailsDto(
    val lineIds: List<String> = emptyList(),
    val productIds: List<String> = emptyList(),
)

/** Order totals (E7 request `clientTotals`, response `totals`, `PRICE_CHANGED` `serverTotals`). */
@Serializable
internal data class TotalsDto(
    val subtotal: Long,
    val discount: Long,
    val promoId: String? = null,
    val tax: Long,
    val rounding: Long,
    val total: Long,
)

/** An EDC terminal (E5 `paymentMethods[].terminal`, E9 card `terminal`). */
@Serializable
internal data class TerminalDto(
    val tid: String,
    val connection: String,
)

/** The staff PIN hash (E5 `staffPin`, E13 response). Never the plain PIN. */
@Serializable
internal data class StaffPinDto(
    val algorithm: String,
    val iterations: Int? = null,
    val salt: String? = null,
    val hash: String,
    val version: Int,
) {
    override fun toString(): String = "StaffPinDto(algorithm=$algorithm, version=$version)"
}
