package com.hitachi.kiosk.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.hitachi.kiosk.MainActivity
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.feature.idle.IDLE_STAFF_CORNER_TAG
import com.hitachi.kiosk.feature.idle.IDLE_TAP_TAG
import com.hitachi.kiosk.feature.idle.STAFF_HOLD_MILLIS
import com.hitachi.kiosk.feature.ordertype.orderTypeCardTag
import com.hitachi.kiosk.testing.KioskState
import com.hitachi.kiosk.testing.awaitScreen
import com.hitachi.kiosk.testing.launchKiosk
import com.hitachi.kiosk.testing.string
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * DEV-010a: walks every edge of the PRD §5.1 navigation map from Idle on. Each `go` taps one edge and checks the
 * screen it lands on. The kiosk starts bound, so the real Splash routes to Idle; the setup edges (Splash, Sign in,
 * Choose a restaurant) are walked by [com.hitachi.kiosk.feature.SetupJourneysUiTest].
 */
@HiltAndroidTest
class NavigationEdgeWalkUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject
    lateinit var activation: ActivationRepository

    @Inject
    lateinit var merchants: MerchantRepository

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hilt.inject()
        KioskState(activation, merchants).bound()
        scenario = launchKiosk()
        compose.awaitScreen("SCR-04")
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun customerFlowCoversMenuBranchesAndPayment() {
        tapToOrder()
        pickDineIn()
        go(R.string.nav_product_card, "SCR-08")
        go(R.string.nav_keep_ordering, "SCR-06")
        go(R.string.nav_meals, "SCR-07")
        go(R.string.nav_back_to_menu, "SCR-06")
        go(R.string.nav_change_order_type, "SCR-05")
        pickDineIn()
        go(R.string.nav_view_order_with_upsell, "SCR-09")
        go(R.string.nav_continue_to_order, "SCR-10")
        go(R.string.nav_add_more, "SCR-06")
        go(R.string.nav_view_order, "SCR-10")
        go(R.string.nav_pay, "SCR-11")
        go(R.string.nav_back_to_order, "SCR-10")
        go(R.string.nav_pay, "SCR-11")
        go(R.string.nav_paid, "SCR-12")
        go(R.string.nav_done, "SCR-04")
    }

    @Test
    fun itemAndComboOpenYourOrderAndMenuCancelReturnsToIdle() {
        tapToOrder()
        pickDineIn()
        go(R.string.nav_product_card, "SCR-08")
        go(R.string.nav_view_order, "SCR-10")
        go(R.string.nav_add_more, "SCR-06")
        go(R.string.nav_meals, "SCR-07")
        go(R.string.nav_my_order, "SCR-10")
        go(R.string.nav_add_more, "SCR-06")
        go(R.string.nav_cancel_order, "SCR-04")
    }

    @Test
    fun budgetPlanStepsAndBackBehaviour() {
        tapToOrder()
        pickDineIn()
        go(R.string.nav_plan_by_budget, "SCR-19")
        go(R.string.nav_see_plans, "SCR-19")
        assertText(R.string.nav_add_plan)
        go(R.string.nav_back, "SCR-19")
        assertText(R.string.nav_see_plans)
        go(R.string.nav_back, "SCR-06")
        go(R.string.nav_plan_by_budget, "SCR-19")
        go(R.string.nav_see_plans, "SCR-19")
        go(R.string.nav_add_plan, "SCR-10")
        go(R.string.nav_add_more, "SCR-06")
    }

    @Test
    fun inactivityOverlayDismissesOrReturnsToIdle() {
        tapToOrder()
        go(R.string.nav_simulate_inactivity, "SCR-13")
        go(R.string.nav_still_here, "SCR-05")
        pickDineIn()
        go(R.string.nav_simulate_inactivity, "SCR-13")
        go(R.string.nav_no_response, "SCR-04")
    }

    @Test
    fun staffAreaCoversEverySettingsPageAndChangePin() {
        holdStaffCorner()
        go(R.string.nav_exit_settings, "SCR-04")
        holdStaffCorner()
        go(R.string.nav_pin_accepted, "SCR-14")
        go(R.string.screen_settings_printer, "SCR-15")
        go(R.string.nav_back, "SCR-14")
        go(R.string.screen_settings_payment, "SCR-16")
        go(R.string.nav_back, "SCR-14")
        go(R.string.screen_settings_kiosk, "SCR-17")
        go(R.string.nav_back, "SCR-14")
        go(R.string.screen_settings_device, "SCR-18")
        go(R.string.nav_change_pin, "SCR-20")
        go(R.string.nav_pin_updated, "SCR-18")
        go(R.string.nav_back, "SCR-14")
        go(R.string.nav_exit_settings, "SCR-04")
    }

    /** Idle (SCR-04): a tap anywhere opens Order type. */
    private fun tapToOrder() = goTag(IDLE_TAP_TAG, "SCR-05")

    /** Order type (SCR-05): Dine in opens the Menu 950 ms later. */
    private fun pickDineIn() = goTag(orderTypeCardTag(OrderType.DINE_IN), "SCR-06")

    /** Idle (SCR-04): holding the staff corner for 2 s opens the staff PIN. */
    private fun holdStaffCorner() {
        compose
            .onNodeWithTag(IDLE_STAFF_CORNER_TAG)
            .performTouchInput { longClick(durationMillis = STAFF_HOLD_MILLIS + HOLD_MARGIN_MILLIS) }
        awaitOn("SCR-14")
    }

    private fun goTag(
        tag: String,
        expectedScreen: String,
    ) {
        compose.onNodeWithTag(tag).performClick()
        awaitOn(expectedScreen)
    }

    private fun awaitOn(screenId: String) {
        compose.awaitScreen(screenId)
        assertOn(screenId)
    }

    private fun go(
        @StringRes edge: Int,
        expectedScreen: String,
    ) {
        val button = compose.onNodeWithText(string(edge))
        // Screens scroll their buttons into view; the SCR-13 overlay card does not scroll.
        if (compose
                .onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(string(edge))))
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            button.performScrollTo()
        }
        button.performClick()
        compose.waitForIdle()
        assertOn(expectedScreen)
    }

    private fun assertOn(screenId: String) {
        compose.onNodeWithTag(screenTag(screenId)).assertIsDisplayed()
    }

    private companion object {
        /** Holds a little past the 2 s threshold. */
        const val HOLD_MARGIN_MILLIS = 200L
    }

    private fun assertText(
        @StringRes text: Int,
    ) {
        compose.onNodeWithText(string(text)).assertExists()
    }
}
