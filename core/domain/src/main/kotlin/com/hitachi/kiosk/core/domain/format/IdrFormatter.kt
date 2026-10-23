package com.hitachi.kiosk.core.domain.format

import com.hitachi.kiosk.core.domain.model.Rupiah

/**
 * The one money format of the app (BR-01): `Rp 1.234.567`, whole Rupiah with a dot every three digits, never the
 * locale's default number format (NFR-008). Pure Kotlin, so receipts and screens format the same way.
 */
object IdrFormatter {
    private const val GROUP = 3

    /** `Rp 52.000`; a negative amount is `-Rp 10.000`. */
    fun format(amount: Rupiah): String = sign(amount) + "Rp " + digits(amount)

    /** A price difference: `+Rp 6.000`, `-Rp 10.000`, or `Rp 0` (FR-010, FR-011 option labels). */
    fun formatSigned(amount: Rupiah): String =
        when {
            amount.value > 0 -> "+Rp " + digits(amount)
            else -> format(amount)
        }

    private fun sign(amount: Rupiah): String = if (amount.value < 0) "-" else ""

    private fun digits(amount: Rupiah): String {
        // Long.MIN_VALUE has no positive counterpart, so work on the digits of the string form.
        val plain = amount.value.toString().removePrefix("-")
        return plain
            .reversed()
            .chunked(GROUP)
            .joinToString(".")
            .reversed()
    }
}
