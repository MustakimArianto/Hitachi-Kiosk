package com.hitachi.kiosk.feature

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.MainActivity
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.feature.menu.MENU_CANCEL_TAG
import com.hitachi.kiosk.feature.menu.MENU_CHANGE_TAG
import com.hitachi.kiosk.feature.menu.MENU_DOCK_SUMMARY_TAG
import com.hitachi.kiosk.feature.menu.MENU_MEALS_PILL_TAG
import com.hitachi.kiosk.feature.menu.menuAddTag
import com.hitachi.kiosk.feature.menu.menuChooseTag
import com.hitachi.kiosk.testing.KioskState
import com.hitachi.kiosk.testing.awaitScreen
import com.hitachi.kiosk.testing.awaitText
import com.hitachi.kiosk.testing.launchKiosk
import com.hitachi.kiosk.testing.string
import com.hitachi.kiosk.testing.tap
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * The Menu on the real app with the sample catalog (DEV-027, DEV-028; TC-028, TC-035, TC-038, TC-039, TC-175 b):
 * a bound kiosk goes Idle → Order type → Menu, quick-adds into the dock, opens Item and Combo, changes the order type
 * keeping the cart, cancels to Idle with an empty cart, and shows the offline banner.
 */
@HiltAndroidTest
class MenuUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject
    lateinit var activation: ActivationRepository

    @Inject
    lateinit var merchants: MerchantRepository

    @Inject
    lateinit var sessions: OrderSessionHolder

    @Inject
    lateinit var connectivity: FakeConnectivityMonitor

    private val state by lazy { KioskState(activation, merchants) }
    private var scenario: ActivityScenario<MainActivity>? = null

    private val disabled = SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    @Before
    fun setUp() {
        hilt.inject()
        state.bound()
        connectivity.set(Connectivity.ONLINE)
        scenario = launchKiosk()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** Idle: tap anywhere (SCR-04); Order type: "Dine in" (SCR-05); the Menu opens after 950 ms. */
    private fun openMenu() {
        compose.awaitScreen("SCR-04")
        compose.tap("screen:SCR-04")
        pickDineIn()
    }

    private fun pickDineIn() {
        compose.awaitScreen("SCR-05")
        compose.awaitText(DINE_IN)
        compose.onNodeWithText(DINE_IN, useUnmergedTree = true).performClick()
        compose.awaitScreen("SCR-06")
    }

    private fun awaitDock(count: String) {
        compose.waitUntil(DOCK_TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(MENU_DOCK_SUMMARY_TAG).assert(hasText(count)) }.isSuccess
        }
    }

    @Test
    fun quickAddMergesIntoTheDock() {
        openMenu()

        compose.tap(menuAddTag("c1"))
        awaitDock("1 item")
        compose.tap(menuAddTag("c1"))
        awaitDock("2 items")

        compose.onNodeWithTag(MENU_DOCK_SUMMARY_TAG).assert(hasText("Rp 76.000"))
        val lines =
            checkNotNull(sessions.current.value)
                .state.value.cart.lines
        assertThat(lines).hasSize(1)
        assertThat(lines.single().quantity).isEqualTo(2)
    }

    @Test
    fun chooseOpensItemDetailInsteadOfAdding() {
        openMenu()

        compose.tap(menuChooseTag("b2"))

        compose.awaitScreen("SCR-08")
        assertThat(
            checkNotNull(sessions.current.value)
                .state.value.cart
                .isEmpty(),
        ).isTrue()
    }

    @Test
    fun mealsOpensCombo() {
        openMenu()

        compose.tap(MENU_MEALS_PILL_TAG)

        compose.awaitScreen("SCR-07")
    }

    @Test
    fun changeKeepsTheCart() {
        openMenu()
        compose.tap(menuAddTag("c1"))
        awaitDock("1 item")

        compose.tap(MENU_CHANGE_TAG)
        pickDineIn()

        awaitDock("1 item")
        assertThat(
            checkNotNull(sessions.current.value)
                .state.value.cart.itemCount,
        ).isEqualTo(1)
    }

    @Test
    fun cancelReturnsToIdleWithAnEmptyCart() {
        openMenu()
        compose.tap(menuAddTag("c1"))
        awaitDock("1 item")

        compose.tap(MENU_CANCEL_TAG)

        compose.awaitScreen("SCR-04")
        compose.waitUntil(DOCK_TIMEOUT_MS) { sessions.current.value == null }
    }

    @Test
    fun offlineShowsTheBannerAndDisablesAdds() {
        openMenu()

        connectivity.set(Connectivity.OFFLINE)

        compose.awaitText(string(R.string.offline_banner))
        compose.onNodeWithTag(menuAddTag("c1")).assert(disabled)
    }

    private companion object {
        const val DINE_IN = "Dine in"
        const val DOCK_TIMEOUT_MS = 3_000L
    }
}
