package com.hitachi.kiosk.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.api.InMemoryDeviceCredentialsStore
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi.Endpoint
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultActivationRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val harness by lazy { SetupHarness(folder.root) }

    @Test
    fun `activation sends the serial number, model, OS and app version`() =
        runTest {
            harness.activation.activate(SampleData.deviceIdentity)

            val call = harness.backend.callsTo(Endpoint.E1).single()
            assertThat(call.args).containsExactly(SampleData.activationRequest(appVersion = "0.2.0"))
        }

    @Test
    fun `a successful activation is stored and survives a restart`() =
        runTest {
            val outcome = harness.activation.activate(SampleData.deviceIdentity)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.activation))
            assertThat(harness.activation.state.value).isEqualTo(SampleData.activatedState)
            assertThat(harness.tokens.token).isEqualTo(SampleData.activation.deviceToken)
            // FR-003 AC4: a restart reads the stored activation.
            assertThat(SetupHarnessRestart(harness).load()).isEqualTo(SampleData.activatedState)
        }

    @Test
    fun `a failed activation stores nothing and is not retried`() =
        runTest {
            val notRegistered = DomainError.Server(code = "DEVICE_NOT_REGISTERED", httpStatus = 404)
            harness.backend.activate.fail(notRegistered)

            assertThat(harness.activation.activate(SampleData.deviceIdentity)).isEqualTo(Outcome.Failure(notRegistered))
            assertThat(harness.backend.callsTo(Endpoint.E1)).hasSize(1)
            assertThat(harness.activation.state.value).isEqualTo(DeviceState.NotActivated)
            assertThat(harness.tokens.token).isNull()
        }

    @Test
    fun `a network failure on activation is not retried`() =
        runTest {
            harness.backend.activate.fail(DomainError.Network(isTimeout = true))

            harness.activation.activate(SampleData.deviceIdentity)

            assertThat(harness.backend.callsTo(Endpoint.E1)).hasSize(1)
        }

    @Test
    fun `load reports the stored state`() =
        runTest {
            assertThat(harness.activation.load()).isEqualTo(DeviceState.NotActivated)
        }
}

/** A fresh [DeviceSession] over the same storage, as after a process restart. */
private class SetupHarnessRestart(
    harness: SetupHarness,
) {
    private val session =
        DeviceSession(
            harness.tokens,
            harness.deviceStore,
            InMemoryDeviceCredentialsStore(),
            harness.catalogStore,
            harness.pendingOrders,
        )

    suspend fun load(): DeviceState = session.load()
}
