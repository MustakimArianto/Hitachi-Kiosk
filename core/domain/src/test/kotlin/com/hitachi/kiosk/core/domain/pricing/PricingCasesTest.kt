package com.hitachi.kiosk.core.domain.pricing

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Totals
import com.hitachi.kiosk.core.domain.testing.CaseTable
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

/** TC-060: every row of `docs/pricing-cases.md` matches the engine. */
class PricingCasesTest {
    @Test
    fun `every golden pricing case matches (TC-060)`() {
        val rows = CaseTable.rows("pricing-cases.md")

        assertThat(rows.size).isAtLeast(MIN_CASES)
        rows.forEach { cells ->
            val (id, _, lines, step) = cells
            val subtotal = lines.split("+").sumOf { line -> lineTotal(line.trim()) }
            val expected =
                Totals(
                    subtotal = Rupiah(CaseTable.amount(cells[SUBTOTAL])),
                    discount = Rupiah(CaseTable.amount(cells[DISCOUNT])),
                    promoId = if (CaseTable.amount(cells[DISCOUNT]) > 0) PROMO_ID else null,
                    tax = Rupiah(CaseTable.amount(cells[TAX])),
                    rounding = Rupiah(CaseTable.amount(cells[ROUNDING])),
                    total = Rupiah(CaseTable.amount(cells[TOTAL])),
                )

            val totals =
                PricingEngine.price(
                    Rupiah(subtotal),
                    PricingRules.of(SampleData.config, CaseTable.amount(step)),
                )

            assertWithMessage(id).that(totals).isEqualTo(expected)
        }
    }

    /** `2×16.000` → 32000; `75.000` → 75000. */
    private fun lineTotal(line: String): Long {
        val parts = line.split("×")
        return if (parts.size == 2) parts[0].toLong() * CaseTable.amount(parts[1]) else CaseTable.amount(line)
    }

    private companion object {
        const val MIN_CASES = 20
        const val PROMO_ID = "promo_10k_over_100k"
        const val SUBTOTAL = 4
        const val DISCOUNT = 5
        const val TAX = 6
        const val ROUNDING = 7
        const val TOTAL = 8
    }
}
