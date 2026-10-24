package com.hitachi.kiosk.feature

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.MainActivity
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import com.hitachi.kiosk.feature.budgetplan.BUDGET_PLAN_ADULTS_MORE_TAG
import com.hitachi.kiosk.feature.budgetplan.BUDGET_PLAN_CHILDREN_MORE_TAG
import com.hitachi.kiosk.feature.budgetplan.BUDGET_PLAN_CTA_TAG
import com.hitachi.kiosk.feature.budgetplan.budgetPlanChipTag
import com.hitachi.kiosk.feature.menu.MENU_BUDGET_PLAN_TAG
import com.hitachi.kiosk.feature.menu.MENU_VIEW_ORDER_TAG
import com.hitachi.kiosk.feature.menu.menuAddTag
import com.hitachi.kiosk.feature.order.YOUR_ORDER_ADD_MORE_TAG
import com.hitachi.kiosk.feature.order.YOUR_ORDER_PAY_TAG
import com.hitachi.kiosk.feature.order.YOUR_ORDER_PLAN_TAG
import com.hitachi.kiosk.feature.order.YOUR_ORDER_TOTAL_TAG
import com.hitachi.kiosk.feature.order.yourOrderDecrementTag
import com.hitachi.kiosk.feature.order.yourOrderIncrementTag
import com.hitachi.kiosk.feature.ordertype.orderTypeCardTag
import com.hitachi.kiosk.feature.upsell.UPSELL_CONTINUE_TAG
import com.hitachi.kiosk.feature.upsell.UPSELL_NO_THANKS_TAG
import com.hitachi.kiosk.feature.upsell.upsellAddTag
import com.hitachi.kiosk.inactivity.STILL_THERE_TAG
import com.hitachi.kiosk.navigation.screenTag
import com.hitachi.kiosk.testing.KioskState
import com.hitachi.kiosk.testing.SCREEN_TIMEOUT_MS
import com.hitachi.kiosk.testing.awaitScreen
import com.hitachi.kiosk.testing.awaitText
import com.hitachi.kiosk.testing.launchKiosk
import com.hitachi.kiosk.testing.string
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * The 0.4.0 ordering journeys on the real screens (FR-012–FR-015, FR-023, FR-032): Upsell once per order, Your order
 * edits down to the empty tray, a Budget Plan in the cart, and the inactivity overlay with the real timer. The
 * device settings live in a real DataStore, so every test restores the defaults.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class OrderingUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject
    lateinit var activation: ActivationRepository

    @Inject
    lateinit var merchants: MerchantRepository

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var orders: OrderSessionHolder

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hilt.inject()
        KioskState(activation, merchants).bound()
        setSettings(KioskSettings())
        scenario = launchKiosk()
        compose.awaitScreen("SCR-04")
    }

    @After
    fun tearDown() {
        scenario.close()
        setSettings(KioskSettings())
    }

    @Test
    fun upsellShowsOnceAndQuickAddsWithDefaults() {
        startOrder()
        tapTag(menuAddTag("c1"))
        tapTag(MENU_VIEW_ORDER_TAG)
        compose.awaitScreen("SCR-09")

        // TC-064: French Fries is suggested with its default size and lands in the cart.
        tapTag(upsellAddTag("s1"))
        tapTag(UPSELL_CONTINUE_TAG)
        compose.awaitScreen("SCR-10")
        compose.awaitText("French Fries")

        // TC-063: once per order.
        tapTag(YOUR_ORDER_ADD_MORE_TAG)
        compose.awaitScreen("SCR-06")
        tapTag(MENU_VIEW_ORDER_TAG)
        compose.awaitScreen("SCR-10")
        assertThat(
            orders.current.value
                ?.state
                ?.value
                ?.cart
                ?.itemCount,
        ).isEqualTo(2)
    }

    @Test
    fun upsellIsSkippedWhenTurnedOff() {
        setSettings(KioskSettings(upsellEnabled = false))
        startOrder()
        tapTag(menuAddTag("c1"))
        tapTag(MENU_VIEW_ORDER_TAG)

        // TC-062.
        compose.awaitScreen("SCR-10")
    }

    @Test
    fun yourOrderEditsLinesDownToTheEmptyTray() {
        startOrder()
        tapTag(menuAddTag("c1"))
        tapTag(MENU_VIEW_ORDER_TAG)
        tapTag(UPSELL_NO_THANKS_TAG)
        compose.awaitScreen("SCR-10")

        // TC-050, TC-066: 2 × Rp 38.000 = Rp 76.000, PB1 10 % → Rp 83.600, and Pay says so.
        tapTag(yourOrderIncrementTag("l1"))
        compose.onNodeWithTag(YOUR_ORDER_TOTAL_TAG).assertTextContains("Rp 83.600", substring = true)
        compose.onNodeWithTag(YOUR_ORDER_PAY_TAG).assertTextContains("Rp 83.600", substring = true)

        // TC-051, TC-053: minus at 1 removes the line; the empty tray disables Pay.
        tapTag(yourOrderDecrementTag("l1"))
        tapTag(yourOrderDecrementTag("l1"))
        compose.awaitText(string(R.string.your_order_empty_title))
        compose.onNodeWithTag(YOUR_ORDER_PAY_TAG).assertIsNotEnabled()
    }

    @Test
    fun aBudgetPlanLandsInTheCartWithItsTotal() {
        startOrder()
        tapTag(MENU_BUDGET_PLAN_TAG)
        compose.awaitScreen("SCR-19")
        tapTag(budgetPlanChipTag(150_000))
        tapTag(BUDGET_PLAN_ADULTS_MORE_TAG)
        tapTag(BUDGET_PLAN_CHILDREN_MORE_TAG)
        tapTag(BUDGET_PLAN_CTA_TAG)
        compose.awaitText("Rp 149.600")

        // TC-167, FR-032 AC5: "Add plan to order", then Your order after 1 s with the plan tag and the plan's total.
        tapTag(BUDGET_PLAN_CTA_TAG)
        compose.awaitScreen("SCR-10")
        compose.onNodeWithTag(YOUR_ORDER_PLAN_TAG).assertExists()
        compose.onNodeWithTag(YOUR_ORDER_TOTAL_TAG).assertTextContains("Rp 149.600", substring = true)
    }

    @Test
    fun inactivityShowsTheOverlayThenReturnsToIdle() {
        setSettings(KioskSettings(idleTimeoutSeconds = 30))
        startOrder()
        tapTag(menuAddTag("c1"))

        // TC-101: the overlay after 30 s; TC-102: Continue ordering keeps the cart.
        compose.waitUntilAtLeastOneExists(hasTestTag(STILL_THERE_TAG), OVERLAY_WAIT_MS)
        compose.onNodeWithText(string(R.string.still_there_continue)).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(STILL_THERE_TAG), SCREEN_TIMEOUT_MS)
        compose.onNodeWithTag(screenTag("SCR-06")).assertIsDisplayed()

        // TC-103: ignored, it returns to Idle 15 s later with the order gone.
        compose.waitUntilAtLeastOneExists(hasTestTag(STILL_THERE_TAG), OVERLAY_WAIT_MS)
        compose.awaitScreen("SCR-04", timeoutMillis = COUNTDOWN_WAIT_MS)
        assertThat(orders.current.value).isNull()
    }

    @Test
    fun checkoutNeverShowsTheOverlay() {
        setSettings(KioskSettings(idleTimeoutSeconds = 30))
        startOrder()
        tapTag(menuAddTag("c1"))
        tapTag(MENU_VIEW_ORDER_TAG)
        tapTag(UPSELL_NO_THANKS_TAG)
        tapTag(YOUR_ORDER_PAY_TAG)
        compose.awaitScreen("SCR-11")

        // TC-104 (Checkout part): past the timeout, still no overlay.
        Thread.sleep(OVERLAY_WAIT_MS)
        compose.onNodeWithTag(screenTag("SCR-11")).assertIsDisplayed()
        assertThat(compose.onAllNodes(hasTestTag(STILL_THERE_TAG)).fetchSemanticsNodes()).isEmpty()
    }

    /** Idle → Order type (Dine in) → Menu. */
    private fun startOrder() {
        compose.onNodeWithTag(screenTag("SCR-04")).performClick()
        compose.awaitScreen("SCR-05")
        tapTag(orderTypeCardTag(OrderType.DINE_IN))
        compose.awaitScreen("SCR-06")
    }

    /** Waits for the node tagged [tag] and taps it, scrolling to it first when it sits in a scrollable container. */
    private fun tapTag(tag: String) {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), SCREEN_TIMEOUT_MS)
        val node = compose.onNodeWithTag(tag)
        if (compose
                .onAllNodes(
                    hasScrollAction() and hasAnyDescendant(hasTestTag(tag)),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            node.performScrollTo()
        }
        node.performClick()
        compose.waitForIdle()
    }

    private fun setSettings(value: KioskSettings) = runBlocking { settings.update { value } }

    private companion object {
        /** The 30 s timeout plus a margin (FR-023 AC1 allows ± 1 s). */
        const val OVERLAY_WAIT_MS = 35_000L

        /** The 15 s countdown plus a margin. */
        const val COUNTDOWN_WAIT_MS = 20_000L
    }
}
