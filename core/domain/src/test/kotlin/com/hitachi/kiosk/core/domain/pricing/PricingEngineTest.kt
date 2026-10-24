package com.hitachi.kiosk.core.domain.pricing

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.cart.Cart
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.model.Promo
import com.hitachi.kiosk.core.domain.model.PromoType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Totals
import com.hitachi.kiosk.core.testing.fixture.SampleData
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class PricingEngineTest {
    private val rules = PricingRules.of(SampleData.config)

    private fun rules(step: Long) = PricingRules.of(SampleData.config, step)

    @Test
    fun `the design sample cart is Rp 172_700 (FR-013 AC1, TC-055)`() {
        var cart = Cart()
        listOf(1 to 75_000L, 1 to 38_000L, 1 to 22_000L, 2 to 16_000L).forEachIndexed { index, (quantity, price) ->
            cart = (cart.add(product("p$index"), quantity, Rupiah(price), emptyList()) as CartAddResult.Added).cart
        }

        val totals = PricingEngine.price(cart.lines, rules)

        assertThat(totals)
            .isEqualTo(
                Totals(
                    subtotal = Rupiah(167_000),
                    discount = Rupiah(10_000),
                    promoId = "promo_10k_over_100k",
                    tax = Rupiah(15_700),
                    rounding = Rupiah.Zero,
                    total = Rupiah(172_700),
                ),
            )
        assertThat(totals).isEqualTo(SampleData.totals)
    }

    @Test
    fun `the promo starts exactly at the threshold and hints below it (FR-013 AC2, TC-056)`() {
        val below = PricingEngine.price(Rupiah(99_999), rules)
        val at = PricingEngine.price(Rupiah(100_000), rules)

        assertThat(below.discount).isEqualTo(Rupiah.Zero)
        assertThat(below.promoId).isNull()
        assertThat(PricingEngine.promoHint(Rupiah(99_999), rules))
            .isEqualTo(
                PromoHint(
                    promoId = "promo_10k_over_100k",
                    remaining = Rupiah(1),
                    discount = Rupiah(10_000),
                    threshold = Rupiah(100_000),
                    progressPercent = 99,
                ),
            )
        assertThat(at.discount).isEqualTo(Rupiah(10_000))
        assertThat(PricingEngine.promoHint(Rupiah(100_000), rules)).isNull()
    }

    @Test
    fun `there is no hint for an empty cart or a merchant without promos`() {
        assertThat(PricingEngine.promoHint(Rupiah.Zero, rules)).isNull()
        assertThat(PricingEngine.promoHint(Rupiah(5_000), rules.copy(promos = emptyList()))).isNull()
        assertThat(PricingEngine.promoHint(Rupiah(5_000), rules)?.progressPercent).isEqualTo(5)
    }

    @Test
    fun `tax rounds half up (BR-02, TC-057)`() {
        assertThat(PricingEngine.price(Rupiah(12_345), rules).tax).isEqualTo(Rupiah(1_235))
        assertThat(PricingEngine.price(Rupiah(12_344), rules).tax).isEqualTo(Rupiah(1_234))
    }

    @Test
    fun `the total rounds up to the step with a Rounding line (FR-013 AC3, TC-058)`() {
        val subtotal = Rupiah(167_000)

        assertThat(PricingEngine.price(subtotal, rules(0)).total).isEqualTo(Rupiah(172_700))
        assertThat(PricingEngine.price(subtotal, rules(100)).total).isEqualTo(Rupiah(172_700))
        assertThat(PricingEngine.price(subtotal, rules(500)).total).isEqualTo(Rupiah(173_000))
        assertThat(PricingEngine.price(subtotal, rules(500)).rounding).isEqualTo(Rupiah(300))
    }

    @Test
    fun `a step config does not allow falls back to the config default (A-25)`() {
        assertThat(PricingRules.of(SampleData.config, 250).roundingStep).isEqualTo(0)
        assertThat(PricingRules.of(SampleData.config, null).roundingStep).isEqualTo(0)
        assertThat(PricingRules.of(SampleData.config, 500).roundingStep).isEqualTo(500)
    }

    @Test
    fun `IDR formatting matches the design (BR-01, TC-059)`() {
        assertThat(IdrFormatter.format(Rupiah(0))).isEqualTo("Rp 0")
        assertThat(IdrFormatter.format(Rupiah(8_000))).isEqualTo("Rp 8.000")
        assertThat(IdrFormatter.format(Rupiah(1_234_567))).isEqualTo("Rp 1.234.567")
    }

    @Test
    fun `the best of several promos applies, and only one (BR-03, A-12)`() {
        val promos =
            listOf(
                promo("small", threshold = 50_000, amount = 5_000),
                promo("big", threshold = 100_000, amount = 10_000),
            )
        val twoPromos = rules.copy(promos = promos)

        assertThat(PricingEngine.price(Rupiah(120_000), twoPromos).promoId).isEqualTo("big")
        assertThat(PricingEngine.price(Rupiah(60_000), twoPromos).promoId).isEqualTo("small")
        assertThat(PricingEngine.promoHint(Rupiah(40_000), twoPromos)?.promoId).isEqualTo("small")
        // One promo per order: once "small" applies there is no nudge towards "big".
        assertThat(PricingEngine.promoHint(Rupiah(60_000), twoPromos)).isNull()
    }

    @Test
    fun `a discount never makes the taxable amount negative`() {
        val generous = rules.copy(promos = listOf(promo("free", threshold = 0, amount = 50_000)))

        val totals = PricingEngine.price(Rupiah(20_000), generous)

        assertThat(totals.discount).isEqualTo(Rupiah(20_000))
        assertThat(totals.total).isEqualTo(Rupiah.Zero)
    }

    @Test
    fun `invalid inputs are programmer errors`() {
        assertThrows(IllegalArgumentException::class.java) { PricingEngine.price(Rupiah(-1), rules) }
        assertThrows(IllegalArgumentException::class.java) { rules.copy(roundingStep = -1) }
        assertThrows(IllegalArgumentException::class.java) { rules.copy(taxRateBasisPoints = -1) }
    }

    @Test
    fun `the invariants hold for random carts (TC-061)`() =
        runTest {
            checkAll(
                PROPERTY_RUNS,
                Arb.list(Arb.long(0L..MAX_LINE_TOTAL), 0..Cart.MAX_LINES),
                Arb.element(0L, 100L, 500L),
                Arb.int(0..2_000),
            ) { lineTotals, step, rate ->
                val subtotal = Rupiah(lineTotals.sum())
                val priced = PricingEngine.price(subtotal, rules.copy(roundingStep = step, taxRateBasisPoints = rate))
                val taxable = priced.subtotal - priced.discount

                assertThat(priced.total.value).isAtLeast(0L)
                assertThat(priced.total.value).isAtLeast(taxable.value)
                assertThat(priced.total).isEqualTo(taxable + priced.tax + priced.rounding)
                assertThat(priced.tax.value).isEqualTo((taxable.value * rate + 5_000) / 10_000)
                if (step > 0) {
                    assertThat(priced.total.value % step).isEqualTo(0L)
                    assertThat(priced.rounding.value).isIn(0L until step)
                } else {
                    assertThat(priced.rounding).isEqualTo(Rupiah.Zero)
                }
            }
        }

    private fun product(id: String) = CartItem.ProductItem(id, emptyList())

    private fun promo(
        id: String,
        threshold: Long,
        amount: Long,
    ) = Promo(
        id = id,
        type = PromoType.THRESHOLD_FIXED,
        threshold = Rupiah(threshold),
        amount = Rupiah(amount),
        label = null,
    )

    private companion object {
        const val PROPERTY_RUNS = 1_000
        const val MAX_LINE_TOTAL = 20L * 200_000L
    }
}
