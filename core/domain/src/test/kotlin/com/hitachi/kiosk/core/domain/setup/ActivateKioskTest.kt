package com.hitachi.kiosk.core.domain.setup

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.BindResult
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fake.FakeMerchantRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ActivateKioskTest {
    private val activation = FakeActivationRepository()
    private val merchants = FakeMerchantRepository()
    private val activateKiosk = ActivateKiosk(activation, merchants)

    @Test
    fun `a registered device without a merchant goes to Choose a restaurant`() =
        runTest {
            val outcome = activateKiosk(SampleData.deviceIdentity)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.activatedState))
            assertThat(activation.activations).containsExactly(SampleData.deviceIdentity)
            assertThat(merchants.bound).isEmpty()
        }

    @Test
    fun `an activation error is returned as it is`() =
        runTest {
            val error = DomainError.Server(code = "DEVICE_NOT_REGISTERED", httpStatus = 404)
            activation.activateScript.fail(error)

            assertThat(activateKiosk(SampleData.deviceIdentity)).isEqualTo(Outcome.Failure(error))
            assertThat(merchants.bound).isEmpty()
        }

    @Test
    fun `a merchant pre-assigned in the dashboard is bound so Choose a restaurant is skipped`() =
        runTest {
            activation.activateScript.succeed(SampleData.activation.copy(boundMerchantId = SampleData.MERCHANT_ID))

            val outcome = activateKiosk(SampleData.deviceIdentity)

            assertThat(outcome).isEqualTo(Outcome.Success(SampleData.boundState))
            assertThat(merchants.bound).containsExactly(SampleData.urbanGrill)
        }

    @Test
    fun `a failed pre-assigned binding leaves the kiosk activated for SCR-03`() =
        runTest {
            activation.activateScript.succeed(SampleData.activation.copy(boundMerchantId = SampleData.MERCHANT_ID))
            merchants.bindResult = BindResult.MenuDownloadFailed(DomainError.Network(isTimeout = true))

            assertThat(activateKiosk(SampleData.deviceIdentity)).isEqualTo(Outcome.Success(SampleData.activatedState))
        }

    @Test
    fun `a closed or unknown pre-assigned merchant is not bound`() =
        runTest {
            activation.activateScript.succeed(
                SampleData.activation.copy(
                    boundMerchantId = SampleData.MERCHANT_ID,
                    merchants = listOf(SampleData.urbanGrillClosed),
                ),
            )
            assertThat(activateKiosk(SampleData.deviceIdentity)).isEqualTo(Outcome.Success(SampleData.activatedState))

            activation.activateScript.succeed(SampleData.activation.copy(boundMerchantId = "mer_unknown"))
            assertThat(activateKiosk(SampleData.deviceIdentity)).isEqualTo(Outcome.Success(SampleData.activatedState))
            assertThat(merchants.bound).isEmpty()
            assertThat(activation.state.value).isInstanceOf(DeviceState.Activated::class.java)
        }
}
