package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.data.dto.CancelledOrderDto
import com.hitachi.kiosk.core.data.dto.ComboSelectionsDto
import com.hitachi.kiosk.core.data.dto.CreatedOrderDto
import com.hitachi.kiosk.core.data.dto.OrderLineDto
import com.hitachi.kiosk.core.data.dto.OrderLineSummaryDto
import com.hitachi.kiosk.core.data.dto.OrderRequestDto
import com.hitachi.kiosk.core.data.dto.SelectionDto
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.CreatedOrder
import com.hitachi.kiosk.core.domain.model.OrderLine
import com.hitachi.kiosk.core.domain.model.OrderLineSummary
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.OrderStatus

/** The 1.0 app always orders in English (D-29, api-spec §5). */
internal const val ORDER_LANGUAGE = "en"

/**
 * [OrderRequest] → E7 body. Selections are sent as given: the domain sets `level` only for LEVEL options that
 * differ from the default (api-spec E7).
 */
internal fun OrderRequest.toDto(): OrderRequestDto =
    OrderRequestDto(
        merchantId = merchantId,
        orderType = orderType.name,
        language = ORDER_LANGUAGE,
        menuVersion = menuVersion,
        configVersion = configVersion,
        roundingStep = roundingStep,
        lines = lines.map { it.toDto() },
        clientTotals = clientTotals.toDto(),
    )

internal fun OrderLine.toDto(): OrderLineDto =
    when (this) {
        is OrderLine.ProductLine -> {
            OrderLineDto(
                lineId = lineId,
                kind = "PRODUCT",
                productId = productId,
                quantity = quantity,
                selections =
                    selections.map {
                        SelectionDto(
                            groupId = it.groupId,
                            optionId = it.optionId,
                            level = it.level?.name,
                        )
                    },
                unitPrice = unitPrice.value,
                lineTotal = lineTotal.value,
            )
        }

        is OrderLine.ComboLine -> {
            OrderLineDto(
                lineId = lineId,
                kind = "COMBO",
                comboId = comboId,
                quantity = quantity,
                comboSelections =
                    ComboSelectionsDto(
                        sideId = selection.sideId,
                        drinkId = selection.drinkId,
                        size = selection.size.name,
                    ),
                unitPrice = unitPrice.value,
                lineTotal = lineTotal.value,
            )
        }
    }

internal fun CreatedOrderDto.toDomain(): CreatedOrder =
    CreatedOrder(
        orderId = orderId,
        orderNumber = orderNumber,
        status = requiredEnum<OrderStatus>(status, "order.status"),
        createdAt = createdAt.toInstant(),
        totals = totals.toDomain(),
        lines = lines.map { it.toDomain() },
    )

private fun OrderLineSummaryDto.toDomain(): OrderLineSummary =
    OrderLineSummary(lineId = lineId, description = description?.toDomain(), lineTotal = lineTotal.toRupiah())

internal fun CancelledOrderDto.toDomain(): CancelledOrder =
    CancelledOrder(orderId = orderId, status = requiredEnum<OrderStatus>(status, "order.status"))
