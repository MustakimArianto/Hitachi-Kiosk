package com.hitachi.kiosk.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
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
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.feature.budgetplan.BUDGET_PLAN_BACK_TAG
import com.hitachi.kiosk.feature.budgetplan.BUDGET_PLAN_CTA_TAG
import com.hitachi.kiosk.feature.budgetplan.budgetPlanChipTag
import com.hitachi.kiosk.feature.checkout.CHECKOUT_BACK_TAG
import com.hitachi.kiosk.feature.combo.COMBO_BACK_TAG
import com.hitachi.kiosk.feature.combo.COMBO_CART_TAG
import com.hitachi.kiosk.feature.idle.IDLE_STAFF_CORNER_TAG
import com.hitachi.kiosk.feature.idle.IDLE_TAP_TAG
import com.hitachi.kiosk.feature.idle.STAFF_HOLD_MILLIS
import com.hitachi.kiosk.feature.item.ITEM_BACK_TAG
import com.hitachi.kiosk.feature.item.ITEM_CART_TAG
import com.hitachi.kiosk.feature.menu.MENU_BUDGET_PLAN_TAG
import com.hitachi.kiosk.feature.menu.MENU_CANCEL_TAG
import com.hitachi.kiosk.feature.menu.MENU_CHANGE_TAG
import com.hitachi.kiosk.feature.menu.MENU_MEALS_PILL_TAG
import com.hitachi.kiosk.feature.menu.MENU_VIEW_ORDER_TAG
import com.hitachi.kiosk.feature.menu.menuAddTag
import com.hitachi.kiosk.feature.menu.menuChooseTag
import com.hitachi.kiosk.feature.order.YOUR_ORDER_ADD_MORE_TAG
import com.hitachi.kiosk.feature.order.YOUR_ORDER_BACK_TO_MENU_TAG
import com.hitachi.kiosk.feature.order.YOUR_ORDER_PAY_TAG
import com.hitachi.kiosk.feature.ordertype.orderTypeCardTag
import com.hitachi.kiosk.feature.upsell.UPSELL_NO_THANKS_TAG
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
 * screen it lands on: real screens by their test tags (`goTag`), placeholders by their button labels (`go`). The
 * kiosk starts bound, so the real Splash routes to Idle; the setup edges (Splash, Sign in, Choose a restaurant) are
 * walked by [com.hitachi.kiosk.feature.SetupJourneysUiTest]. The inactivity edge (SCR-13) needs real waiting, so it is
 * walked by [com.hitachi.kiosk.feature.OrderingUiTest].
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

    @Inject
    lateinit var backend: FakeBackendApi

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
        goTag(menuChooseTag("b2"), "SCR-08")
        goTag(ITEM_BACK_TAG, "SCR-06")
        goTag(MENU_MEALS_PILL_TAG, "SCR-07")
        goTag(COMBO_BACK_TAG, "SCR-06")
        goTag(MENU_CHANGE_TAG, "SCR-05")
        pickDineIn()
        goTag(menuAddTag("c1"), "SCR-06")
        // The first View order of an order goes through Upsell (FR-014); the second goes straight to Your order.
        goTag(MENU_VIEW_ORDER_TAG, "SCR-09")
        goTag(UPSELL_NO_THANKS_TAG, "SCR-10")
        goTag(YOUR_ORDER_ADD_MORE_TAG, "SCR-06")
        goTag(MENU_VIEW_ORDER_TAG, "SCR-10")
        goTag(YOUR_ORDER_PAY_TAG, "SCR-11")
        goTag(CHECKOUT_BACK_TAG, "SCR-10")
        goTag(YOUR_ORDER_PAY_TAG, "SCR-11")
        // PAID from the backend, not a button, leads to Complete (A-15).
        backend.paymentStatus.succeed(SampleData.paymentPaid)
        awaitOn("SCR-12")
        go(R.string.nav_done, "SCR-04")
    }

    @Test
    fun itemAndComboOpenYourOrderAndMenuCancelReturnsToIdle() {
        tapToOrder()
        pickDineIn()
        goTag(menuChooseTag("b2"), "SCR-08")
        goTag(ITEM_CART_TAG, "SCR-10")
        goTag(YOUR_ORDER_ADD_MORE_TAG, "SCR-06")
        goTag(MENU_MEALS_PILL_TAG, "SCR-07")
        goTag(COMBO_CART_TAG, "SCR-10")
        goTag(YOUR_ORDER_BACK_TO_MENU_TAG, "SCR-06")
        goTag(MENU_CANCEL_TAG, "SCR-04")
    }

    @Test
    fun budgetPlanStepsAndBackBehaviour() {
        tapToOrder()
        pickDineIn()
        goTag(MENU_BUDGET_PLAN_TAG, "SCR-19")
        goTag(budgetPlanChipTag(BUDGET), "SCR-19")
        goTag(BUDGET_PLAN_CTA_TAG, "SCR-19")
        assertText(R.string.budget_plan_add)
        goTag(BUDGET_PLAN_BACK_TAG, "SCR-19")
        assertText(R.string.budget_plan_see_plans)
        goTag(BUDGET_PLAN_BACK_TAG, "SCR-06")
        goTag(MENU_BUDGET_PLAN_TAG, "SCR-19")
        goTag(budgetPlanChipTag(BUDGET), "SCR-19")
        goTag(BUDGET_PLAN_CTA_TAG, "SCR-19")
        // "Added to order" shows for 1 s, then Your order opens.
        goTag(BUDGET_PLAN_CTA_TAG, "SCR-10")
        goTag(YOUR_ORDER_ADD_MORE_TAG, "SCR-06")
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
        val target = compose.onNodeWithTag(tag)
        if (compose
                .onAllNodes(
                    hasScrollAction() and hasAnyDescendant(hasTestTag(tag)),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            target.performScrollTo()
        }
        target.performClick()
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

        /** The Rp 150k chip of the sample config (FR-032 AC1). */
        const val BUDGET = 150_000L
    }

    private fun assertText(
        @StringRes text: Int,
    ) {
        compose.onNodeWithText(string(text)).assertExists()
    }
}
