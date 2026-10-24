package com.hitachi.kiosk.feature

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.MainActivity
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.feature.combo.COMBO_ADD_TAG
import com.hitachi.kiosk.feature.item.ITEM_ADD_TAG
import com.hitachi.kiosk.feature.item.ITEM_VIEW_ORDER_TAG
import com.hitachi.kiosk.feature.item.itemOptionTag
import com.hitachi.kiosk.navigation.screenTag
import com.hitachi.kiosk.testing.KioskState
import com.hitachi.kiosk.testing.SCREEN_TIMEOUT_MS
import com.hitachi.kiosk.testing.awaitScreen
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
 * Item detail and Combo on the real screens with the sample menu (DEV-029, DEV-030; TC-040, TC-043, TC-044, TC-046,
 * TC-049). The kiosk starts bound; the order is reached through Idle, Order type and Menu by their visible copy.
 */
@HiltAndroidTest
class ItemComboUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject
    lateinit var activation: ActivationRepository

    @Inject
    lateinit var merchants: MerchantRepository

    @Inject
    lateinit var orders: OrderSessionHolder

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

    /** Idle → Order type (Dine in) → Menu. */
    private fun startOrder() {
        compose.onNodeWithTag(screenTag("SCR-04")).performClick()
        compose.awaitScreen("SCR-05")
        tapText(DINE_IN)
        compose.awaitScreen("SCR-06")
    }

    private fun tapText(text: String) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onAllNodes(hasText(text))
            .onFirst()
            .performScrollTo()
            .performClick()
    }

    @Test
    fun itemNeedsASizeThenAddsOneLineAndOpensYourOrder() {
        startOrder()
        tapText(SMOKY_DOUBLE)
        compose.awaitScreen("SCR-08")

        // TC-044: the size is required.
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose
                .onAllNodes(hasText(string(R.string.item_cta_to_continue, "Choose a size")))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag(itemOptionTag("mg_b2_size", "opt_b2_regular")).performScrollTo().performClick()
        compose.tap(ITEM_ADD_TAG)

        // TC-043: one line, then the Added dock.
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(hasTestTag(ITEM_VIEW_ORDER_TAG)).fetchSemanticsNodes().isNotEmpty()
        }
        val lines =
            orders.current.value
                ?.state
                ?.value
                ?.cart
                ?.lines
                .orEmpty()
        assertThat(lines.map { (it.item as CartItem.ProductItem).productId }).containsExactly("b2")
        compose.tap(ITEM_VIEW_ORDER_TAG)
        compose.awaitScreen("SCR-10")
    }

    @Test
    fun comboAddsOnceOnADoubleTap() {
        startOrder()
        tapText(MEALS)
        compose.awaitScreen("SCR-07")

        compose
            .onNodeWithTag(COMBO_ADD_TAG)
            .performClick()
            .performClick()
        compose.waitForIdle()

        val lines =
            orders.current.value
                ?.state
                ?.value
                ?.cart
                ?.lines
                .orEmpty()
        assertThat(lines).hasSize(1)
        assertThat(lines.single().quantity).isEqualTo(1)
        assertThat((lines.single().item as CartItem.ComboItem).comboId).isEqualTo("combo_burger")
    }

    private companion object {
        const val DINE_IN = "Dine in"
        const val SMOKY_DOUBLE = "Smoky Double Burger"
        const val MEALS = "Meals"
    }
}
