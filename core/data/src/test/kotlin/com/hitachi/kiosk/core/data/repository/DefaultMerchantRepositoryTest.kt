package com.hitachi.kiosk.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.repository.BindResult
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi.Endpoint
import com.hitachi.kiosk.core.testing.fake.FakeScenario
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultMerchantRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val harness by lazy { SetupHarness(folder.root) }
    private val serverError = DomainError.Server(code = "SERVER_ERROR", httpStatus = 503)

    private suspend fun activate() {
        harness.activation.activate(SampleData.deviceIdentity)
    }

    @Test
    fun `the merchant list comes from E2 for this device`() =
        runTest {
            activate()

            assertThat(harness.merchants.merchants()).isEqualTo(Outcome.Success(SampleData.merchants))
            assertThat(
                harness.backend
                    .callsTo(Endpoint.E2)
                    .single()
                    .args,
            ).containsExactly(SampleData.DEVICE_ID)
        }

    @Test
    fun `E2 is retried on a transient error`() =
        runTest {
            activate()
            harness.backend.merchants.enqueue(Outcome.Failure(DomainError.Network()))

            assertThat(harness.merchants.merchants()).isEqualTo(Outcome.Success(SampleData.merchants))
            assertThat(harness.backend.callsTo(Endpoint.E2)).hasSize(2)
        }

    @Test
    fun `without an activation there is nothing to list or bind`() =
        runTest {
            assertThat(harness.merchants.merchants()).isEqualTo(Outcome.Failure(DomainError.Unauthorized))
            assertThat(harness.merchants.bind(SampleData.urbanGrill))
                .isEqualTo(BindResult.BindFailed(DomainError.Unauthorized))
            assertThat(harness.backend.calls).isEmpty()
        }

    @Test
    fun `binding calls E3, downloads config and menu, and stores the binding with its mode`() =
        runTest {
            activate()

            val result = harness.merchants.bind(SampleData.urbanGrill)

            assertThat(result).isEqualTo(BindResult.Bound(SampleData.boundState))
            assertThat(
                harness.backend
                    .callsTo(Endpoint.E3)
                    .single()
                    .args,
            ).containsExactly(SampleData.DEVICE_ID, SampleData.MERCHANT_ID)
            assertThat(harness.backend.callsTo(Endpoint.E5)).hasSize(1)
            assertThat(harness.backend.callsTo(Endpoint.E6)).hasSize(1)
            assertThat(harness.session.state.value).isEqualTo(SampleData.boundState)
            // FR-005 AC1: the mode stored is the config's SINGLE.
            assertThat(harness.deviceStore.read()?.mode).isEqualTo(KioskMode.SINGLE)
        }

    @Test
    fun `E3 retries reuse one idempotency key`() =
        runTest {
            activate()
            harness.backend.bind.enqueue(Outcome.Failure(serverError))

            harness.merchants.bind(SampleData.urbanGrill)

            val keys = harness.backend.callsTo(Endpoint.E3).map { it.key }
            assertThat(keys).containsExactly(IdempotencyKey("key-1"), IdempotencyKey("key-1"))
        }

    @Test
    fun `a failed E3 is a bind failure and nothing is stored`() =
        runTest {
            activate()
            val notLive = DomainError.Conflict("MERCHANT_NOT_LIVE")
            harness.backend.bind.fail(notLive)

            assertThat(harness.merchants.bind(SampleData.urbanGrill)).isEqualTo(BindResult.BindFailed(notLive))
            assertThat(harness.backend.callsTo(Endpoint.E5)).isEmpty()
            assertThat(harness.session.state.value).isEqualTo(SampleData.activatedState)
        }

    @Test
    fun `a failed menu download keeps the kiosk unbound so a restart shows SCR-03`() =
        runTest {
            activate()
            harness.backend.apply(FakeScenario.MENU_DOWNLOAD_FAILED)

            val result = harness.merchants.bind(SampleData.urbanGrill)

            assertThat(result).isEqualTo(BindResult.MenuDownloadFailed(serverError))
            assertThat(harness.session.state.value).isEqualTo(SampleData.activatedState)
            assertThat(harness.deviceStore.read()?.merchant).isNull()
            // E6 was retried 1 s, 2 s, 4 s before giving up (technical-design §5.3).
            assertThat(harness.backend.callsTo(Endpoint.E6)).hasSize(4)
        }

    @Test
    fun `a retry after a failed menu download binds`() =
        runTest {
            activate()
            harness.backend.menu.enqueue(Outcome.Failure(DomainError.Server("SERVER_ERROR", 400)))

            assertThat(
                harness.merchants.bind(SampleData.urbanGrill),
            ).isInstanceOf(BindResult.MenuDownloadFailed::class.java)
            assertThat(harness.merchants.bind(SampleData.urbanGrill)).isEqualTo(BindResult.Bound(SampleData.boundState))
        }

    @Test
    fun `a food court config is stored but the kiosk stays single merchant`() =
        runTest {
            activate()
            val config = SampleData.config.copy(mode = KioskMode.FOOD_COURT)
            harness.backend.config.succeed(Conditional.Modified(config, null))

            val result = harness.merchants.bind(SampleData.urbanGrill) as BindResult.Bound

            assertThat(result.state.mode).isEqualTo(KioskMode.FOOD_COURT)
        }

    @Test
    fun `a 401 during the bind leaves nothing bound`() =
        runTest {
            activate()
            harness.backend.bind.fail(DomainError.Unauthorized)

            assertThat(harness.merchants.bind(SampleData.urbanGrill))
                .isEqualTo(BindResult.BindFailed(DomainError.Unauthorized))
        }

    @Test
    fun `log out calls E4 and wipes the device`() =
        runTest {
            activate()
            harness.merchants.bind(SampleData.urbanGrill)

            harness.merchants.logOut()

            assertThat(
                harness.backend
                    .callsTo(Endpoint.E4)
                    .single()
                    .args,
            ).containsExactly(SampleData.DEVICE_ID)
            assertThat(harness.session.state.value).isEqualTo(DeviceState.NotActivated)
            assertThat(harness.tokens.token).isNull()
            assertThat(harness.deviceStore.read()).isNull()
        }

    @Test
    fun `log out wipes the device even when E4 fails`() =
        runTest {
            activate()
            harness.backend.unbind.fail(DomainError.Network())

            harness.merchants.logOut()

            assertThat(harness.backend.callsTo(Endpoint.E4)).hasSize(1)
            assertThat(harness.session.state.value).isEqualTo(DeviceState.NotActivated)
        }

    @Test
    fun `log out without an activation only wipes`() =
        runTest {
            harness.merchants.logOut()

            assertThat(harness.backend.calls).isEmpty()
            assertThat(harness.session.state.value).isEqualTo(DeviceState.NotActivated)
        }
}
