package com.hitachi.kiosk.core.domain.model

/**
 * An amount of Indonesian Rupiah in whole units (BR-01, TR-DAT-01). Money is never a floating-point number.
 * Arithmetic throws [ArithmeticException] on overflow instead of wrapping.
 */
@JvmInline
value class Rupiah(
    val value: Long,
) : Comparable<Rupiah> {
    operator fun plus(other: Rupiah): Rupiah = Rupiah(Math.addExact(value, other.value))

    operator fun minus(other: Rupiah): Rupiah = Rupiah(Math.subtractExact(value, other.value))

    operator fun times(quantity: Int): Rupiah = Rupiah(Math.multiplyExact(value, quantity.toLong()))

    override fun compareTo(other: Rupiah): Int = value.compareTo(other.value)

    companion object {
        val Zero = Rupiah(0)
    }
}
