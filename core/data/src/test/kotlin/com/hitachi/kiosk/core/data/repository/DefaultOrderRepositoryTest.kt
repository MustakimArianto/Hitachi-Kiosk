package com.hitachi.kiosk.core.data.repository

import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi.Endpoint
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** DEV-045: E7 and E8 with idempotent retries and the pending order (FR-016, TC-068, TC-070, TR-REL-02). */
class DefaultOrderRepositoryTest {
    private val backend = FakeBackendApi()
    private val store = InMemoryPendingOrderStore()
    private val ids = FakeIdGenerator()
    private val repository = DefaultOrderRepository(backend, store, ids)

    private val key = IdempotencyKey("key-1")
    private val request = SampleData.orderRequest

    private val transient =
        listOf(
            DomainError.Network(),
            DomainError.Network(isTimeout = true),
            DomainError.Server("SERVER_ERROR", 503),
            DomainError.Server("SERVER_ERROR", 500),
            DomainError.Server("RATE_LIMITED", 429),
        )

    private val business =
        listOf(
            DomainError.PriceChanged("menu-119", "cfg-42", SampleData.changedTotals),
            DomainError.ItemUnavailable(listOf("l2"), listOf("c1")),
            DomainError.Conflict("MERCHANT_CLOSED"),
            DomainError.Server("VALIDATION_ERROR", 400),
            DomainError.Unauthorized,
            DomainError.Unknown,
        )

    // create -----------------------------------------------------------------------------------------------------------

