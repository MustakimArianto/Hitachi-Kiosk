package com.hitachi.kiosk.core.data.dto

import kotlinx.serialization.Serializable

// Request DTOs have no default values except null: encodeDefaults is off, so a default would not be sent.

/** E7 request. */
@Serializable
internal data class OrderRequestDto(
    val merchantId: String,
    val orderType: String,
    val language: String,
    val menuVersion: String,
    val configVersion: String,
    val roundingStep: Long,
    val lines: List<OrderLineDto>,
    val clientTotals: TotalsDto,
)

/** One E7 line: `kind` `PRODUCT` carries `productId` and `selections`, `COMBO` carries `comboId` and `comboSelections`. */
@Serializable
internal data class OrderLineDto(
    val lineId: String,
    val kind: String,
    val productId: String? = null,
    val comboId: String? = null,
    val quantity: Int,
    val selections: List<SelectionDto>? = null,
    val comboSelections: ComboSelectionsDto? = null,
    val unitPrice: Long,
    val lineTotal: Long,
)

@Serializable
internal data class SelectionDto(
    val groupId: String,
    val optionId: String,
    val level: String? = null,
)

@Serializable
internal data class ComboSelectionsDto(
    val sideId: String,
    val drinkId: String,
    val size: String,
)

/** E7 response. */
@Serializable
internal data class CreatedOrderDto(
    val orderId: String,
    val orderNumber: String,
    val status: String,
    val createdAt: String,
    val totals: TotalsDto,
    val lines: List<OrderLineSummaryDto> = emptyList(),
)

@Serializable
internal data class OrderLineSummaryDto(
    val lineId: String,
    val description: LocalizedTextDto? = null,
    val lineTotal: Long,
)

/** E8 request. */
@Serializable
internal data class CancelOrderRequestDto(
    val reason: String,
)

/** E8 response. */
@Serializable
internal data class CancelledOrderDto(
    val orderId: String,
    val status: String,
)
