package com.hitachi.kiosk.core.domain.planner

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.availableCombos
import com.hitachi.kiosk.core.domain.pricing.PricingEngine
import com.hitachi.kiosk.core.domain.pricing.PricingRules
import com.hitachi.kiosk.core.testing.fixture.SampleData
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.measureTime

/** FR-032 AC6, AC8, AC9 and the DEV-042 property test, on the sample menu. */
class BudgetPlannerPropertiesTest {
    private val planner = BudgetPlannerTest.planner()
    private val rules = PricingRules.of(SampleData.config)
    private val menu = SampleData.menu

    @Test
    fun `the planner returns an exact plan whenever one exists (AC8)`() {
        GROUPS.forEach { (adults, children) ->
            val exactTotals = reachableSubtotals(adults, children).map { total(it) }.toSet()
            for (budget in 50_000L..300_000L step 100L) {
                val result = planner.plan(PlanRequest(Rupiah(budget), adults, children)) as PlanResult.Plans
                if (budget in exactTotals) {
                    assertWithMessage("$adults + $children at $budget").that(result.hero.kind).isEqualTo(PlanKind.EXACT)
                }
                if (result.hero.kind == PlanKind.EXACT) assertThat(result.hero.total.value).isEqualTo(budget)
            }
        }
    }

    @Test
    fun `random groups and budgets keep the plan rules (DEV-042)`() =
        runTest {
            checkAll(PROPERTY_RUNS, Arb.int(1..10), Arb.int(0..9), Arb.long(500L..8_000L)) { adults, kids, hundreds ->
                val children = minOf(kids, 10 - adults)
                val budget = hundreds * 100
                val request = PlanRequest(Rupiah(budget), adults, children)
                val result = planner.plan(request) as PlanResult.Plans
                val cap = budget + budget / 10

                result.plans.forEach { BudgetPlannerTest.assertCovers(it, adults, children) }
                when (result.hero.kind) {
                    PlanKind.EXACT -> assertThat(result.hero.total.value).isEqualTo(budget)
                    PlanKind.UNDER, PlanKind.MORE_THAN_ENOUGH -> assertThat(result.hero.total.value).isAtMost(budget)
                    PlanKind.TOO_LOW -> assertThat(result.hero.total.value).isGreaterThan(budget)
                    PlanKind.OVER -> error("The hero is never the over plan")
                }
                result.over?.let { assertThat(it.total.value).isIn(budget + 1..cap) }
            }
        }

    @Test
    fun `a swap lands on the budget or the nearest total on its side (AC9, TC-170)`() {
        val request = PlanRequest(Rupiah(150_000), 2, 1)
        val result = planner.plan(request) as PlanResult.Plans
        listOf(result.hero, checkNotNull(result.over)).forEach { plan ->
            plan.picks.forEachIndexed { index, pick ->
                val parts = if (pick is PlanPick.MainAndDrink) listOf(SwapPart.MAIN, SwapPart.DRINK) else emptyList()
                parts.forEach { part ->
                    val swapped = planner.swap(request, plan, index, part)
                    val base = swapped.picks.filterNot { it is PlanPick.Extra }
                    val baseSubtotal = base.sumOf { p -> p.items.sumOf { it.unitPrice.value } }
                    val possible = extraSubtotals(people = 3).map { total(baseSubtotal + it) }

                    assertThat(swapped.picks[index]).isNotEqualTo(pick)
                    val total = swapped.total.value
                    if (plan.kind == PlanKind.OVER && possible.any { it in 150_001L..165_000L }) {
                        assertThat(total).isEqualTo(possible.filter { it in 150_001L..165_000L }.min())
                    } else if (possible.any { it <= 150_000L }) {
                        assertThat(total).isEqualTo(possible.filter { it <= 150_000L }.max())
                    } else {
                        assertThat(total).isEqualTo(possible.min())
                    }
                    BudgetPlannerTest.assertCovers(swapped, 2, 1)
                }
            }
        }
    }

    @Test
    fun `swapping an extra keeps it and cycles within its role, and other picks do not swap`() {
        val request = PlanRequest(Rupiah(100_000), 1, 0)
        val plan = (planner.plan(request) as PlanResult.Plans).hero
        val extraIndex = plan.picks.indexOfFirst { it is PlanPick.Extra }
        val extra = plan.picks[extraIndex] as PlanPick.Extra

        val swapped = planner.swap(request, plan, extraIndex, SwapPart.EXTRA).picks[extraIndex] as PlanPick.Extra

        assertThat(swapped.item.id).isNotEqualTo(extra.item.id)
        assertThat(swapped.item.role).isEqualTo(extra.item.role)
        assertThat(planner.swap(request, plan, extraIndex, SwapPart.MAIN)).isEqualTo(plan)
        assertThat(planner.swap(request, plan, plan.picks.size, SwapPart.MAIN)).isEqualTo(plan)
    }

