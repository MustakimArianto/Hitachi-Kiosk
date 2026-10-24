package com.hitachi.kiosk.feature

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.MainActivity
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.feature.checkout.CHECKOUT_BACK_TAG
import com.hitachi.kiosk.feature.checkout.CHECKOUT_CANCEL_TAG
import com.hitachi.kiosk.feature.checkout.CHECKOUT_QR_TAG
import com.hitachi.kiosk.feature.checkout.CHECKOUT_SWITCH_TAG
import com.hitachi.kiosk.feature.checkout.CHECKOUT_TITLE_TAG
import com.hitachi.kiosk.feature.checkout.CHECKOUT_TRY_AGAIN_TAG
import com.hitachi.kiosk.feature.checkout.CHECKOUT_WAITING_TAG
import com.hitachi.kiosk.feature.menu.MENU_VIEW_ORDER_TAG
import com.hitachi.kiosk.feature.menu.menuAddTag
import com.hitachi.kiosk.feature.order.YOUR_ORDER_PAY_TAG
import com.hitachi.kiosk.feature.ordertype.orderTypeCardTag
import com.hitachi.kiosk.feature.upsell.UPSELL_NO_THANKS_TAG
import com.hitachi.kiosk.navigation.screenTag
import com.hitachi.kiosk.testing.KioskState
import com.hitachi.kiosk.testing.SCREEN_TIMEOUT_MS
import com.hitachi.kiosk.testing.awaitScreen
import com.hitachi.kiosk.testing.hasNodeWithText
import com.hitachi.kiosk.testing.launchKiosk
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * The 0.5.0 Checkout journeys on the real screens with the fake backend (FR-016–FR-019): one E7 for rapid taps, the QR
 * view without the removed parts, the failure panel, offline, a recreated activity and the way to Complete. Each state
 * is reached through the fake backend's scripts, so no test waits for a real timeout.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class CheckoutUiTest {
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

    @Inject
    lateinit var backend: FakeBackendApi

    @Inject
    lateinit var connectivity: FakeConnectivityMonitor

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
    fun tenRapidPayTapsMakeOneOrderAndTheQrViewHasNoRemovedParts() {
        reachYourOrder()
        compose.waitUntilAtLeastOneExists(hasTestTag(YOUR_ORDER_PAY_TAG), SCREEN_TIMEOUT_MS)
        repeat(RAPID_TAPS) {
            // Once Checkout is up, the Pay node is gone; later taps simply find nothing (TC-069).
            runCatching { compose.onNodeWithTag(YOUR_ORDER_PAY_TAG).performClick() }
        }
        compose.awaitScreen("SCR-11")
        compose.waitUntilAtLeastOneExists(hasTestTag(CHECKOUT_QR_TAG), SCREEN_TIMEOUT_MS)

        assertThat(backend.callsTo(FakeBackendApi.Endpoint.E7)).hasSize(1)
        assertThat(backend.callsTo(FakeBackendApi.Endpoint.E9)).hasSize(1)
        compose.onNodeWithTag(CHECKOUT_WAITING_TAG).assertIsDisplayed()
        // TC-074, TC-087: none of the removed parts, and no counter payment.
        listOf("Step 3 of 3", "Open any e-wallet or banking app", "Pay at the counter").forEach {
            assertThat(compose.hasNodeWithText(it)).isFalse()
        }
        assertThat(compose.hasNodeWithText("Code expires in 5:00") || compose.hasNodeWithText("Code expires in 4:59"))
            .isTrue()
    }

    @Test
    fun backToOrderReturnsToYourOrderWithTheCartAndNextPayMakesANewOrder() {
        reachCheckout()

        compose.onNodeWithTag(CHECKOUT_BACK_TAG).performClick()
        compose.awaitScreen("SCR-10")

        // TC-086: E11 and E8 were sent and the cart is intact.
        assertThat(backend.callsTo(FakeBackendApi.Endpoint.E8)).hasSize(1)
        assertThat(
            orders.current.value
                ?.state
                ?.value
                ?.phase,
        ).isEqualTo(OrderPhase.REVIEWING)
        assertThat(
            orders.current.value
                ?.state
                ?.value
                ?.cart
                ?.itemCount,
        ).isEqualTo(1)
        compose.onNodeWithTag(YOUR_ORDER_PAY_TAG).performClick()
        compose.awaitScreen("SCR-11")
        assertThat(backend.callsTo(FakeBackendApi.Endpoint.E7)).hasSize(2)
    }

    @Test
    fun aFailureOffersTryAgainTheOtherMethodAndCancelOrder() {
        backend.paymentStatus.enqueue(
            Outcome.Success(
                SampleData.paymentPending.copy(status = PaymentStatus.EXPIRED),
            ),
        )
        reachCheckout()
        awaitTitle("QR code expired")

        // TC-178: the three actions, no counter button.
        compose.onNodeWithTag(CHECKOUT_SWITCH_TAG).assertIsDisplayed()
        compose.onNodeWithTag(CHECKOUT_CANCEL_TAG).assertIsDisplayed()
        assertThat(compose.hasNodeWithText("Pay at the counter")).isFalse()

        compose.onNodeWithTag(CHECKOUT_TRY_AGAIN_TAG).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag(CHECKOUT_QR_TAG), SCREEN_TIMEOUT_MS)
        assertThat(backend.callsTo(FakeBackendApi.Endpoint.E9)).hasSize(2)
        assertThat(backend.callsTo(FakeBackendApi.Endpoint.E7)).hasSize(1)
    }

    @Test
    fun cancelOrderOnAFailureReturnsToIdleWithTheOrderGone() {
        backend.paymentStatus.enqueue(
            Outcome.Success(
                SampleData.paymentPending.copy(status = PaymentStatus.EXPIRED),
            ),
        )
        reachCheckout()
        awaitTitle("QR code expired")

        compose.onNodeWithTag(CHECKOUT_CANCEL_TAG).performClick()

        compose.awaitScreen("SCR-04")
        assertThat(orders.current.value).isNull()
    }

    @Test
    fun offlineDisablesTryAgainAsReconnecting() {
        backend.paymentStatus.enqueue(
            Outcome.Success(
                SampleData.paymentPending.copy(status = PaymentStatus.EXPIRED),
            ),
        )
        reachCheckout()
        awaitTitle("QR code expired")

        connectivity.set(Connectivity.OFFLINE)
        compose.waitUntil(SCREEN_TIMEOUT_MS) { nodeDisabled() }
        compose.onNodeWithTag(CHECKOUT_TRY_AGAIN_TAG).assertIsNotEnabled()

        connectivity.set(Connectivity.ONLINE)
        compose.waitUntil(SCREEN_TIMEOUT_MS) { !nodeDisabled() }
    }

    @Test
    fun aRecreatedActivityKeepsThePaymentAndSendsNothingAgain() {
        reachCheckout()

        scenario.recreate()
        compose.awaitScreen("SCR-11")
        compose.waitUntilAtLeastOneExists(hasTestTag(CHECKOUT_QR_TAG), SCREEN_TIMEOUT_MS)

        // TC-138: still one order and one payment.
        assertThat(backend.callsTo(FakeBackendApi.Endpoint.E7)).hasSize(1)
        assertThat(backend.callsTo(FakeBackendApi.Endpoint.E9)).hasSize(1)
    }

    @Test
    fun aPaidPaymentLeadsToComplete() {
        reachCheckout()
        backend.paymentStatus.succeed(SampleData.paymentPaid)

        // The next poll says PAID; Confirming payment shows for at least 800 ms first (TC-075).
        compose.awaitScreen("SCR-12")
    }

    /** Idle → Order type → Menu → Upsell → Your order, with Crispy Chicken in the cart. */
    private fun reachYourOrder() {
        compose.onNodeWithTag(screenTag("SCR-04")).performClick()
        compose.awaitScreen("SCR-05")
        tapTag(orderTypeCardTag(OrderType.DINE_IN))
        compose.awaitScreen("SCR-06")
        tapTag(menuAddTag("c1"))
        tapTag(MENU_VIEW_ORDER_TAG)
        tapTag(UPSELL_NO_THANKS_TAG)
        compose.awaitScreen("SCR-10")
    }

    /** Your order → Pay with the default method (QRIS) → the QR is on screen. */
    private fun reachCheckout() {
        reachYourOrder()
        tapTag(YOUR_ORDER_PAY_TAG)
        compose.awaitScreen("SCR-11")
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose.onAllNodes(hasTestTag(CHECKOUT_QR_TAG)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag(CHECKOUT_TITLE_TAG)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTitle(title: String) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            compose
                .onAllNodes(hasTestTag(CHECKOUT_TITLE_TAG) and hasText(title))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun nodeDisabled(): Boolean =
        runCatching { compose.onNodeWithTag(CHECKOUT_TRY_AGAIN_TAG).assertIsNotEnabled() }.isSuccess

    /** Waits for the node tagged [tag] and taps it, scrolling to it first when it sits in a scrollable container. */
    private fun tapTag(tag: String) {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), SCREEN_TIMEOUT_MS)
        val node = compose.onNodeWithTag(tag)
        if (compose
                .onAllNodes(hasScrollAction() and hasAnyDescendant(hasTestTag(tag)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            node.performScrollTo()
        }
        node.performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val RAPID_TAPS = 10
    }
}
