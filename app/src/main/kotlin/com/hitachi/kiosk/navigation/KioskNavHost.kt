package com.hitachi.kiosk.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.hitachi.kiosk.R

/**
 * The navigation graph of architecture §11 with every edge of PRD §5.1 (DEV-010a). Each destination is a
 * [PlaceholderScreen] until its milestone replaces it with the feature's `XxxRoute`.
 *
 * @param onInactivity shows the SCR-13 overlay; offered on the screens where the inactivity timer runs (FR-023).
 * @param onBrandMarkClick taps on the splash mark (5 taps open Developer options in debug builds, DEV-009).
 */
@Composable
fun KioskNavHost(
    navController: NavHostController,
    onInactivity: () -> Unit,
    onBrandMarkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // System Back is disabled on kiosk screens; screens navigate with their own controls (architecture §11).
    BackHandler(enabled = true) {}

    NavHost(navController = navController, startDestination = SplashRoute, modifier = modifier) {
        setupDestinations(navController, onBrandMarkClick)
        navigation<CustomerGraph>(startDestination = OrderTypeRoute) {
            orderingDestinations(navController, onInactivity)
            checkoutDestinations(navController, onInactivity)
        }
        navigation<StaffGraph>(startDestination = StaffPinRoute) {
            staffDestinations(navController)
        }
    }
}

private fun NavGraphBuilder.setupDestinations(
    navController: NavHostController,
    onBrandMarkClick: () -> Unit,
) {
    composable<SplashRoute> {
        // StartupRouter (DEV-011) picks one of these automatically from 0.2.0.
        PlaceholderScreen(
            screenId = "SCR-01",
            title = R.string.screen_splash,
            onMarkClick = onBrandMarkClick,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_not_activated) { navController.replaceStack(SignInRoute) },
                    PlaceholderAction(
                        R.string.nav_activated_no_merchant,
                    ) { navController.replaceStack(MerchantsRoute) },
                    PlaceholderAction(R.string.nav_merchant_bound) { navController.replaceStack(IdleRoute) },
                ),
        )
    }
    composable<SignInRoute> {
        PlaceholderScreen(
            screenId = "SCR-02",
            title = R.string.screen_sign_in,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_activation_ok) { navController.replaceStack(MerchantsRoute) },
                ),
        )
    }
    composable<MerchantsRoute> {
        PlaceholderScreen(
            screenId = "SCR-03",
            title = R.string.screen_merchants,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_pick_merchant) { navController.replaceStack(IdleRoute) },
                    PlaceholderAction(R.string.nav_log_out) { navController.replaceStack(SignInRoute) },
                ),
        )
    }
    composable<IdleRoute> {
        PlaceholderScreen(
            screenId = "SCR-04",
            title = R.string.screen_idle,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_tap_anywhere) { navController.navigate(CustomerGraph) },
                    PlaceholderAction(R.string.nav_hold_staff_entry) { navController.navigate(StaffGraph) },
                ),
        )
    }
}

/** SCR-05…SCR-08 and SCR-19: building the order. */
private fun NavGraphBuilder.orderingDestinations(
    navController: NavHostController,
    onInactivity: () -> Unit,
) {
    val inactivity = PlaceholderAction(R.string.nav_simulate_inactivity, onInactivity)
    composable<OrderTypeRoute> {
        PlaceholderScreen(
            screenId = "SCR-05",
            title = R.string.screen_order_type,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_pick_order_type) { navController.navigate(MenuRoute) },
                    inactivity,
                ),
        )
    }
    composable<MenuRoute> {
        PlaceholderScreen(
            screenId = "SCR-06",
            title = R.string.screen_menu,
            actions = menuActions(navController, inactivity),
        )
    }
    composable<ItemDetailRoute> {
        PlaceholderScreen(
            screenId = "SCR-08",
            title = R.string.screen_item,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_keep_ordering) { navController.popBackStack(MenuRoute, false) },
                    PlaceholderAction(R.string.nav_view_order) { navController.navigateToYourOrder() },
                    inactivity,
                ),
        )
    }
    composable<ComboRoute> {
        PlaceholderScreen(
            screenId = "SCR-07",
            title = R.string.screen_combo,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_back_to_menu) { navController.popBackStack(MenuRoute, false) },
                    PlaceholderAction(R.string.nav_my_order) { navController.navigateToYourOrder() },
                    inactivity,
                ),
        )
    }
    composable<BudgetPlanRoute> {
        BudgetPlanPlaceholder(navController, inactivity)
    }
}

/** The Menu's edges (PRD §5.1): it branches to every other ordering screen. */
private fun menuActions(
    navController: NavHostController,
    inactivity: PlaceholderAction,
): List<PlaceholderAction> =
    listOf(
        PlaceholderAction(R.string.nav_product_card) { navController.navigate(ItemDetailRoute) },
        PlaceholderAction(R.string.nav_plan_by_budget) { navController.navigate(BudgetPlanRoute) },
        PlaceholderAction(R.string.nav_meals) { navController.navigate(ComboRoute) },
        // The cart is kept when the order type changes (PRD §5.1).
        PlaceholderAction(R.string.nav_change_order_type) { navController.popBackStack(OrderTypeRoute, false) },
        PlaceholderAction(R.string.nav_cancel_order) { navController.popBackStack(IdleRoute, false) },
        PlaceholderAction(R.string.nav_view_order_with_upsell) { navController.navigate(UpsellRoute) },
        PlaceholderAction(R.string.nav_view_order) { navController.navigate(YourOrderRoute) },
        inactivity,
    )

