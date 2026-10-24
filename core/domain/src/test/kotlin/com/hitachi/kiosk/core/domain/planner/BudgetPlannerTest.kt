package com.hitachi.kiosk.core.domain.planner

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Assert.assertThrows
import org.junit.Test

/** FR-032 on the tagged sample menu of api-spec §4 (testing-plan §5.4b). */
class BudgetPlannerTest {
    private val planner = planner()

    @Test
    fun `2 adults and a child at Rp 150_000 get Rp 149_600 and Rp 150_700 (AC1, TC-161)`() {
        val result = plans(150_000, adults = 2, children = 1)

        assertThat(result.plans.map { it.kind }).containsExactly(PlanKind.UNDER, PlanKind.OVER).inOrder()
        assertThat(result.hero.total).isEqualTo(Rupiah(149_600))
        assertThat(result.over?.total).isEqualTo(Rupiah(150_700))
        result.plans.forEach { plan -> assertCovers(plan, adults = 2, children = 1) }
    }

    @Test
    fun `1 adult and a child at Rp 99_000 is an exact match on a Rp 100_000 subtotal (AC2, TC-162)`() {
        val result = plans(99_000, adults = 1, children = 1)

        assertThat(result.plans).hasSize(1)
        assertThat(result.hero.kind).isEqualTo(PlanKind.EXACT)
        assertThat(result.hero.total).isEqualTo(Rupiah(99_000))
        assertThat(result.hero.totals.subtotal).isEqualTo(Rupiah(100_000))
        assertThat(result.hero.totals.discount).isEqualTo(Rupiah(10_000))
    }

    @Test
    fun `2 adults and a child at Rp 100_000 need Rp 16_600 more (AC3, TC-163)`() {
        val result = plans(100_000, adults = 2, children = 1)

        assertThat(result.plans).hasSize(1)
        assertThat(result.hero.kind).isEqualTo(PlanKind.TOO_LOW)
        assertThat(result.hero.total).isEqualTo(Rupiah(116_600))
        assertCovers(result.hero, adults = 2, children = 1)
    }

    @Test
    fun `a huge budget gets the most generous plan, one main per person (TC-164 a)`() {
        val result = plans(1_000_000, adults = 1, children = 0)

        assertThat(result.plans).hasSize(1)
        assertThat(result.hero.kind).isEqualTo(PlanKind.MORE_THAN_ENOUGH)
        assertCovers(result.hero, adults = 1, children = 0)
    }

    @Test
    fun `no over plan beyond the cap, and none in Strict (TC-164 b, AC10)`() {
        // Rp 116_600 is the cheapest plan for 2 + 1; Rp 110_000 plus 10 % is Rp 121_000, so Rp 116_600 is over and in
        // the cap — but with no under plan the budget is too low.
        assertThat(plans(110_000, 2, 1).hero.kind).isEqualTo(PlanKind.TOO_LOW)
        val strict = plans(150_000, 2, 1, PlanOptions(tolerance = PlanTolerance.STRICT))
        assertThat(strict.plans.map { it.kind }).containsExactly(PlanKind.UNDER)
        val tightCap = planner(overCapPercent = 0).plan(PlanRequest(Rupiah(150_000), 2, 1)) as PlanResult.Plans
        assertThat(tightCap.over).isNull()
    }

    @Test
    fun `children get only kid-friendly mains and drinks (AC4, TC-165 a)`() {
        val result = plans(120_000, adults = 1, children = 2)

        result.plans.forEach { plan ->
            val children = plan.picks.filterIsInstance<PlanPick.MainAndDrink>().filter { it.diner.isChild }
            assertThat(children).hasSize(2)
            children.forEach { pick ->
                assertThat(pick.main.id).isIn(listOf("b1", "c3", "r2"))
                assertThat(pick.drink.id).isIn(listOf("d2", "d4"))
                assertThat(pick.kidFriendly).isTrue()
            }
        }
        assertThat(result.kidsMenuUnavailable).isFalse()
    }

