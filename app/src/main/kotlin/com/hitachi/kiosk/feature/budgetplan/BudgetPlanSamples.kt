package com.hitachi.kiosk.feature.budgetplan

import com.hitachi.kiosk.core.domain.planner.PlanKind

/**
 * Preview states mirroring the `BudgetPas.dc.html` scenarios (2 adults + 1 child at Rp 150.000, 4 items in the order).
 * Screenshot tests build their states through the ViewModel, the real planner and the sample menu instead.
 */
internal object BudgetPlanSamples {
    private val chips =
        listOf(50_000L, 100_000L, 150_000L, 200_000L).map {
            BudgetChipUi(it, it / 1_000, "Rp ${it / 1_000}.000", selected = it == 150_000L)
        }

    val StepOne =
        BudgetPlanUiState(
            loaded = true,
            budget = "Rp 150.000",
            hasBudget = true,
            perPerson = "Rp 50.000",
            chips = chips,
            adults = 2,
            children = 1,
            canSeePlans = true,
            cartCount = 4,
        )

    private fun item(
        name: String,
        meta: PlanItemMeta,
        price: String,
        index: Int,
    ) = PlanItemUi(name, meta, price, imageUrl = null, tone = "#E9DED0", swap = SwapTarget(index))

    private val groups =
        listOf(
            PlanGroupUi(
                PlanGroupLabel.Adult(1),
                listOf(item("Smoky Double Burger + Iced Lychee Tea", PlanItemMeta.MainAndDrink, "Rp 68.000", 0)),
            ),
            PlanGroupUi(
                PlanGroupLabel.Adult(2),
                listOf(item("Cheeseburger Meal", PlanItemMeta.Meal("fries, cola"), "Rp 56.000", 1)),
            ),
            PlanGroupUi(
                PlanGroupLabel.Child(1, kidFriendly = true),
                listOf(item("Chicken Nuggets + Mineral Water", PlanItemMeta.MainAndDrink, "Rp 40.000", 2)),
            ),
        )

    private val plan =
        PlanUi(
            title = PlanTitle.TWO_PLANS,
            cards =
                listOf(
                    PlanCardUi(PlanSide.UNDER, "Rp 149.600", "Rp 400", items = 5, selected = true),
                    PlanCardUi(PlanSide.OVER, "Rp 150.700", "Rp 700", items = 6, selected = false),
                ),
            hero = HeroUi(PlanKind.UNDER, "Rp 149.600", "Rp 400"),
            notes = emptyList(),
            options =
                PlanOptionsUi(
                    includeSides = true,
                    includeDesserts = true,
                    allowOver = true,
                    overCapPercent = 10,
                    canReset = false,
                ),
            groups = groups,
            itemCount = 5,
            price =
                PriceDetailsUi(
                    subtotal = "Rp 146.000",
                    promoLabel = "Rp 10.000 off over Rp 100.000",
                    discount = "Rp 10.000",
                    taxLabel = "Restaurant tax (PB1 10%)",
                    tax = "Rp 13.600",
                    rounding = null,
                    comboSavings = "Rp 14.000",
                    total = "Rp 149.600",
                ),
        )

    val StepTwo = StepOne.copy(step = BudgetPlanStep.PLAN, plan = plan)

    val Exact =
        StepTwo.copy(
            budget = "Rp 99.000",
            adults = 1,
            plan =
                plan.copy(
                    title = PlanTitle.PERFECT_FIT,
                    cards = emptyList(),
                    hero = HeroUi(PlanKind.EXACT, "Rp 99.000", "Rp 0"),
                    groups = groups.drop(1),
                    itemCount = 3,
                ),
        )

    val TooLow =
        StepTwo.copy(
            budget = "Rp 100.000",
            plan =
                plan.copy(
                    title = PlanTitle.YOUR_PLAN,
                    cards = emptyList(),
                    hero = HeroUi(PlanKind.TOO_LOW, "Rp 116.600", "Rp 16.600"),
                    notes = listOf(PlanNote.TooLow),
                ),
        )

    val Group =
        StepOne.copy(
            budget = "Rp 200.000",
            adults = 8,
            children = 4,
            groupTooLarge = true,
            canSeePlans = false,
        )
}
