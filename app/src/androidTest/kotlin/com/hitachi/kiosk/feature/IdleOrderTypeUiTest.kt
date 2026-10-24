package com.hitachi.kiosk.feature

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.MainActivity
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.feature.idle.IDLE_TAP_TAG
import com.hitachi.kiosk.feature.ordertype.orderTypeCardTag
import com.hitachi.kiosk.testing.KioskState
import com.hitachi.kiosk.testing.awaitScreen
import com.hitachi.kiosk.testing.launchKiosk
import com.hitachi.kiosk.testing.tap
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * The start of journey J1 on the real Idle and Order type screens (DEV-024, DEV-025; TC-021, TC-023, TC-025): a bound
 * kiosk shows Idle, a tap opens Order type, Dine in starts the order and opens the Menu, and Cancel back to Idle ends
 * the order so the next one starts with an empty cart and no order type.
 */
@HiltAndroidTest
class IdleOrderTypeUiTest {
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
    fun j1_tapToOrderThenDineInOpensTheMenu() {
        compose.tap(IDLE_TAP_TAG)
        compose.awaitScreen("SCR-05")

        compose.tap(orderTypeCardTag(OrderType.DINE_IN))
        compose.awaitScreen("SCR-06")

        assertThat(
            sessions.current.value!!
                .state.value.orderType,
        ).isEqualTo(OrderType.DINE_IN)
    }

    @Test
    fun tc023_cancellingBackToIdleEndsTheOrderAndItsCart() {
        compose.tap(IDLE_TAP_TAG)
        compose.awaitScreen("SCR-05")
        compose.tap(orderTypeCardTag(OrderType.TAKEAWAY))
        compose.awaitScreen("SCR-06")
        val session = sessions.current.value!!
        val fries = ProductCustomization.defaults(SampleData.menu.product("s1")!!, SampleData.menu)
        session.add(fries.toCartItem(), quantity = 3, unitPrice = fries.unitPrice, choices = fries.choices())
        assertThat(session.state.value.cart.itemCount).isEqualTo(3)

        compose.onNodeWithText(CANCEL, useUnmergedTree = true).performClick()
        compose.awaitScreen("SCR-04")
        compose.waitForIdle()
        assertThat(sessions.current.value).isNull()

        compose.tap(IDLE_TAP_TAG)
        compose.awaitScreen("SCR-05")
        assertThat(sessions.current.value).isNull()
    }

    private companion object {
        /** The Menu header's Cancel (`Menu.dc.html`), which returns to Idle. */
        const val CANCEL = "Cancel"
    }
}
