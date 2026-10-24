package com.hitachi.kiosk.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.jsonResponse
import com.hitachi.kiosk.core.data.takeRequestNow
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.CancelOrderReason
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/** The order repository over real HTTP against a scripted server: TC-070 and the E8 retry (FR-016, DEV-045). */
class OrderSubmissionIntegrationTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val harness by lazy { BackendHarness(server) }
    private val store = InMemoryPendingOrderStore()
    private val repository by lazy { DefaultOrderRepository(harness.api, store, FakeIdGenerator()) }
    private val key = IdempotencyKey("ord-key-1")

    private fun error(code: String) = """{ "code": "$code", "message": "m", "details": { } }"""

    @Test
    fun `503 then 201 sends the same Idempotency-Key twice and yields one order (TC-070)`() =
        runTest {
            server.enqueue(jsonResponse(error("SERVER_ERROR"), 503))
            server.enqueue(fixtureResponse("e7_response", 201))

            val outcome = repository.create(SampleData.orderRequest, key)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.createdOrder))
            val first = server.takeRequestNow()
            val second = server.takeRequestNow()
            assertThat(first.headers["Idempotency-Key"]).isEqualTo("ord-key-1")
            assertThat(second.headers["Idempotency-Key"]).isEqualTo("ord-key-1")
            assertThat(second.target).isEqualTo("/v1/orders")
            assertThat(server.requestCount).isEqualTo(2)
            assertThat(store.stored?.idempotencyKey).isEqualTo(key)
        }

    @Test
    fun `429 is retried like a server error`() =
        runTest {
            server.enqueue(jsonResponse(error("RATE_LIMITED"), 429))
            server.enqueue(fixtureResponse("e7_response", 201))

            val outcome = repository.create(SampleData.orderRequest, key)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.createdOrder))
            assertThat(server.requestCount).isEqualTo(2)
        }

    @Test
    fun `409 PRICE_CHANGED and ITEM_UNAVAILABLE are not retried and leave no pending order`() =
        runTest {
            server.enqueue(fixtureResponse("e7_error_price_changed", 409))
            val changed = repository.create(SampleData.orderRequest, key)
            assertThat((changed as Outcome.Failure).error).isInstanceOf(DomainError.PriceChanged::class.java)
            assertThat(server.requestCount).isEqualTo(1)

            server.enqueue(fixtureResponse("e7_error_item_unavailable", 409))
            val unavailable = repository.create(SampleData.orderRequest, key)
            assertThat(unavailable)
                .isEqualTo(Outcome.Failure(DomainError.ItemUnavailable(listOf("l2"), listOf("c1"))))
            assertThat(server.requestCount).isEqualTo(2)
            assertThat(store.stored).isNull()
        }

    @Test
    fun `cancel sends one key on every retry and clears the pending order`() =
        runTest {
            store.stored = SamplePendingOrder
            server.enqueue(jsonResponse(error("SERVER_ERROR"), 503))
            server.enqueue(fixtureResponse("e8_response"))

            val outcome = repository.cancel(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.cancelledOrder))
            val first = server.takeRequestNow()
            val second = server.takeRequestNow()
            assertThat(first.target).isEqualTo("/v1/orders/${SampleData.ORDER_ID}/cancel")
            assertThat(first.headers["Idempotency-Key"]).isEqualTo("key-1")
            assertThat(second.headers["Idempotency-Key"]).isEqualTo("key-1")
            assertThat(store.stored).isNull()
        }

    @Test
    fun `409 ORDER_ALREADY_PAID on cancel comes back as a conflict and keeps the pending order`() =
        runTest {
            store.stored = SamplePendingOrder
            server.enqueue(jsonResponse(error("ORDER_ALREADY_PAID"), 409))

            val outcome = repository.cancel(SampleData.ORDER_ID, CancelOrderReason.CUSTOMER_BACK)

            assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.Conflict("ORDER_ALREADY_PAID")))
            assertThat(server.requestCount).isEqualTo(1)
            assertThat(store.stored).isEqualTo(SamplePendingOrder)
        }
}
