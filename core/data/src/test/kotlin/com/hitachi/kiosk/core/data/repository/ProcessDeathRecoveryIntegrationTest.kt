package com.hitachi.kiosk.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.backend.BackendPaymentGateway
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.jsonResponse
import com.hitachi.kiosk.core.data.takeRequestNow
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.order.RecoverPendingOrder
import com.hitachi.kiosk.core.domain.order.RecoveryOutcome
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/**
 * TC-073's logic over real HTTP against a scripted server (FR-016 AC4, DEV-052): the app was killed with a pending
 * payment, and the next launch reads `pending_order`, asks E10 and decides. The real `adb shell am kill` check on the
 * device is a manual step.
 */
class ProcessDeathRecoveryIntegrationTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val harness by lazy { BackendHarness(server) }
    private val store =
        InMemoryPendingOrderStore(
            SamplePendingOrder.copy(paymentId = SampleData.QRIS_PAYMENT_ID, method = PaymentMethod.QRIS),
        )
    private val recover by lazy {
        RecoverPendingOrder(
            orders = DefaultOrderRepository(harness.api, store, FakeIdGenerator("order")),
            gateway = BackendPaymentGateway(harness.api),
            ids = FakeIdGenerator("pay"),
        )
    }

    private fun error(code: String) = """{ "code": "$code", "message": "m", "details": { } }"""

    @Test
    fun `paid after the kill goes to Complete with the backend order number and nothing is cancelled (TC-073)`() =
        runTest {
            server.enqueue(fixtureResponse("e10_response_paid"))

            val outcome = recover()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Paid::class.java)
            val order = (outcome as RecoveryOutcome.Paid).order
            assertThat(order.orderNumber).isEqualTo("A247")
            assertThat(order.orderId).isEqualTo(SampleData.ORDER_ID)
            assertThat(order.totals).isEqualTo(SampleData.createdOrder.totals)
            assertThat(order.reference).isEqualTo("QR-8812-4471-0091")
            assertThat(server.requestCount).isEqualTo(1)
            assertThat(server.takeRequestNow().target)
                .isEqualTo("/v1/orders/${SampleData.ORDER_ID}/payments/${SampleData.QRIS_PAYMENT_ID}")
            assertThat(store.stored).isNull()
        }

    @Test
    fun `a payment still pending is cancelled with E11 then E8, each with its own key, and forgotten`() =
        runTest {
            server.enqueue(fixtureResponse("e10_response_pending"))
            server.enqueue(fixtureResponse("e11_response"))
            server.enqueue(fixtureResponse("e8_response"))

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            val e10 = server.takeRequestNow()
            val e11 = server.takeRequestNow()
            val e8 = server.takeRequestNow()
            assertThat(e10.method).isEqualTo("GET")
            assertThat(e11.target)
                .isEqualTo("/v1/orders/${SampleData.ORDER_ID}/payments/${SampleData.QRIS_PAYMENT_ID}/cancel")
            assertThat(e11.body?.utf8()).contains("EXPIRED_ON_KIOSK")
            assertThat(e11.headers["Idempotency-Key"]).isEqualTo("pay-1")
            assertThat(e8.target).isEqualTo("/v1/orders/${SampleData.ORDER_ID}/cancel")
            assertThat(e8.body?.utf8()).contains("APP_RECOVERY")
            assertThat(e8.headers["Idempotency-Key"]).isEqualTo("order-1")
            assertThat(store.stored).isNull()
        }

    @Test
    fun `E11 answering 409 PAYMENT_NOT_CANCELLABLE re-reads E10 and finds the payment paid`() =
        runTest {
            server.enqueue(fixtureResponse("e10_response_pending"))
            server.enqueue(fixtureResponse("e11_error_not_cancellable", 409))
            server.enqueue(fixtureResponse("e10_response_paid"))

            val outcome = recover()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Paid::class.java)
            assertThat(server.requestCount).isEqualTo(3)
            assertThat(store.stored).isNull()
        }

    @Test
    fun `a failed payment is cancelled with E8 only`() =
        runTest {
            server.enqueue(fixtureResponse("e10_response_failed"))
            server.enqueue(fixtureResponse("e8_response"))

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(server.requestCount).isEqualTo(2)
            assertThat(store.stored).isNull()
        }

    @Test
    fun `an order without a payment is cancelled with E8 and no payment call`() =
        runTest {
            store.stored = SamplePendingOrder
            server.enqueue(fixtureResponse("e8_response"))

            assertThat(recover()).isEqualTo(RecoveryOutcome.Cancelled)

            assertThat(server.requestCount).isEqualTo(1)
            assertThat(store.stored).isNull()
        }

    @Test
    fun `503 on E10 is retried 1 s, 2 s, 4 s and then leaves the pending order for the next launch`() =
        runTest {
            repeat(4) { server.enqueue(jsonResponse(error("SERVER_ERROR"), 503)) }

            val outcome = recover()

            assertThat(outcome).isInstanceOf(RecoveryOutcome.Deferred::class.java)
            assertThat((outcome as RecoveryOutcome.Deferred).error).isInstanceOf(DomainError.Server::class.java)
            assertThat(server.requestCount).isEqualTo(4)
            assertThat(store.stored).isNotNull()
        }

    @Test
    fun `a second run after a recovery finds nothing and sends nothing`() =
        runTest {
            server.enqueue(fixtureResponse("e10_response_paid"))
            recover()
            val sent = server.requestCount

            assertThat(recover()).isEqualTo(RecoveryOutcome.NothingPending)

            assertThat(server.requestCount).isEqualTo(sent)
        }
}
