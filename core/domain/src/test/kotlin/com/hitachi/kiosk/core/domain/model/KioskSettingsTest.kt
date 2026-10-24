package com.hitachi.kiosk.core.domain.model

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

class KioskSettingsTest {
    private val config = SampleData.config

    @Test
    fun `both methods show by default, QRIS first (FR-015)`() {
        assertThat(config.visiblePaymentMethods(KioskSettings()))
            .containsExactly(PaymentMethod.QRIS, PaymentMethod.CARD)
            .inOrder()
    }

    @Test
    fun `settings only restrict what config allows (FR-025 AC1, A-24, TC-065)`() {
        val qrisOnly = KioskSettings(paymentMethods = setOf(PaymentMethod.QRIS))
        val cardOffInConfig =
            config.copy(
                paymentMethods = config.paymentMethods.map { it.copy(enabled = it.method == PaymentMethod.QRIS) },
            )

        assertThat(config.visiblePaymentMethods(qrisOnly)).containsExactly(PaymentMethod.QRIS)
        assertThat(cardOffInConfig.visiblePaymentMethods(KioskSettings())).containsExactly(PaymentMethod.QRIS)
        assertThat(cardOffInConfig.visiblePaymentMethods(KioskSettings(paymentMethods = setOf(PaymentMethod.CARD))))
            .isEmpty()
    }
}
