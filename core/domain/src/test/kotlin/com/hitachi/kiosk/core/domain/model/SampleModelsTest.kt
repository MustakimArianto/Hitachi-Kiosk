package com.hitachi.kiosk.core.domain.model

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

/** Invariants of the domain models, checked on the api-spec sample data. */
class SampleModelsTest {
    @Test
    fun `the sample order totals follow the FR-013 formula`() {
        val totals = SampleData.orderRequest.clientTotals
        val lineSum = SampleData.orderRequest.lines.fold(Rupiah.Zero) { sum, line -> sum + line.lineTotal }

        assertThat(totals.subtotal).isEqualTo(lineSum)
        assertThat(totals.total).isEqualTo(totals.subtotal - totals.discount + totals.tax + totals.rounding)
        SampleData.orderRequest.lines.forEach { line ->
            assertThat(line.lineTotal).isEqualTo(line.unitPrice * line.quantity)
        }
        assertThat(SampleData.comboLine.lineTotal).isEqualTo(SampleData.comboLine.unitPrice * 1)
    }

    @Test
    fun `only final payment statuses end polling`() {
        val final = PaymentStatus.entries.filter { it.isFinal }

        assertThat(final).containsExactly(
            PaymentStatus.PAID,
            PaymentStatus.FAILED,
            PaymentStatus.EXPIRED,
            PaymentStatus.CANCELLED,
        )
    }

    @Test
    fun `payments are possible only when the backend and the gateway are healthy`() {
        val healthy = SampleData.health

        assertThat(healthy.canTakePayments).isTrue()
        assertThat(healthy.copy(paymentGateway = HealthStatus.DEGRADED).canTakePayments).isFalse()
        assertThat(healthy.copy(status = HealthStatus.DOWN).canTakePayments).isFalse()
    }

    @Test
    fun `the sample menu is the full design menu`() {
        val menu = SampleData.menu

        assertThat(menu.products).hasSize(23)
        assertThat(menu.combos).hasSize(4)
        assertThat(
            menu.products
                .groupingBy { it.categoryId }
                .eachCount()
                .values,
        ).containsExactly(4, 4, 4, 4, 4, 3)
        val referenced = menu.products.flatMap { it.modifierGroupIds }.toSet()
        assertThat(menu.modifierGroups.map { it.id }).containsExactlyElementsIn(referenced)
    }

    @Test
    fun `secrets are not printed by toString`() {
        assertThat(SampleData.activation.toString()).doesNotContain(SampleData.activation.deviceToken)
        assertThat(SampleData.config.staffPin.toString()).doesNotContain(SampleData.config.staffPin.hash)
        assertThat(SampleData.qrisPayment.toString()).doesNotContain(SampleData.qrisPayment.qrString)
    }

    @Test
    fun `the sample data covers every model of E1 to E14`() {
        assertThat(
            SampleData.merchants.filter { it.status == MerchantStatus.LIVE },
        ).containsExactly(SampleData.urbanGrill)
        assertThat(SampleData.binding.merchantId).isEqualTo(SampleData.MERCHANT_ID)
        assertThat(SampleData.config.mode).isEqualTo(KioskMode.SINGLE)
        assertThat(
            SampleData.menu.products.map { it.id },
        ).containsAtLeastElementsIn(SampleData.menu.featuredProductIds)
        assertThat(SampleData.createdOrder.orderNumber).isEqualTo("A247")
        assertThat(SampleData.cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(SampleData.cardPayment.timeoutSeconds).isEqualTo(90)
        assertThat(SampleData.paymentPaid.paidOrder?.status).isEqualTo(OrderStatus.PAID)
        assertThat(SampleData.paymentDeclined.failureReason).isEqualTo(PaymentFailureReason.DECLINED)
        assertThat(SampleData.terminalResult.approved).isTrue()
        assertThat(SampleData.terminalRelayed.status).isEqualTo(PaymentStatus.PROCESSING)
        assertThat(SampleData.newStaffPin.version).isEqualTo(4)
    }
}
