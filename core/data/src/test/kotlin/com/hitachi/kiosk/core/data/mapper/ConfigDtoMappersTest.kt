package com.hitachi.kiosk.core.data.mapper

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.api.KioskJson
import com.hitachi.kiosk.core.data.dto.MerchantConfigDto
import com.hitachi.kiosk.core.domain.model.BudgetPlannerConfig
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

/** The cache stores E5 as JSON and reads it back through the E5 mapper (technical-design §6.1). */
class ConfigDtoMappersTest {
    @Test
    fun `a config survives the JSON round trip`() {
        val json = KioskJson.encodeToString(MerchantConfigDto.serializer(), SampleData.config.toDto())

        assertThat(KioskJson.decodeFromString(MerchantConfigDto.serializer(), json).toDomain())
            .isEqualTo(SampleData.config)
    }

    @Test
    fun `optional parts survive too`() {
        val sparse =
            SampleData.config.copy(
                accentColor = null,
                tax = SampleData.config.tax.copy(label = null),
                receipt = SampleData.config.receipt.copy(defaultFooter = null),
                budgetPlanner = BudgetPlannerConfig(enabled = false),
                idlePromoImageUrls = listOf(null),
                paymentMethods = SampleData.config.paymentMethods.map { it.copy(terminal = null) },
                promos = SampleData.config.promos.map { it.copy(label = null) },
            )

        assertThat(sparse.toDto().toDomain()).isEqualTo(sparse)
    }
}