    @Test
    fun `create sends E7 with the key and saves the pending order before returning (TC-068)`() =
        runTest {
            val outcome = repository.create(request, key)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.createdOrder))
            val call = backend.callsTo(Endpoint.E7).single()
            assertThat(call.args).containsExactly(request)
            assertThat(call.key).isEqualTo(key)
            assertThat(store.stored).isEqualTo(SamplePendingOrder)
            assertThat(repository.pending()).isEqualTo(SamplePendingOrder)
        }

    @Test
    fun `a 503 then a 201 is one order, and both attempts carry the same key (TC-070, AC2)`() =
        runTest {
            backend.createOrder.enqueue(Outcome.Failure(DomainError.Server("SERVER_ERROR", 503)))

            val outcome = repository.create(request, key)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.createdOrder))
            assertThat(backend.callsTo(Endpoint.E7).map { it.key }).containsExactly(key, key)
            assertThat(testScheduler.currentTime).isEqualTo(1_000)
            assertThat(store.stored).isEqualTo(SamplePendingOrder)
        }

    @Test
    fun `network errors, 5xx and 429 are retried after 1, 2 and 4 seconds, then reported`() =
        runTest {
            transient.forEach { error ->
                val before = testScheduler.currentTime
                val calls = backend.callsTo(Endpoint.E7).size
                backend.createOrder.fail(error)

                val outcome = repository.create(request, key)

                assertThat(outcome).isEqualTo(Outcome.Failure(error))
                assertThat(backend.callsTo(Endpoint.E7).size - calls).isEqualTo(4)
                assertThat(testScheduler.currentTime - before).isEqualTo(7_000)
                assertThat(store.stored).isNull()
            }
        }

    @Test
    fun `business errors are returned at once without a retry or a pending order`() =
        runTest {
            business.forEach { error ->
                val calls = backend.callsTo(Endpoint.E7).size
                backend.createOrder.fail(error)

                val outcome = repository.create(request, key)

                assertThat(outcome).isEqualTo(Outcome.Failure(error))
                assertThat(backend.callsTo(Endpoint.E7).size - calls).isEqualTo(1)
                assertThat(store.stored).isNull()
            }
            assertThat(testScheduler.currentTime).isEqualTo(0)
        }

    @Test
    fun `a failed create leaves the previous pending order alone`() =
        runTest {
            store.stored = SamplePendingOrder
            backend.createOrder.fail(DomainError.Conflict("MERCHANT_CLOSED"))

            repository.create(request, IdempotencyKey("key-2"))

            assertThat(store.stored).isEqualTo(SamplePendingOrder)
        }

    @Test
    fun `a pending order that cannot be saved is reported instead of success, and Pay again recovers`() =
        runTest {
            store.failWrites = true

            val first = repository.create(request, key)

            assertThat(first).isEqualTo(Outcome.Failure(DomainError.Unknown))
            assertThat(store.stored).isNull()

            // The caller retries with the same key; the backend answers with the same order and the save works now.
            store.failWrites = false
            val second = repository.create(request, key)

            assertThat(second).isEqualTo(Outcome.Success(SampleData.createdOrder))
            assertThat(backend.callsTo(Endpoint.E7).map { it.key }).containsExactly(key, key)
            assertThat(store.stored).isEqualTo(SamplePendingOrder)
        }

    @Test
    fun `a new order replaces the pending one`() =
        runTest {
            store.stored =
                SamplePendingOrder.copy(orderId = "ord_old", paymentId = "pay_old", method = PaymentMethod.QRIS)

            repository.create(request, IdempotencyKey("key-9"))

            assertThat(store.stored).isEqualTo(SamplePendingOrder.copy(idempotencyKey = IdempotencyKey("key-9")))
        }

    // cancel -----------------------------------------------------------------------------------------------------------

    @Test
    fun `a cancelled order clears the pending order`() =
        runTest {
            repository.create(request, key)

            val outcome = repository.cancel(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.cancelledOrder))
            val call = backend.callsTo(Endpoint.E8).single()
            assertThat(call.args).containsExactly(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK)
            assertThat(store.stored).isNull()
        }

    @Test
    fun `cancel uses one key from the generator for all of its retries and a new one for the next cancel`() =
        runTest {
            backend.cancelOrder.enqueue(Outcome.Failure(DomainError.Network()))
            backend.cancelOrder.enqueue(Outcome.Failure(DomainError.Server("SERVER_ERROR", 502)))

            repository.cancel(SampleData.ORDER_ID, CancelOrderReason.TIMEOUT)
            repository.cancel(SampleData.ORDER_ID, CancelOrderReason.TIMEOUT)

            assertThat(backend.callsTo(Endpoint.E8).map { it.key })
                .containsExactly(key, key, key, IdempotencyKey("key-2"))
                .inOrder()
        }

    @Test
    fun `a cancel that cannot reach the backend keeps the pending order for recovery`() =
        runTest {
            store.stored = SamplePendingOrder
            backend.cancelOrder.fail(DomainError.Network())

            val outcome = repository.cancel(SampleData.ORDER_ID, CancelOrderReason.APP_RECOVERY)

            assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.Network()))
            assertThat(backend.callsTo(Endpoint.E8)).hasSize(4)
            assertThat(store.stored).isEqualTo(SamplePendingOrder)
        }

    @Test
    fun `409 ORDER_ALREADY_PAID is returned unchanged, not retried, and keeps the pending order`() =
        runTest {
            store.stored = SamplePendingOrder.copy(paymentId = SampleData.QRIS_PAYMENT_ID, method = PaymentMethod.QRIS)
            backend.cancelOrder.fail(DomainError.Conflict("ORDER_ALREADY_PAID"))

            val outcome = repository.cancel(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK)

            assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.Conflict("ORDER_ALREADY_PAID")))
            assertThat(backend.callsTo(Endpoint.E8)).hasSize(1)
            assertThat(store.stored).isNotNull()
        }

    @Test
    fun `an answer that is not CANCELLED keeps the pending order`() =
        runTest {
            store.stored = SamplePendingOrder
            backend.cancelOrder.succeed(CancelledOrder(SampleData.ORDER_ID, OrderStatus.PAID))

            repository.cancel(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK)

            assertThat(store.stored).isEqualTo(SamplePendingOrder)
        }

    @Test
    fun `cancelling an older order never drops a newer pending one`() =
        runTest {
            store.stored = SamplePendingOrder.copy(orderId = "ord_new")

            repository.cancel(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK)

            assertThat(store.stored?.orderId).isEqualTo("ord_new")
        }

    @Test
    fun `a pending order that cannot be deleted does not fail a successful cancel`() =
        runTest {
            store.stored = SamplePendingOrder
            store.failWrites = true

            val outcome = repository.cancel(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.cancelledOrder))
        }

    // pending, recordPayment, clearPending -----------------------------------------------------------------------------

    @Test
    fun `recordPayment adds the payment to the pending order`() =
        runTest {
            repository.create(request, key)

            repository.recordPayment(SampleData.ORDER_ID, SampleData.QRIS_PAYMENT_ID, PaymentMethod.QRIS)

            assertThat(repository.pending())
                .isEqualTo(
                    SamplePendingOrder.copy(paymentId = SampleData.QRIS_PAYMENT_ID, method = PaymentMethod.QRIS),
                )
        }

    @Test
    fun `recordPayment for another order or on a broken disk changes nothing and does not throw`() =
        runTest {
            store.stored = SamplePendingOrder

            repository.recordPayment("ord_other", "pay_x", PaymentMethod.CARD)
            assertThat(store.stored).isEqualTo(SamplePendingOrder)

            store.failWrites = true
            repository.recordPayment(SampleData.ORDER_ID, "pay_x", PaymentMethod.CARD)
            assertThat(store.stored).isEqualTo(SamplePendingOrder)
        }

    @Test
    fun `clearPending forgets the order`() =
        runTest {
            store.stored = SamplePendingOrder

            repository.clearPending()

            assertThat(repository.pending()).isNull()
        }

    @Test
    fun `an unreadable pending order reads as none instead of crashing`() =
        runTest {
            store.stored = SamplePendingOrder
            store.readFailure = SQLiteException("corrupt")
            assertThat(repository.pending()).isNull()

            store.readFailure = IllegalArgumentException("Unknown or missing pending_order.method")
            assertThat(repository.pending()).isNull()
        }
}