    @Test
    fun `without a kid-friendly main children get the cheapest main and a note (AC4, TC-165 b)`() {
        val menu =
            SampleData.menu.copy(
                products =
                    SampleData.menu.products.map {
                        if (it.role == ProductRole.MAIN) it.copy(kidFriendly = false) else it
                    },
            )

        val result = planner(menu).plan(PlanRequest(Rupiah(120_000), 1, 2)) as PlanResult.Plans

        assertThat(result.kidsMenuUnavailable).isTrue()
        result.hero.picks
            .filterIsInstance<PlanPick.MainAndDrink>()
            .filter { it.diner.isChild }
            .forEach {
                assertThat(it.main.id).isEqualTo("r3")
                assertThat(it.kidFriendly).isFalse()
            }
    }

    @Test
    fun `a group over maxGroupSize gets no plans (AC11, TC-166)`() {
        assertThat(planner.plan(PlanRequest(Rupiah(500_000), 8, 3))).isEqualTo(PlanResult.GroupTooLarge(10))
        assertThat(planner.plan(PlanRequest(Rupiah(500_000), 8, 2))).isInstanceOf(PlanResult.Plans::class.java)
        assertThrows(IllegalArgumentException::class.java) { planner.plan(PlanRequest(Rupiah(100_000), 0, 1)) }
        assertThrows(IllegalArgumentException::class.java) { planner.plan(PlanRequest(Rupiah.Zero, 1, 0)) }
    }

    @Test
    fun `a menu without drinks has no plan and no entry row`() {
        val menu = SampleData.menu.copy(products = SampleData.menu.products.filterNot { it.role == ProductRole.DRINK })
        val noDrinks = planner(menu)

        assertThat(noDrinks.isAvailable).isFalse()
        assertThat(noDrinks.plan(PlanRequest(Rupiah(100_000), 1, 0))).isEqualTo(PlanResult.NoPlan)
        assertThat(planner.isAvailable).isTrue()
    }

    @Test
    fun `sides and desserts can be left out (Customize toggles, TC-170)`() {
        val noExtras = plans(150_000, 2, 1, PlanOptions(includeSides = false, includeDesserts = false))
        val noDesserts = plans(150_000, 2, 1, PlanOptions(includeDesserts = false))

        assertThat(noExtras.hero.picks.filterIsInstance<PlanPick.Extra>()).isEmpty()
        assertThat(
            noDesserts.plans
                .flatMap { it.picks }
                .flatMap { it.items }
                .map { it.role },
        ).doesNotContain(PlanRole.DESSERT)
    }

    @Test
    fun `a plan goes to the cart with exactly its items and total (AC5)`() {
        val plan = plans(150_000, 2, 1).hero

        val additions = plan.toCartAdditions()

        assertThat(additions.sumOf { it.unitPrice.value * it.quantity }).isEqualTo(plan.totals.subtotal.value)
        assertThat(additions).hasSize(plan.itemCount)
    }

    @Test
    fun `items without a default option take the first one (A-42)`() {
        val plan = plans(300_000, 3, 0).hero
        val items = plan.picks.flatMap { it.items }

        items.filter { it.id == "r1" }.forEach { assertThat(it.choices).isNotEmpty() }
    }

    private fun plans(
        budget: Long,
        adults: Int,
        children: Int,
        options: PlanOptions = PlanOptions(),
    ): PlanResult.Plans = planner.plan(PlanRequest(Rupiah(budget), adults, children, options)) as PlanResult.Plans

    companion object {
        fun planner(
            menu: Menu = SampleData.menu,
            overCapPercent: Int = 10,
        ) = BudgetPlanner(
            menu = menu,
            config = SampleData.config.budgetPlanner.copy(overCapPercent = overCapPercent),
            rules = PricingRules.of(SampleData.config),
        )

        /** BR-13, BR-14: every person has one main and one drink, or a meal; nobody has two mains. */
        fun assertCovers(
            plan: BudgetPlan,
            adults: Int,
            children: Int,
        ) {
            val covered =
                plan.picks.flatMap { pick ->
                    when (pick) {
                        is PlanPick.MainAndDrink -> listOf(pick.diner)
                        is PlanPick.Meal -> listOf(pick.diner)
                        is PlanPick.SharedMeal -> listOf(pick.first, pick.second)
                        is PlanPick.Extra -> emptyList()
                    }
                }
            assertThat(covered).containsNoDuplicates()
            assertThat(covered.count { !it.isChild }).isEqualTo(adults)
            assertThat(covered.count { it.isChild }).isEqualTo(children)
            assertThat(plan.picks.count { it is PlanPick.Extra }).isAtMost(adults + children)
        }
    }
}
