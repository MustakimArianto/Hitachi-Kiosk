package com.hitachi.kiosk.feature.budgetplan

import com.hitachi.kiosk.core.domain.planner.PlanKind
import com.hitachi.kiosk.core.domain.planner.SwapPart

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
        swap: SwapTarget?,
    ) = PlanItemUi(name, meta, price, imageUrl = null, tone = "#E9DED0", swap = swap)

    private val groups =
        listOf(
            PlanGroupUi(
                PlanGroupLabel.Adult(1),
                listOf(
                    item("Smoky Double Burger", PlanItemMeta.Main, "Rp 52.000", SwapTarget(0, SwapPart.MAIN)),
                    item("Iced Lychee Tea", PlanItemMeta.Drink, "Rp 16.000", SwapTarget(0, SwapPart.DRINK)),
                ),
            ),
            PlanGroupUi(
                PlanGroupLabel.Adult(2),
                listOf(
                    item(
                        "Cheeseburger Meal",
                        PlanItemMeta.Meal("Cheeseburger · fries · cola"),
                        "Rp 56.000",
                        SwapTarget(1, SwapPart.MEAL),
                    ),
                ),
            ),
            PlanGroupUi(
                PlanGroupLabel.Child(1),
                listOf(
                    item("Chicken Nuggets (9 pc)", PlanItemMeta.KidMain, "Rp 32.000", SwapTarget(2, SwapPart.MAIN)),
                    item("Mineral Water 600 ml", PlanItemMeta.Drink, "Rp 8.000", SwapTarget(2, SwapPart.DRINK)),
                ),
            ),
        )

    private val plan =
        PlanUi(
            title = PlanTitle.TWO_PLANS,
            options =
                listOf(
                    PlanOptionUi(PlanSide.UNDER, "Rp 149.600", selected = true),
                    PlanOptionUi(PlanSide.OVER, "Rp 150.700", selected = false),
                ),
            hero = HeroUi(PlanKind.UNDER, "Rp 149.600", "Rp 400"),
            notes = emptyList(),
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
                    options = emptyList(),
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
                    options = emptyList(),
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
