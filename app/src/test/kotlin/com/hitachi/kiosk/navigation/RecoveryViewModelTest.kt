package com.hitachi.kiosk.navigation

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.device.Connectivity
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PendingOrder
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.RecoverPendingOrder
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fake.FakeConnectivityMonitor
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fake.FakeOrderRepository
import com.hitachi.kiosk.core.testing.fake.FakePaymentGateway
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import com.hitachi.kiosk.recovery.PendingOrderRecoveryCoordinator
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The request to open Complete for an order that a process-death recovery found paid (FR-016 AC4, DEV-052). */
class RecoveryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `a paid recovery asks for Complete once`() =
        runTest(main.dispatcher) {
            val gateway = FakePaymentGateway().apply { status.succeed(SampleData.paymentPaid) }
            val orders =
                FakeOrderRepository(
                    PendingOrder(
                        orderId = SampleData.ORDER_ID,
                        orderNumber = "A247",
                        paymentId = SampleData.QRIS_PAYMENT_ID,
                        method = PaymentMethod.QRIS,
                        idempotencyKey = IdempotencyKey("order-key"),
                        totals = SampleData.totals,
                        createdAt = SampleData.createdOrder.createdAt,
                    ),
                )
            val coordinator =
                PendingOrderRecoveryCoordinator(
                    RecoverPendingOrder(orders, gateway, FakeIdGenerator()),
                    FakeActivationRepository().apply { emit(SampleData.boundState) },
                    FakeConnectivityMonitor(Connectivity.ONLINE),
                    DefaultOrderSessionHolder(),
                    backgroundScope,
                )
            val viewModel = RecoveryViewModel(coordinator)
            runCurrent()
            assertThat(viewModel.completeRequested.value).isFalse()

            coordinator.recoverAtStartup()
            runCurrent()
            assertThat(viewModel.completeRequested.value).isTrue()

            viewModel.onCompleteShown()
            runCurrent()
            assertThat(viewModel.completeRequested.value).isFalse()
        }
}
