package com.hitachi.kiosk.core.data.mapper

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.LogCapture
import com.hitachi.kiosk.core.data.api.KioskJson
import com.hitachi.kiosk.core.data.dto.HealthDto
import com.hitachi.kiosk.core.data.dto.MenuDto
import com.hitachi.kiosk.core.data.dto.MerchantConfigDto
import com.hitachi.kiosk.core.data.dto.MerchantDto
import com.hitachi.kiosk.core.data.dto.PaymentStateDto
import com.hitachi.kiosk.core.data.dto.RoundingDto
import com.hitachi.kiosk.core.data.dto.StaffPinDto
import com.hitachi.kiosk.core.data.dto.TaxDto
import com.hitachi.kiosk.core.domain.log.LogLevel
import com.hitachi.kiosk.core.domain.model.BudgetPlannerConfig
import com.hitachi.kiosk.core.domain.model.HealthStatus
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.model.MerchantStatus
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.RoundingMode
import com.hitachi.kiosk.core.testing.fixture.Fixtures
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Unknown or missing enum values are handled explicitly, never coerced silently (technical-design §5.1). */
class UnknownValuesMapperTest {
    private val logs = LogCapture()

    @Before
    fun installLogs() = logs.install()

    @After
    fun uninstallLogs() = logs.uninstall()

    private fun warnings(field: String) =
        logs.records.filter { it.level == LogLevel.WARN && it.fields["field"] == field }

    private val minimalMenu =
        """
        {
          "menuVersion": "m1",
          "products": [
            { "id": "p1", "categoryId": "c", "name": { "en": "No role" }, "price": 1000, "available": true },
            { "id": "p2", "categoryId": "c", "name": { "en": "Odd role" }, "price": 1000, "available": true,
              "role": "SNACK", "badge": { "type": "HOT" } }
          ],
          "modifierGroups": [
            { "id": "g1", "name": { "en": "Odd" }, "type": "SLIDER", "options": [] },
            { "id": "g2", "name": { "en": "Levels" }, "type": "LEVEL", "options": [
              { "id": "o1", "name": { "en": "Cheese" }, "levels": [ { "level": "NONE" }, { "level": "TRIPLE", "priceDelta": 9 } ],
                "defaultLevel": "LOTS" }
            ] }
          ],
          "promoBanners": "not even a list"
        }
        """.trimIndent()

    @Test
    fun `a product with a missing or unknown role is a MAIN and logs a warning`() {
        val menu = KioskJson.decodeFromString<MenuDto>(minimalMenu).toDomain()

        assertThat(menu.products.map { it.role }).containsExactly(ProductRole.MAIN, ProductRole.MAIN)
        assertThat(warnings("menu.products.role")).hasSize(2)
    }

    @Test
    fun `an unknown badge is dropped`() {
        val menu = KioskJson.decodeFromString<MenuDto>(minimalMenu).toDomain()

        assertThat(menu.products[1].badge).isNull()
        assertThat(warnings("menu.products.badge.type")).hasSize(1)
    }

    @Test
    fun `unknown modifier group types and levels are left out, an unknown default level falls back to REGULAR`() {
        val menu = KioskJson.decodeFromString<MenuDto>(minimalMenu).toDomain()

        assertThat(menu.modifierGroups.map { it.id }).containsExactly("g2")
        val option =
            menu.modifierGroups
                .single()
                .options
                .single()
        assertThat(option.levels.map { it.level.name }).containsExactly("NONE")
        assertThat(option.defaultLevel?.name).isEqualTo("REGULAR")
        assertThat(menu.comboSlots.sides).isEmpty()
    }

    @Test
    fun `combo slot options are available unless they say otherwise (FR-011)`() {
        val json =
            """
            { "menuVersion": "m1", "comboSlots": { "SIDE": [
              { "id": "s1", "name": { "en": "Fries" } },
              { "id": "s2", "name": { "en": "Cheese fries" }, "upcharge": 8000, "available": false }
            ] } }
            """.trimIndent()

        val sides =
            KioskJson
                .decodeFromString<MenuDto>(json)
                .toDomain()
                .comboSlots.sides

        assertThat(sides.map { it.available }).containsExactly(true, false).inOrder()
    }

    @Test
    fun `the full menu fixture parses although promoBanners is not mapped`() {
        val menu = KioskJson.decodeFromString<MenuDto>(Fixtures.read("e6_response"))

        assertThat(menu.promoBanners).isNotNull()
        assertThat(menu.toDomain().products).hasSize(23)
    }