/** SCR-09…SCR-12: upsell, review, payment and completion. */
private fun NavGraphBuilder.checkoutDestinations(
    navController: NavHostController,
    onInactivity: () -> Unit,
) {
    val inactivity = PlaceholderAction(R.string.nav_simulate_inactivity, onInactivity)
    composable<UpsellRoute> {
        PlaceholderScreen(
            screenId = "SCR-09",
            title = R.string.screen_upsell,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_continue_to_order) { navController.navigateToYourOrder() },
                    inactivity,
                ),
        )
    }
    composable<YourOrderRoute> {
        PlaceholderScreen(
            screenId = "SCR-10",
            title = R.string.screen_your_order,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_add_more) { navController.popBackStack(MenuRoute, false) },
                    PlaceholderAction(R.string.nav_pay) { navController.navigate(CheckoutRoute) },
                    inactivity,
                ),
        )
    }
    composable<CheckoutRoute> {
        PlaceholderScreen(
            screenId = "SCR-11",
            title = R.string.screen_checkout,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_back_to_order) { navController.popBackStack(YourOrderRoute, false) },
                    PlaceholderAction(R.string.nav_paid) {
                        navController.navigate(CompleteRoute) { popUpTo(IdleRoute) }
                    },
                ),
        )
    }
    composable<CompleteRoute> {
        PlaceholderScreen(
            screenId = "SCR-12",
            title = R.string.screen_complete,
            actions = listOf(PlaceholderAction(R.string.nav_done) { navController.popBackStack(IdleRoute, false) }),
        )
    }
}

@Composable
private fun BudgetPlanPlaceholder(
    navController: NavHostController,
    inactivity: PlaceholderAction,
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    val back = {
        // Back from step 2 returns to step 1, Back from step 1 returns to Menu (architecture §11).
        if (step == 2) step = 1 else navController.popBackStack(MenuRoute, false)
    }
    BackHandler(onBack = { back() })
    val stepName = stringResource(if (step == 1) R.string.budget_plan_step_table else R.string.budget_plan_step_plan)
    PlaceholderScreen(
        screenId = "SCR-19",
        title = R.string.screen_budget_plan,
        subtitle = stringResource(R.string.budget_plan_step, step, stepName),
        actions =
            buildList {
                if (step == 1) {
                    add(PlaceholderAction(R.string.nav_see_plans) { step = 2 })
                } else {
                    add(PlaceholderAction(R.string.nav_add_plan) { navController.navigateToYourOrder() })
                }
                add(PlaceholderAction(R.string.nav_back) { back() })
                add(inactivity)
            },
    )
}

private fun NavGraphBuilder.staffDestinations(navController: NavHostController) {
    composable<StaffPinRoute> {
        PlaceholderScreen(
            screenId = "SCR-14",
            title = R.string.screen_staff_pin,
            actions =
                listOf(
                    // The PIN gate is not kept on the back stack.
                    PlaceholderAction(R.string.nav_pin_accepted) {
                        navController.navigate(SettingsHomeRoute) { popUpTo(StaffPinRoute) { inclusive = true } }
                    },
                    PlaceholderAction(R.string.nav_exit_settings) { navController.popBackStack(IdleRoute, false) },
                ),
        )
    }
    composable<SettingsHomeRoute> {
        PlaceholderScreen(
            screenId = "SCR-14",
            title = R.string.screen_settings_home,
            actions =
                listOf(
                    PlaceholderAction(
                        R.string.screen_settings_printer,
                    ) { navController.navigate(PrinterSettingsRoute) },
                    PlaceholderAction(
                        R.string.screen_settings_payment,
                    ) { navController.navigate(PaymentSettingsRoute) },
                    PlaceholderAction(R.string.screen_settings_kiosk) { navController.navigate(KioskSettingsRoute) },
                    PlaceholderAction(R.string.screen_settings_device) { navController.navigate(DeviceSettingsRoute) },
                    PlaceholderAction(R.string.nav_exit_settings) { navController.popBackStack(IdleRoute, false) },
                ),
        )
    }
    settingsPage<PrinterSettingsRoute>(navController, "SCR-15", R.string.screen_settings_printer)
    settingsPage<PaymentSettingsRoute>(navController, "SCR-16", R.string.screen_settings_payment)
    settingsPage<KioskSettingsRoute>(navController, "SCR-17", R.string.screen_settings_kiosk)
    composable<DeviceSettingsRoute> {
        PlaceholderScreen(
            screenId = "SCR-18",
            title = R.string.screen_settings_device,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_change_pin) { navController.navigate(ChangePinRoute) },
                    PlaceholderAction(R.string.nav_back) { navController.popBackStack(SettingsHomeRoute, false) },
                ),
        )
    }
    composable<ChangePinRoute> {
        PlaceholderScreen(
            screenId = "SCR-20",
            title = R.string.screen_change_pin,
            actions =
                listOf(
                    PlaceholderAction(
                        R.string.nav_pin_updated,
                    ) { navController.popBackStack(DeviceSettingsRoute, false) },
                ),
        )
    }
}

private inline fun <reified T : Any> NavGraphBuilder.settingsPage(
    navController: NavHostController,
    screenId: String,
    title: Int,
) {
    composable<T> {
        PlaceholderScreen(
            screenId = screenId,
            title = title,
            actions =
                listOf(
                    PlaceholderAction(R.string.nav_back) { navController.popBackStack(SettingsHomeRoute, false) },
                ),
        )
    }
}

/** Replaces the whole back stack, as after startup routing, activation, binding and Log out (FR-001, FR-004). */
private fun NavHostController.replaceStack(route: Any) {
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}

/** Opens Your order with Menu below it, so "Add more" and Back return to Menu (PRD §5.1). */
private fun NavHostController.navigateToYourOrder() {
    navigate(YourOrderRoute) { popUpTo(MenuRoute) }
}
