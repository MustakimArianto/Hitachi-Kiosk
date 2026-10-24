package com.hitachi.kiosk.core.testing.fake

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.HealthStatus
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class FakeBackendApiTest {
    private val api = FakeBackendApi()
    private val key = IdempotencyKey("key-1")

    @Test
    fun `every endpoint answers the api-spec happy path by default`() =
        runTest {
            assertThat(api.activate(SampleData.activationRequest())).isEqualTo(Outcome.Success(SampleData.activation))
            assertThat(api.merchants(SampleData.DEVICE_ID)).isEqualTo(Outcome.Success(SampleData.merchants))
            assertThat(
                api.bind(SampleData.DEVICE_ID, SampleData.MERCHANT_ID, key),
            ).isEqualTo(Outcome.Success(SampleData.binding))
            assertThat(api.unbind(SampleData.DEVICE_ID)).isEqualTo(Outcome.Success(Unit))
            assertThat(api.config(SampleData.MERCHANT_ID, null)).isEqualTo(
                Outcome.Success(Conditional.Modified(SampleData.config, "\"cfg-42\"")),
            )
            assertThat(api.menu(SampleData.MERCHANT_ID, null)).isEqualTo(
                Outcome.Success(Conditional.Modified(SampleData.menu, "\"menu-118\"")),
            )
            assertThat(
                api.createOrder(SampleData.orderRequest, key),
            ).isEqualTo(Outcome.Success(SampleData.createdOrder))
            assertThat(api.cancelOrder(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK, key))
                .isEqualTo(Outcome.Success(SampleData.cancelledOrder))
            assertThat(api.startPayment(SampleData.ORDER_ID, PaymentMethod.QRIS, 300, key))
                .isEqualTo(Outcome.Success(SampleData.qrisPayment))
            assertThat(api.startPayment(SampleData.ORDER_ID, PaymentMethod.CARD, null, key))
                .isEqualTo(Outcome.Success(SampleData.cardPayment))
            assertThat(api.paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID))
                .isEqualTo(Outcome.Success(SampleData.paymentPending))
            assertThat(
                api.cancelPayment(
                    SampleData.ORDER_ID,
                    SampleData.QRIS_PAYMENT_ID,
                    CancelPaymentReason.CUSTOMER_BACK,
                    key,
                ),
            ).isEqualTo(Outcome.Success(SampleData.paymentCancelled))
            assertThat(
                api.relayTerminalResult(SampleData.ORDER_ID, SampleData.CARD_PAYMENT_ID, SampleData.terminalResult),
            ).isEqualTo(Outcome.Success(SampleData.terminalRelayed))
            assertThat(api.changeStaffPin(SampleData.DEVICE_ID, SampleData.STAFF_PIN, "5678", key))
                .isEqualTo(Outcome.Success(SampleData.newStaffPin))
            assertThat(api.health()).isEqualTo(Outcome.Success(SampleData.health))

            assertThat(api.calls.map { it.endpoint })
                .containsExactlyElementsIn(
                    FakeBackendApi.Endpoint.entries.flatMap {
                        if (it ==
                            FakeBackendApi.Endpoint.E9
                        ) {
                            listOf(it, it)
                        } else {
                            listOf(it)
                        }
                    },
                ).inOrder()
        }

    @Test
    fun `calls record their idempotency key but never the staff PINs`() =
        runTest {
            api.createOrder(SampleData.orderRequest, key)
            api.changeStaffPin(SampleData.DEVICE_ID, SampleData.STAFF_PIN, "5678", key)

            assertThat(api.callsTo(FakeBackendApi.Endpoint.E7).single().key).isEqualTo(key)
            assertThat(api.callsTo(FakeBackendApi.Endpoint.E13).single().args).containsExactly(SampleData.DEVICE_ID)
        }

    @Test
    fun `a wrong current PIN is rejected`() =
        runTest {
            assertThat(api.changeStaffPin(SampleData.DEVICE_ID, "0000", "5678", key))
                .isEqualTo(Outcome.Failure(DomainError.Conflict("PIN_INCORRECT")))
        }

    @Test
    fun `queued answers come first, then the fallback, with scripted delays`() =
        runTest {
            api.health.enqueue(Outcome.Failure(DomainError.Network(isTimeout = true)), delay = 3.seconds)

            assertThat(api.health()).isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = true)))
            assertThat(currentTime).isEqualTo(3_000)
            assertThat(api.health()).isEqualTo(Outcome.Success(SampleData.health))
            assertThat(api.health.callCount).isEqualTo(2)
        }

    @Test
    fun `cleared queued answers are not used`() =
        runTest {
            api.health.enqueue(Outcome.Failure(DomainError.Unknown))
            api.health.clearQueue()

            assertThat(api.health()).isEqualTo(Outcome.Success(SampleData.health))
        }

    @Test
    fun `with a clock the QR expires relative to now`() =
        runTest {
            val clock = TestClock()
            val realtime = FakeBackendApi(clock)

            val payment =
                (
                    realtime.startPayment(
                        SampleData.ORDER_ID,
                        PaymentMethod.QRIS,
                        180,
                        key,
                    ) as Outcome.Success
                ).value

            assertThat((payment as StartedPayment.Qris).expiresAt).isEqualTo(clock.now() + 180.seconds)
            // The fake backend's `Date` header is the clock it was given (NFR-017).
            assertThat(payment.serverTime).isEqualTo(clock.now())
            val defaultExpiry =
                realtime.startPayment(
                    SampleData.ORDER_ID,
                    PaymentMethod.QRIS,
                    null,
                    key,
                ) as Outcome.Success
            assertThat((defaultExpiry.value as StartedPayment.Qris).expiresAt).isEqualTo(clock.now() + 300.seconds)
            assertThat(realtime.startPayment(SampleData.ORDER_ID, PaymentMethod.CARD, null, key))
                .isEqualTo(Outcome.Success(SampleData.cardPayment))
        }

    @Test
    fun `scenarios script the api-spec mock outcomes`() =
        runTest {
            fun fresh(scenario: FakeScenario) = FakeBackendApi().apply { apply(scenario) }

            assertThat(fresh(FakeScenario.HAPPY_PATH).health()).isEqualTo(Outcome.Success(SampleData.health))
            assertThat(fresh(FakeScenario.ACTIVATE_UNREGISTERED).activate(SampleData.activationRequest()))
                .isEqualTo(Outcome.Failure(DomainError.Server("DEVICE_NOT_REGISTERED", 404)))
            assertThat(fresh(FakeScenario.ACTIVATE_DISABLED).activate(SampleData.activationRequest()))
                .isEqualTo(Outcome.Failure(DomainError.Server("DEVICE_DISABLED", 403)))
            assertThat(
                fresh(
                    FakeScenario.ORDER_PRICE_CHANGED,
                ).createOrder(SampleData.orderRequest, key).errorIs<DomainError.PriceChanged>(),
            ).isTrue()
            assertThat(fresh(FakeScenario.ORDER_ITEM_UNAVAILABLE).createOrder(SampleData.orderRequest, key))
                .isEqualTo(Outcome.Failure(DomainError.ItemUnavailable(listOf("l2"), listOf("c1"))))
            assertThat(fresh(FakeScenario.QRIS_CANCELLED).status()).isEqualTo(PaymentStatus.FAILED)
            assertThat(fresh(FakeScenario.CARD_DECLINED).status()).isEqualTo(PaymentStatus.FAILED)
            assertThat(fresh(FakeScenario.CARD_TIMEOUT).status()).isEqualTo(PaymentStatus.FAILED)
            assertThat((fresh(FakeScenario.GATEWAY_DOWN).health() as Outcome.Success).value.paymentGateway)
                .isEqualTo(HealthStatus.DOWN)

            val expiring =
                fresh(
                    FakeScenario.QRIS_EXPIRE_FAST,
                ).startPayment(SampleData.ORDER_ID, PaymentMethod.QRIS, 300, key)
            val qr = (expiring as Outcome.Success).value as StartedPayment.Qris
            assertThat(qr.expiresAt - qr.createdAt).isEqualTo(20.seconds)
        }

    @Test
    fun `qris_paid_after_5s answers PENDING a few times, then PAID`() =
        runTest {
            val scripted = FakeBackendApi().apply { apply(FakeScenario.fromId("qris_paid_after_5s")!!) }

            val statuses = List(FakeScenario.PENDING_POLLS + 2) { scripted.status() }

            assertThat(statuses)
                .containsExactly(
                    PaymentStatus.PENDING,
                    PaymentStatus.PENDING,
                    PaymentStatus.PENDING,
                    PaymentStatus.PAID,
                    PaymentStatus.PAID,
                ).inOrder()
        }

    @Test
    fun `server_error_once fails the first order only`() =
        runTest {
            val scripted = FakeBackendApi().apply { apply(FakeScenario.SERVER_ERROR_ONCE) }

            assertThat(scripted.createOrder(SampleData.orderRequest, key))
                .isEqualTo(Outcome.Failure(DomainError.Server("SERVER_ERROR", 503)))
            assertThat(
                scripted.createOrder(SampleData.orderRequest, key),
            ).isEqualTo(Outcome.Success(SampleData.createdOrder))
        }

    @Test
    fun `slow_network delays every answer by 8 seconds`() =
        runTest {
            val scripted = FakeBackendApi().apply { apply(FakeScenario.SLOW_NETWORK) }

            scripted.health()

            assertThat(currentTime).isEqualTo(8_000)
        }

    @Test
    fun `scenario ids match api-spec section 4`() {
        assertThat(FakeScenario.entries.map { it.id }).containsAtLeast(
            "activate_unregistered",
            "activate_disabled",
            "order_price_changed",
            "order_item_unavailable",
            "qris_paid_after_5s",
            "qris_cancelled",
            "qris_expire_fast",
            "card_declined",
            "card_timeout",
            "slow_network",
            "server_error_once",
            "gateway_down",
        )
        assertThat(FakeScenario.fromId("nope")).isNull()
    }

    private suspend fun FakeBackendApi.status(): PaymentStatus =
        (paymentStatus(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID) as Outcome.Success).value.status

    private inline fun <reified E : DomainError> Outcome<*>.errorIs(): Boolean = (this as? Outcome.Failure)?.error is E
}
