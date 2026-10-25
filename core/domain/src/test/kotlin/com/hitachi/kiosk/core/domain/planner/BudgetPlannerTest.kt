package com.hitachi.kiosk.core.domain.planner

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

    @Test
    fun `a combo diner has no extra side, desserts are unaffected (OQ-15, TC-204)`() {
        val combos = planner(cheapCombos())
        val plans =
            listOf(1 to 0, 2 to 0, 2 to 1, 3 to 1).flatMap { (adults, children) ->
                (40_000L..300_000L step 100L).flatMap { budget ->
                    (combos.plan(PlanRequest(Rupiah(budget), adults, children)) as PlanResult.Plans)
                        .plans
                        .map { Triple(adults + children, budget, it) }
                }
            }

        plans.forEach { (people, budget, plan) ->
            val comboDiners = plan.picks.sumOf { comboDinersOf(it) }
            val sides = plan.picks.count { it is PlanPick.Extra && it.item.role == PlanRole.SIDE }
            val extras = plan.picks.count { it is PlanPick.Extra }
            assertWithMessage("$people people at $budget").that(sides).isAtMost(people - comboDiners)
            assertWithMessage("$people people at $budget").that(extras).isAtMost(people)
        }
        assertThat(plans.count { (_, _, plan) -> plan.picks.sumOf { comboDinersOf(it) } > 0 }).isGreaterThan(0)
    }

    @Test
    fun `two adults who share a meal for two get no sides at all, only desserts (OQ-15)`() {
        val combos = planner(cheapCombos())
        val shared =
            (60_000L..300_000L step 100L)
                .map { combos.plan(PlanRequest(Rupiah(it), 2, 0)) as PlanResult.Plans }
                .flatMap { it.plans }
                .filter { plan -> plan.picks.any { it is PlanPick.SharedMeal } }

        assertThat(shared).isNotEmpty()
        shared.forEach { plan ->
            val extras = plan.picks.filterIsInstance<PlanPick.Extra>()
            assertThat(extras.map { it.item.role }).containsNoneIn(listOf(PlanRole.SIDE))
        }
    }

    @Test
    fun `alternatives list the current choice first and each row carries its price change (TC-200)`() {
        val request = PlanRequest(Rupiah(150_000), 2, 1)
        val plan = (planner.plan(request) as PlanResult.Plans).hero
        val index = plan.picks.indexOfFirst { it is PlanPick.MainAndDrink && !it.diner.isChild }

        val rows = planner.alternatives(request, plan, index)

        assertThat(rows.first().isCurrent).isTrue()
        assertThat(rows.first().plan).isEqualTo(plan)
        assertThat(rows.first().change).isEqualTo(0L)
        assertThat(rows.drop(1).none { it.isCurrent }).isTrue()
        rows.drop(1).forEach { row ->
            assertThat(row.change).isEqualTo(row.plan.total.value - plan.total.value)
            assertThat(row.plan.total.value).isAtMost(150_000L)
            assertThat(row.replacement.single()).isNotEqualTo(plan.picks[index])
            BudgetPlannerTest.assertCovers(row.plan, 2, 1)
        }
        assertThat(rows.drop(1).map { it.plan.total.value }).isInOrder()
        assertThat(rows.size - 1).isAtMost(BudgetPlanner.MAX_ALTERNATIVES)
    }

    @Test
    fun `an adult's alternatives mix combos and main plus drink pairs, a child's are kid-friendly pairs`() {
        val request = PlanRequest(Rupiah(200_000), 2, 1)
        val plan = (planner.plan(request) as PlanResult.Plans).hero
        val adult = plan.picks.indexOfFirst { it is PlanPick.MainAndDrink && !it.diner.isChild }
        val child = plan.picks.indexOfFirst { it is PlanPick.MainAndDrink && it.diner.isChild }

        val adultRows = planner.alternatives(request, plan, adult)
        val childRows = planner.alternatives(request, plan, child)

        val kinds = adultRows.map { it.replacement.single()::class }
        assertThat(kinds).contains(PlanPick.MainAndDrink::class)
        assertThat(kinds).contains(PlanPick.Meal::class)
        assertThat(childRows).isNotEmpty()
        childRows.forEach { row ->
            val pick = row.replacement.single() as PlanPick.MainAndDrink
            assertThat(pick.diner.isChild).isTrue()
            assertThat(pick.main.id).isIn(listOf("b1", "c3", "r2"))
            assertThat(pick.drink.id).isIn(listOf("d2", "d4"))
        }
    }

    @Test
    fun `a shared meal for two swaps for another one or for two separate meals`() {
        val combos = planner(cheapCombos())
        // A plan with a shared meal, then the picker with a roomier budget so the dearer separate meals fit.
        val plan =
            (60_000L..300_000L step 100L)
                .flatMap { (combos.plan(PlanRequest(Rupiah(it), 2, 0)) as PlanResult.Plans).plans }
                .first { plan -> plan.picks.any { it is PlanPick.SharedMeal } }
        val index = plan.picks.indexOfFirst { it is PlanPick.SharedMeal }

        val rows = combos.alternatives(PlanRequest(Rupiah(400_000), 2, 0), plan, index)

        assertThat(rows.first().isCurrent).isTrue()
        val separate = rows.filter { it.replacement.size == 2 }
        assertThat(separate).hasSize(1)
        assertThat(separate.single().replacement.all { it is PlanPick.MainAndDrink }).isTrue()
        assertThat(separate.single().change).isGreaterThan(0L)
        rows.forEach { BudgetPlannerTest.assertCovers(it.plan, 2, 0) }
    }

    @Test
    fun `a shared meal is not offered as separate meals when they would break the budget`() {
        val combos = planner(cheapCombos())
        val plan =
            (60_000L..300_000L step 100L)
                .flatMap { (combos.plan(PlanRequest(Rupiah(it), 2, 0)) as PlanResult.Plans).plans }
                .first { plan -> plan.picks.any { it is PlanPick.SharedMeal } }
        val index = plan.picks.indexOfFirst { it is PlanPick.SharedMeal }

        val rows = combos.alternatives(PlanRequest(plan.total, 2, 0), plan, index)

        assertThat(rows.none { it.replacement.size == 2 }).isTrue()
        rows.forEach { assertThat(it.plan.total.value).isAtMost(plan.total.value) }
    }

    @Test
    fun `an extra swaps for another extra of its kind, and an unknown pick has none`() {
        val request = PlanRequest(Rupiah(100_000), 1, 0)
        val plan = (planner.plan(request) as PlanResult.Plans).hero
        val index = plan.picks.indexOfFirst { it is PlanPick.Extra }
        val extra = plan.picks[index] as PlanPick.Extra

        val rows = planner.alternatives(request, plan, index)

        rows
            .drop(
                1,
            ).forEach { assertThat((it.replacement.single() as PlanPick.Extra).item.role).isEqualTo(extra.item.role) }
        assertThat(planner.alternatives(request, plan, plan.picks.size)).isEmpty()
        assertThat(planner.alternatives(request, plan, -1)).isEmpty()
    }

    /** Combos cheaper than the same food apart, so a plan can reach totals only they reach (OQ-15, TC-204). */
    private fun cheapCombos(): Menu =
        SampleData.menu.copy(
            combos =
                SampleData.menu.combos.map {
                    it.copy(price = Rupiah(if (it.serves == 2) 60_000L else 30_000L + it.price.value % 5_000L))
                },
        )

    private fun comboDinersOf(pick: PlanPick): Int =
        when (pick) {
            is PlanPick.Meal -> 1
            is PlanPick.SharedMeal -> 2
            else -> 0
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
