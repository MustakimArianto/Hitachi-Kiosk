package com.hitachi.kiosk.core.data.backend

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelPaymentReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi.Endpoint
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Which payment calls the gateway retries (technical-design §5.3, TR-NET-04): E9 and E11 carry a key, so they retry
 * with it; E10 is polled by the payment state machine and E12 has no key, so neither is retried here.
 */
class BackendPaymentGatewayTest {
    private val backend = FakeBackendApi()
    private val gateway = BackendPaymentGateway(backend)
    private val key = IdempotencyKey("start-key")
    private val network = DomainError.Network()
    private val unavailable = DomainError.Server(code = "SERVER_ERROR", httpStatus = 503)

    @Test
    fun `E9 retries a network error and a 503 with the same key, 1 s then 2 s apart`() =
        runTest {
            backend.startQris.enqueue(Outcome.Failure(network))
            backend.startQris.enqueue(Outcome.Failure(unavailable))

            val result = gateway.start(SampleData.ORDER_ID, PaymentMethod.QRIS, qrExpirySeconds = 300, key = key)

            assertThat(result).isEqualTo(Outcome.Success(SampleData.qrisPayment))
            assertThat(backend.callsTo(Endpoint.E9).map { it.key }).containsExactly(key, key, key)
            assertThat(currentTime).isEqualTo(3_000)
        }

    @Test
    fun `E9 does not retry a business error`() =
        runTest {
            backend.startQris.fail(DomainError.Conflict("ORDER_NOT_AWAITING_PAYMENT"))

            val result = gateway.start(SampleData.ORDER_ID, PaymentMethod.QRIS, qrExpirySeconds = 300, key = key)

            assertThat(result).isEqualTo(Outcome.Failure(DomainError.Conflict("ORDER_NOT_AWAITING_PAYMENT")))
            assertThat(backend.callsTo(Endpoint.E9)).hasSize(1)
        }

    @Test
    fun `E9 gives up after three retries`() =
        runTest {
            backend.startCard.fail(network)

            val result = gateway.start(SampleData.ORDER_ID, PaymentMethod.CARD, qrExpirySeconds = null, key = key)

            assertThat(result).isEqualTo(Outcome.Failure(network))
            assertThat(backend.callsTo(Endpoint.E9)).hasSize(4)
        }

    @Test
    fun `E11 retries with the same key`() =
        runTest {
            backend.cancelPayment.enqueue(Outcome.Failure(unavailable))

            val result =
                gateway.cancel(
                    SampleData.ORDER_ID,
                    SampleData.QRIS_PAYMENT_ID,
                    CancelPaymentReason.CUSTOMER_BACK,
                    key,
                )

            assertThat(result).isEqualTo(Outcome.Success(SampleData.paymentCancelled))
            assertThat(backend.callsTo(Endpoint.E11).map { it.key }).containsExactly(key, key)
        }

    @Test
    fun `E10 and E12 are not retried`() =
        runTest {
            backend.paymentStatus.fail(network)
            backend.relayTerminalResult.fail(network)

            assertThat(gateway.status(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID))
                .isEqualTo(Outcome.Failure(network))
            assertThat(
                gateway.relayTerminalResult(SampleData.ORDER_ID, SampleData.CARD_PAYMENT_ID, SampleData.terminalResult),
            ).isEqualTo(Outcome.Failure(network))

            assertThat(backend.callsTo(Endpoint.E10)).hasSize(1)
            assertThat(backend.callsTo(Endpoint.E12)).hasSize(1)
            assertThat(currentTime).isEqualTo(0)
        }
}
