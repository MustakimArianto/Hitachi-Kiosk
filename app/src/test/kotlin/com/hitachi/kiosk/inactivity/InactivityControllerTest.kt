package com.hitachi.kiosk.inactivity

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderSession
import com.hitachi.kiosk.core.testing.fake.FakeSettingsRepository
import com.hitachi.kiosk.core.testing.fake.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** FR-023 timer logic (DEV-040; TC-101–TC-104, A-23) on a test clock and virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class InactivityControllerTest {
    private val clock = TestClock()
    private val settings = FakeSettingsRepository()
    private val sessions = DefaultOrderSessionHolder()

    private class Harness(
        val controller: InactivityController,
        val effects: List<InactivityEffect>,
        val states: List<StillThereState>,
    )

    private fun TestScope.start(zone: InactivityZone = InactivityZone.ORDERING): Harness {
        val controller = InactivityController(clock, settings, sessions, backgroundScope)
        val effects = mutableListOf<InactivityEffect>()
        val states = mutableListOf<StillThereState>()
        backgroundScope.launch { controller.effect.toList(effects) }
        backgroundScope.launch { controller.state.toList(states) }
        controller.onDestinationChanged(zone)
        runCurrent()
        return Harness(controller, effects, states)
    }

    /** Moves the device clock and virtual time together, in small steps as the real clock would. */
    private fun TestScope.advance(duration: Duration) {
        var left = duration
        while (left > Duration.ZERO) {
            val step = minOf(STEP, left)
            clock.advanceBy(step)
            advanceTimeBy(step)
            runCurrent()
            left -= step
        }
    }

    private fun orderWithItem(): OrderSession =
        sessions.chooseOrderType(OrderType.DINE_IN, "menu-118", "cfg-42").also {
            it.startBuilding()
            it.add(CartItem.ProductItem("c1", emptyList()), 1, Rupiah(38_000), emptyList())
        }

    private val Harness.state get() = controller.state.value

    @Test
    fun `TC-101 the overlay shows with 15 s at the 60 s timeout`() =
        runTest {
            orderWithItem()
            val h = start()

            advance(59.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            advance(1.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Showing(15))
            assertThat(h.effects).isEmpty()
        }

    @Test
    fun `the countdown ticks every second and is low for the last 5 s`() =
        runTest {
            orderWithItem()
            val h = start()
            advance(60.seconds)

            advance(1.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Showing(14))
            advance(8.seconds)
            assertThat((h.state as StillThereState.Showing).countdownLow).isFalse()
            advance(1.seconds)

            val low = h.state as StillThereState.Showing
            assertThat(low.secondsLeft).isEqualTo(5)
            assertThat(low.countdownLow).isTrue()
        }

    @Test
    fun `a touch moves the deadline`() =
        runTest {
            orderWithItem()
            val h = start()

            advance(50.seconds)
            h.controller.onUserActivity()
            advance(59.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            advance(1.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Showing(15))
        }

    @Test
    fun `TC-102 Continue hides the overlay, resets the timer and keeps the cart`() =
        runTest {
            val session = orderWithItem()
            val h = start()
            advance(65.seconds)

            h.controller.onContinue()
            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            advance(59.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            advance(1.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Showing(15))
            assertThat(
                session.state.value.cart
                    .isEmpty(),
            ).isFalse()
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.BUILDING)
            assertThat(h.effects).isEmpty()
        }

    @Test
    fun `a touch while the overlay shows leaves it to the overlay's own buttons`() =
        runTest {
            orderWithItem()
            val h = start()
            advance(60.seconds)

            h.controller.onUserActivity()

            assertThat(h.state).isEqualTo(StillThereState.Showing(15))
        }

    @Test
    fun `TC-103 the countdown running out times the order out and goes to Idle`() =
        runTest {
            val session = orderWithItem()
            val h = start()
            advance(60.seconds)

            advance(14.9.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Showing(1))
            assertThat(h.effects).isEmpty()
            advance(0.1.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.TIMED_OUT)
            assertThat(h.effects).containsExactly(InactivityEffect.GoToIdle)
        }

    @Test
    fun `Start over cancels the order and goes to Idle`() =
        runTest {
            val session = orderWithItem()
            val h = start()
            advance(62.seconds)

            h.controller.onStartOver()
            advance(30.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.CANCELLED)
            assertThat(h.effects).containsExactly(InactivityEffect.GoToIdle)
        }

    @Test
    fun `TC-104 no timer on excluded destinations`() =
        runTest {
            orderWithItem()
            val h = start(InactivityZone.OFF)

            advance(10.minutes())

            assertThat(h.states).containsExactly(StillThereState.Hidden)
            assertThat(h.effects).isEmpty()
        }

    @Test
    fun `TC-104 leaving for an excluded destination hides the overlay and stops the timer`() =
        runTest {
            orderWithItem()
            val h = start()
            advance(61.seconds)

            h.controller.onDestinationChanged(InactivityZone.OFF)
            runCurrent()
            advance(10.minutes())

            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            assertThat(h.effects).isEmpty()
        }

    @Test
    fun `TC-104 no overlay while the order is submitting, paying or paid`() =
        runTest {
            val session = orderWithItem()
            session.startReviewing()
            val h = start()

            session.submit()
            runCurrent()
            advance(5.minutes())
            assertWithMessage("SUBMITTING").that(h.state).isEqualTo(StillThereState.Hidden)
            session.orderCreated()
            runCurrent()
            advance(5.minutes())
            assertWithMessage("PAYING").that(h.state).isEqualTo(StillThereState.Hidden)
            session.paid()
            runCurrent()
            advance(5.minutes())
            assertWithMessage("PAID").that(h.state).isEqualTo(StillThereState.Hidden)

            assertThat(h.states).containsExactly(StillThereState.Hidden)
            assertThat(h.effects).isEmpty()
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.PAID)
        }

    @Test
    fun `the overlay goes away when the order is submitted, and the timer runs again after Back to order`() =
        runTest {
            val session = orderWithItem()
            session.startReviewing()
            val h = start()
            advance(60.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Showing(15))

            session.submit()
            runCurrent()
            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            session.orderCreated()
            session.backToOrder()
            runCurrent()
            advance(60.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Showing(15))
        }

    @Test
    fun `A-23 Order type with an empty cart goes straight to Idle without the overlay`() =
        runTest {
            val session = sessions.chooseOrderType(OrderType.TAKEAWAY, "menu-118", "cfg-42")
            val h = start(InactivityZone.ORDER_TYPE)

            advance(60.seconds)

            assertThat(h.states).containsExactly(StillThereState.Hidden)
            assertThat(h.effects).containsExactly(InactivityEffect.GoToIdle)
            assertThat(session.state.value.phase).isEqualTo(OrderPhase.TIMED_OUT)
        }

    @Test
    fun `A-23 Order type before any pick goes straight to Idle`() =
        runTest {
            val h = start(InactivityZone.ORDER_TYPE)

            advance(60.seconds)

            assertThat(h.states).containsExactly(StillThereState.Hidden)
            assertThat(h.effects).containsExactly(InactivityEffect.GoToIdle)
        }

    @Test
    fun `Order type with items in the cart shows the overlay`() =
        runTest {
            orderWithItem()
            val h = start(InactivityZone.ORDER_TYPE)

            advance(60.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Showing(15))
            assertThat(h.effects).isEmpty()
        }

    @Test
    fun `the configured timeout is used and a change applies to the next timer`() =
        runTest {
            settings.value = KioskSettings(idleTimeoutSeconds = 120)
            orderWithItem()
            val h = start()
            advance(119.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            advance(1.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Showing(15))

            settings.value = KioskSettings(idleTimeoutSeconds = 30)
            runCurrent()
            h.controller.onContinue()
            advance(29.seconds)
            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            advance(1.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Showing(15))
        }

    @Test
    fun `Show now opens the overlay at once and Reset hides it`() =
        runTest {
            orderWithItem()
            val h = start()

            h.controller.showNow()
            runCurrent()
            assertThat(h.state).isEqualTo(StillThereState.Showing(15))
            h.controller.reset()
            advance(30.seconds)

            assertThat(h.state).isEqualTo(StillThereState.Hidden)
            assertThat(h.effects).isEmpty()
        }

    private fun Int.minutes(): Duration = (this * 60).seconds

    private companion object {
        val STEP = 100.milliseconds
    }
}