    @Test
    fun `ten people plan within the JVM budget (AC6, TC-168 until the device benchmark)`() {
        val request = PlanRequest(Rupiah(600_000), 10, 0)
        repeat(WARM_UP) { BudgetPlannerTest.planner().plan(request) }

        val times =
            List(RUNS) {
                measureTime {
                    val fresh = BudgetPlannerTest.planner()
                    val plan = (fresh.plan(request) as PlanResult.Plans).hero
                    fresh.swap(request, plan, 0, SwapPart.MAIN)
                }
            }.sorted()

        val p95 = times[RUNS * 95 / 100 - 1]
        println("BudgetPlanner 10 adults: p50=${times[RUNS / 2]} p95=$p95")
        assertThat(p95.inWholeMilliseconds).isLessThan(JVM_LIMIT_MILLIS)
    }

    @Test
    fun `swaps never take a plan past its limit, in Strict or within the cap (AC9, AC10)`() {
        val requests =
            PlanTolerance.entries.flatMap { tolerance ->
                (40_000L..300_000L step 20_000L).flatMap { budget ->
                    (GROUPS + (3 to 2)).map { (adults, children) ->
                        PlanRequest(Rupiah(budget), adults, children, PlanOptions(tolerance = tolerance))
                    }
                }
            }
        requests.forEach { request ->
            (planner.plan(request) as PlanResult.Plans).plans.forEach { start ->
                assertSwapsStayWithinLimit(request, start)
            }
        }
    }

    private fun assertSwapsStayWithinLimit(
        request: PlanRequest,
        start: BudgetPlan,
    ) {
        val budget = request.budget.value
        val cap = if (request.options.tolerance == PlanTolerance.STRICT) budget else budget + budget / 10
        var plan = start
        repeat(SWAP_ROUNDS) {
            for (index in plan.picks.indices) {
                for (part in SwapPart.entries) {
                    plan = planner.swap(request, plan, index, part)
                    if (start.kind != PlanKind.TOO_LOW) {
                        assertWithMessage("$request $part").that(plan.total.value).isAtMost(cap)
                    }
                    BudgetPlannerTest.assertCovers(plan, request.adults, request.children)
                }
            }
        }
    }

    @Test
    fun `prices that share only Rp 1 still plan ten people quickly (TR-PRF-08)`() {
        val odd = menu.copy(products = menu.products.map { if (it.id == "d1") it.copy(price = Rupiah(16_001)) else it })
        val oddPlanner = BudgetPlannerTest.planner(odd)
        val request = PlanRequest(Rupiah(600_000), 10, 0)
        repeat(WARM_UP) { oddPlanner.plan(request) }

        val time = measureTime { oddPlanner.plan(request) }
        val result = oddPlanner.plan(request) as PlanResult.Plans

        println("BudgetPlanner 10 adults, Rp 1 unit: $time")
        assertThat(time.inWholeMilliseconds).isLessThan(JVM_LIMIT_MILLIS)
        assertThat(result.hero.total.value).isAtMost(600_000L)
        BudgetPlannerTest.assertCovers(result.hero, 10, 0)
    }

    /** Every subtotal the plan rules allow for the group, by plain set convolution (no planner code). */
    private fun reachableSubtotals(
        adults: Int,
        children: Int,
    ): Set<Long> {
        val products = menu.products.filter { it.available }
        val mains = products.filter { it.role == ProductRole.MAIN && it.servesAdults == 1 }
        val drinks = products.filter { it.role == ProductRole.DRINK }
        val combos = menu.availableCombos()
        val adult =
            (
                mains.flatMap { m -> drinks.map { m.price.value + it.price.value } } +
                    combos.filter { it.serves == 1 }.map { it.price.value }
            ).toSet()
        val kid =
            mains
                .filter { it.kidFriendly }
                .flatMap { m ->
                    drinks.filter { it.kidFriendly }.map {
                        m.price.value +
                            it.price.value
                    }
                }.toSet()
        val shared = combos.filter { it.serves == 2 }.map { it.price.value }.toSet()
        val result = mutableSetOf<Long>()
        for (pairs in 0..adults / 2) {
            var sums = setOf(0L)
            repeat(pairs) { sums = sums.plusEach(shared) }
            repeat(adults - pairs * 2) { sums = sums.plusEach(adult) }
            repeat(children) { sums = sums.plusEach(kid) }
            result += sums.flatMap { base -> extraSubtotals(adults + children).map { base + it } }
        }
        return result
    }

    private fun extraSubtotals(people: Int): Set<Long> {
        val extras =
            setOf(0L) +
                menu.products
                    .filter {
                        it.role == ProductRole.SIDE || it.role == ProductRole.DESSERT
                    }.map { it.price.value }
        var sums = setOf(0L)
        repeat(people) { sums = sums.plusEach(extras) }
        return sums
    }

    private fun Set<Long>.plusEach(other: Set<Long>): Set<Long> = flatMap { a -> other.map { a + it } }.toSet()

    private fun total(subtotal: Long): Long = PricingEngine.price(Rupiah(subtotal), rules).total.value

    private companion object {
        val GROUPS = listOf(1 to 0, 1 to 1, 2 to 1)
        const val PROPERTY_RUNS = 1_000
        const val WARM_UP = 5
        const val RUNS = 50
        const val SWAP_ROUNDS = 3
        const val JVM_LIMIT_MILLIS = 100L
    }
}
