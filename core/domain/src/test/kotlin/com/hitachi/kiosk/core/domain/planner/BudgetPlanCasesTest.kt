package com.hitachi.kiosk.core.domain.planner

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.testing.CaseTable
import org.junit.Test

/** Every row of `docs/budget-plan-cases.md` matches the planner (FR-032, DEV-042). */
class BudgetPlanCasesTest {
    private val planner = BudgetPlannerTest.planner()

    @Test
    fun `every golden Budget Plan case matches`() {
        val rows = CaseTable.rows("budget-plan-cases.md")

        assertThat(rows.size).isAtLeast(MIN_CASES)
        rows.forEach { cells ->
            val name = cells[0]
            val budget = cells[BUDGET]
            val adults = cells[ADULTS]
            val children = cells[CHILDREN]
            val options = cells[OPTIONS]
            val request =
                PlanRequest(
                    budget = Rupiah(CaseTable.amount(budget)),
                    adults = adults.toInt(),
                    children = children.toInt(),
                    options =
                        PlanOptions(
                            includeSides = "no sides" !in options,
                            includeDesserts = "no desserts" !in options,
                            tolerance = if ("strict" in options) PlanTolerance.STRICT else PlanTolerance.UP_TO_CAP,
                        ),
                )

            val result = planner.plan(request) as PlanResult.Plans

            assertWithMessage("$name hero").that(result.hero.kind.name).isEqualTo(cells[HERO])
            assertWithMessage(
                "$name hero total",
            ).that(result.hero.total.value).isEqualTo(CaseTable.amount(cells[HERO_TOTAL]))
            assertWithMessage(
                "$name just over",
            ).that(result.over?.total?.value ?: 0L).isEqualTo(CaseTable.amount(cells[OVER]))
            result.plans.forEach { BudgetPlannerTest.assertCovers(it, request.adults, request.children) }
        }
    }

    private companion object {
        const val MIN_CASES = 10
        const val BUDGET = 1
        const val ADULTS = 2
        const val CHILDREN = 3
        const val OPTIONS = 4
        const val HERO = 5
        const val HERO_TOTAL = 6
        const val OVER = 7
    }
}
