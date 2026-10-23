package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.data.dto.LocalizedTextDto
import com.hitachi.kiosk.core.data.dto.StaffPinDto
import com.hitachi.kiosk.core.data.dto.TerminalDto
import com.hitachi.kiosk.core.data.dto.TotalsDto
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.StaffPinHash
import com.hitachi.kiosk.core.domain.model.TerminalInfo
import com.hitachi.kiosk.core.domain.model.Totals

internal fun LocalizedTextDto.toDomain(): LocalizedText = LocalizedText(en = en, id = id)

internal fun Long.toRupiah(): Rupiah = Rupiah(this)

internal fun TotalsDto.toDomain(): Totals =
    Totals(
        subtotal = subtotal.toRupiah(),
        discount = discount.toRupiah(),
        promoId = promoId,
        tax = tax.toRupiah(),
        rounding = rounding.toRupiah(),
        total = total.toRupiah(),
    )

internal fun Totals.toDto(): TotalsDto =
    TotalsDto(
        subtotal = subtotal.value,
        discount = discount.value,
        promoId = promoId,
        tax = tax.value,
        rounding = rounding.value,
        total = total.value,
    )

internal fun TerminalDto.toDomain(): TerminalInfo = TerminalInfo(tid = tid, connection = connection)

internal fun StaffPinDto.toDomain(): StaffPinHash =
    StaffPinHash(algorithm = algorithm, iterations = iterations, salt = salt, hash = hash, version = version)