    @Test
    fun `config drops unknown payment methods, order types and promos and defaults the rest`() {
        val dto =
            MerchantConfigDto(
                configVersion = "c",
                merchantId = "m",
                mode = "KIOSK_PLUS",
                brandName = "b",
                isOpen = true,
                currency = "IDR",
                tax = TaxDto(rateBasisPoints = 1000, inclusive = false),
                rounding = RoundingDto(mode = "DOWN"),
                promos =
                    listOf(
                        com.hitachi.kiosk.core.data.dto
                            .PromoDto("p", "PERCENT", 1, 1),
                    ),
                orderTypes = listOf("DINE_IN", "DRIVE_THRU"),
                paymentMethods =
                    listOf(
                        com.hitachi.kiosk.core.data.dto
                            .PaymentMethodConfigDto(method = "COUNTER", enabled = true),
                        com.hitachi.kiosk.core.data.dto
                            .PaymentMethodConfigDto(method = "QRIS", enabled = true),
                    ),
                staffPin = StaffPinDto(algorithm = "PLAIN_MOCK_ONLY", hash = "1234", version = 1),
            )

        val config = dto.toDomain()

        assertThat(config.mode).isEqualTo(KioskMode.SINGLE)
        assertThat(config.rounding.mode).isEqualTo(RoundingMode.UP)
        assertThat(config.promos).isEmpty()
        assertThat(config.orderTypes.map { it.name }).containsExactly("DINE_IN")
        assertThat(config.paymentMethods.map { it.method }).containsExactly(PaymentMethod.QRIS)
        assertThat(config.budgetPlanner).isEqualTo(BudgetPlannerConfig(enabled = false))
        assertThat(config.upsell.categoryIds).isEmpty()
        assertThat(config.receipt.address).isNull()
        assertThat(config.idlePromoImageUrls).isEmpty()
        assertThat(warnings("config.paymentMethods.method")).hasSize(1)
    }

    @Test
    fun `a budget planner without limits uses the domain defaults`() {
        val json =
            Fixtures.read("e5_response").replace(
                """"budgetPlanner": { "enabled": true, "overCapPercent": 10, "maxGroupSize": 10,""",
                """"budgetPlanner": { "enabled": true,""",
            )

        val planner = KioskJson.decodeFromString<MerchantConfigDto>(json).toDomain().budgetPlanner

        assertThat(planner.overCapPercent).isEqualTo(BudgetPlannerConfig(enabled = true).overCapPercent)
        assertThat(planner.maxGroupSize).isEqualTo(BudgetPlannerConfig(enabled = true).maxGroupSize)
        assertThat(planner.quickBudgets).hasSize(4)
    }

    @Test
    fun `an unknown merchant status cannot be bound`() {
        val merchant = MerchantDto(id = "m", name = "n", status = "paused").toDomain()

        assertThat(merchant.status).isEqualTo(MerchantStatus.COMING_SOON)
        assertThat(warnings("merchant.status")).hasSize(1)
    }

    @Test
    fun `unknown or missing health values count as DOWN`() {
        val health = HealthDto(status = "OK", components = null).toDomain()
        val odd = KioskJson.decodeFromString<HealthDto>("""{ "status": "MAYBE" }""").toDomain()

        assertThat(health.api).isEqualTo(HealthStatus.DOWN)
        assertThat(health.canTakePayments).isFalse()
        assertThat(odd.status).isEqualTo(HealthStatus.DOWN)
    }

    @Test
    fun `an unknown failure reason is kept as null while the status is mapped`() {
        val state =
            PaymentStateDto(
                paymentId = "p",
                method = "CARD",
                status = "FAILED",
                amount = 1,
                reason = "ALIENS",
            ).toDomain()

        assertThat(state.failureReason).isNull()
        assertThat(state.status.isFinal).isTrue()
        assertThat(warnings("payment.reason")).hasSize(1)
    }

    @Test
    fun `DTOs that hold secrets do not print them`() {
        val pin =
            com.hitachi.kiosk.core.data.dto
                .ChangeStaffPinRequestDto(currentPin = "1234", newPin = "5678")
        val hash = StaffPinDto(algorithm = "a", hash = "b64:secret", version = 1)

        assertThat(pin.toString()).doesNotContain("1234")
        assertThat(hash.toString()).doesNotContain("secret")
    }
}
