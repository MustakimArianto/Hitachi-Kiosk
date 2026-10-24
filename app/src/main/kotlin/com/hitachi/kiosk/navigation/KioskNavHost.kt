package com.hitachi.kiosk.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.hitachi.kiosk.R
import com.hitachi.kiosk.feature.activation.SignInRoute as SignInFeatureRoute
import com.hitachi.kiosk.feature.budgetplan.BudgetPlanRoute as BudgetPlanFeatureRoute
import com.hitachi.kiosk.feature.checkout.CheckoutRoute as CheckoutFeatureRoute
import com.hitachi.kiosk.feature.combo.ComboRoute as ComboFeatureRoute
import com.hitachi.kiosk.feature.idle.IdleRoute as IdleFeatureRoute
import com.hitachi.kiosk.feature.item.ItemDetailRoute as ItemDetailFeatureRoute
import com.hitachi.kiosk.feature.menu.MenuRoute as MenuFeatureRoute
import com.hitachi.kiosk.feature.merchant.MerchantsRoute as MerchantsFeatureRoute
import com.hitachi.kiosk.feature.order.YourOrderRoute as YourOrderFeatureRoute
import com.hitachi.kiosk.feature.ordertype.OrderTypeRoute as OrderTypeFeatureRoute
import com.hitachi.kiosk.feature.splash.SplashRoute as SplashFeatureRoute
import com.hitachi.kiosk.feature.upsell.UpsellRoute as UpsellFeatureRoute

/**
 * The navigation graph of architecture §11 with every edge of PRD §5.1 (DEV-010a). Splash, Sign in and Choose a
 * restaurant (0.2.0), Idle, Order type, Menu, Item detail and Combo (0.3.0), and Upsell, Your order and Budget Plan
 * (0.4.0) are the real screens; every other destination is a [PlaceholderScreen] until its milestone replaces it with
 * the feature's `XxxRoute`. The SCR-13 overlay lives above this host in [KioskRoot].
 *
 * @param onBrandMarkClick taps on the splash mark (5 taps open Developer options in debug builds, DEV-009).
 */
@Composable
fun KioskNavHost(
    navController: NavHostController,
    onBrandMarkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // System Back is disabled on kiosk screens; screens navigate with their own controls (architecture §11).
    BackHandler(enabled = true) {}

    NavHost(navController = navController, startDestination = SplashRoute, modifier = modifier) {
        setupDestinations(navController, onBrandMarkClick)
        navigation<CustomerGraph>(startDestination = OrderTypeRoute) {
            orderingDestinations(navController)
            checkoutDestinations(navController)
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
        SplashFeatureRoute(
            onNavigate = { destination -> navController.replaceStack(destination.route()) },
            onMarkClick = onBrandMarkClick,
        )
    }
    composable<SignInRoute> {
        SignInFeatureRoute(
            onNavigateToMerchants = { navController.replaceStack(MerchantsRoute) },
            onNavigateToIdle = { navController.replaceStack(IdleRoute) },
        )
    }
    composable<MerchantsRoute> {
        MerchantsFeatureRoute(
            onNavigateToIdle = { navController.replaceStack(IdleRoute) },
            onNavigateToSignIn = { navController.replaceStack(SignInRoute) },
        )
    }
    composable<IdleRoute> {
        IdleFeatureRoute(
            onStartOrder = { navController.navigate(CustomerGraph) },
            onStaffEntry = { navController.navigate(StaffGraph) },
        )
    }
}

/** SCR-05…SCR-08 and SCR-19: building the order. */
private fun NavGraphBuilder.orderingDestinations(navController: NavHostController) {
    composable<OrderTypeRoute> {
        OrderTypeFeatureRoute(onNavigateToMenu = { navController.navigate(MenuRoute) })
    }
    composable<MenuRoute> {
        MenuFeatureRoute(
            onOpenProduct = { productId -> navController.navigate(ItemDetailRoute(productId = productId)) },
            onOpenCombos = { navController.navigate(ComboRoute()) },
            onOpenBudgetPlan = { navController.navigate(BudgetPlanRoute) { launchSingleTop = true } },
            // The cart is kept when the order type changes (PRD §5.1).
            onChangeOrderType = { navController.popBackStack(OrderTypeRoute, false) },
            // Entering Idle ends the order and clears the cart (FR-009 AC4).
            onCancel = { navController.popBackStack(IdleRoute, false) },
            onViewOrder = { navController.navigate(YourOrderRoute) { launchSingleTop = true } },
            onOfferUpsell = { navController.navigate(UpsellRoute) { launchSingleTop = true } },
        )
    }
    composable<ItemDetailRoute> {
        ItemDetailFeatureRoute(
            onNavigateToMenu = { navController.popBackStack(MenuRoute, false) },
            onNavigateToYourOrder = { navController.navigateToYourOrder() },
        )
    }
    composable<ComboRoute> {
        ComboFeatureRoute(
            onNavigateToMenu = { navController.popBackStack(MenuRoute, false) },
            onNavigateToYourOrder = { navController.navigateToYourOrder() },
        )
    }
    composable<BudgetPlanRoute> {
        BudgetPlanFeatureRoute(
            onNavigateToMenu = { navController.popBackStack(MenuRoute, false) },
            onNavigateToYourOrder = { navController.navigateToYourOrder() },
        )
    }
}

/** SCR-09…SCR-12: upsell, review, payment and completion. */
private fun NavGraphBuilder.checkoutDestinations(navController: NavHostController) {
    composable<UpsellRoute> {
        UpsellFeatureRoute(
            onNavigateToYourOrder = { navController.navigateToYourOrder() },
            onNavigateToMenu = { navController.popBackStack(MenuRoute, false) },
        )
    }
    composable<YourOrderRoute> {
        YourOrderFeatureRoute(
            onBackToMenu = { navController.popBackStack(MenuRoute, false) },
            // Checkout submits the order (E7) and starts the payment with the chosen method (FR-016).
            onPay = { method -> navController.navigate(CheckoutRoute(method)) { launchSingleTop = true } },
        )
    }
    composable<CheckoutRoute> {
        CheckoutFeatureRoute(
            // "Back to order", PRICE_CHANGED and ITEM_UNAVAILABLE return to Your order with the cart intact.
            onNavigateToYourOrder = { navController.popBackStack(YourOrderRoute, false) },
            // Paid: Complete replaces the order screens, so Back cannot return to Checkout (A-15).
            onNavigateToComplete = { navController.navigate(CompleteRoute) { popUpTo(IdleRoute) } },
            // "Cancel order" ends the order and returns to Idle (FR-019).
            onNavigateToIdle = { navController.popBackStack(IdleRoute, false) },
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
internal fun NavHostController.replaceStack(route: Any) {
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}

private fun StartDestination.route(): Any =
    when (this) {
        StartDestination.SIGN_IN -> SignInRoute
        StartDestination.MERCHANTS -> MerchantsRoute
        StartDestination.IDLE -> IdleRoute
    }

/** Opens Your order with Menu below it, so "Add more" and Back return to Menu (PRD §5.1). */
private fun NavHostController.navigateToYourOrder() {
    navigate(YourOrderRoute) {
        popUpTo(MenuRoute)
        launchSingleTop = true
    }
}
