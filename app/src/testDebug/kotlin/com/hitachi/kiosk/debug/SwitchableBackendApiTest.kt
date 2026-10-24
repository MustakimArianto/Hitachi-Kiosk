package com.hitachi.kiosk.debug

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeScenario
import com.hitachi.kiosk.core.testing.fake.TestClock
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SwitchableBackendApiTest {
    private val real = FakeBackendApi().apply { health.fail(DomainError.Network()) }
    private var settings = DeveloperSettings()
    private val api = SwitchableBackendApi(real = real, settings = { settings }, clock = TestClock())

    @Test
    fun `fakes mode answers from a fake with the selected scenario`() =
        runTest {
            settings = DeveloperSettings(BackendMode.FAKES, FakeScenario.ACTIVATE_UNREGISTERED)

            assertThat(api.activate(SampleData.activationRequest()))
                .isEqualTo(Outcome.Failure(DomainError.Server("DEVICE_NOT_REGISTERED", 404)))
            assertThat(real.calls).isEmpty()
        }

    @Test
    fun `mock server and real modes use the real client`() =
        runTest {
            settings = DeveloperSettings(BackendMode.MOCK_SERVER)
            assertThat(api.health()).isEqualTo(Outcome.Failure(DomainError.Network()))

            settings = DeveloperSettings(BackendMode.REAL)
            assertThat(api.health()).isEqualTo(Outcome.Failure(DomainError.Network()))
            assertThat(real.callsTo(FakeBackendApi.Endpoint.E14)).hasSize(2)
        }

    @Test
    fun `the fake is kept while the scenario stays and replaced when it changes`() =
        runTest {
            settings = DeveloperSettings(BackendMode.FAKES, FakeScenario.SERVER_ERROR_ONCE)
            val key = IdempotencyKey("k")

            assertThat(api.createOrder(SampleData.orderRequest, key)).isInstanceOf(Outcome.Failure::class.java)
            assertThat(
                api.createOrder(SampleData.orderRequest, key),
            ).isEqualTo(Outcome.Success(SampleData.createdOrder))

            settings = DeveloperSettings(BackendMode.FAKES, FakeScenario.HAPPY_PATH)
            val happy = api.target()
            settings = DeveloperSettings(BackendMode.FAKES, FakeScenario.SERVER_ERROR_ONCE)
            assertThat(api.target()).isNotSameInstanceAs(happy)
            assertThat(api.createOrder(SampleData.orderRequest, key)).isInstanceOf(Outcome.Failure::class.java)
        }

    @Test
    fun `fakes mode serves the catalog with the bundled demo photos`() =
        runTest {
            settings = DeveloperSettings(BackendMode.FAKES)

            val menu = (api.menu(SampleData.MERCHANT_ID, null) as Outcome.Success).value as Conditional.Modified
            val config = (api.config(SampleData.MERCHANT_ID, null) as Outcome.Success).value as Conditional.Modified

            assertThat(
                menu.value.products
                    .first { it.id == "s1" }
                    .imageUrl,
            ).endsWith("/fries.webp")
            assertThat(config.value.logoUrl).endsWith("/logo.webp")
        }
}
