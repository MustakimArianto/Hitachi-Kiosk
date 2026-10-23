package com.hitachi.kiosk.feature.activation

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.setup.ActivateKiosk
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fake.FakeDeviceIdentityProvider
import com.hitachi.kiosk.core.testing.fake.FakeMerchantRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** SCR-02 behaviour (FR-002, FR-003; TC-007–TC-012, TC-179). */
class SignInViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val identity = FakeDeviceIdentityProvider()
    private val activation = FakeActivationRepository()
    private val merchants = FakeMerchantRepository()

    private fun viewModel() = SignInViewModel(identity, ActivateKiosk(activation, merchants), versionName = "0.2.0")

    @Test
    fun `Log in is busy until the serial number is read, then enabled`() =
        runTest(main.dispatcher) {
            identity.slow(5.seconds)
            val vm = viewModel()

            runCurrent()
            assertThat(
                vm.state.value,
            ).isEqualTo(SignInUiState(versionName = "0.2.0", buttonEnabled = false, busy = true))

            advanceTimeBy(5.seconds)
            runCurrent()
            assertThat(vm.state.value)
                .isEqualTo(
                    SignInUiState(
                        versionName = "0.2.0",
                        button = SignInButton.LOG_IN,
                        buttonEnabled = true,
                        busy = false,
                    ),
                )
        }

    @Test
    fun `an unreadable serial number shows the error and keeps Log in disabled`() =
        runTest(main.dispatcher) {
            identity.unavailable()
            val vm = viewModel()
            advanceUntilIdle()

            assertThat(vm.state.value.error).isEqualTo(SignInError.SerialUnreadable)
            assertThat(vm.state.value.buttonEnabled).isFalse()

            vm.onEvent(SignInEvent.LogIn)
            advanceUntilIdle()
            assertThat(activation.activations).isEmpty()
        }

    @Test
    fun `a registered device goes to Choose a restaurant`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.onEvent(SignInEvent.LogIn)
            assertThat(vm.state.value.button).isEqualTo(SignInButton.SIGNING_IN)
            assertThat(vm.state.value.busy).isTrue()
            assertThat(vm.state.value.buttonEnabled).isFalse()

            assertThat(vm.effect.first()).isEqualTo(SignInEffect.NavigateToMerchants)
            assertThat(activation.activations).containsExactly(SampleData.deviceIdentity)
        }

    @Test
    fun `a pre-assigned merchant goes straight to Idle`() =
        runTest(main.dispatcher) {
            activation.activateScript.succeed(SampleData.activation.copy(boundMerchantId = SampleData.MERCHANT_ID))
            val vm = viewModel()
            advanceUntilIdle()

            vm.onEvent(SignInEvent.LogIn)

            assertThat(vm.effect.first()).isEqualTo(SignInEffect.NavigateToIdle)
        }

    @Test
    fun `a double tap sends one activation`() =
        runTest(main.dispatcher) {
            activation.activateScript.succeed(SampleData.activation, delay = 1.seconds)
            val vm = viewModel()
            advanceUntilIdle()

            vm.onEvent(SignInEvent.LogIn)
            vm.onEvent(SignInEvent.LogIn)
            advanceUntilIdle()

            assertThat(activation.activations).hasSize(1)
        }

    @Test
    fun `not registered shows the serial number and Try again`() =
        runTest(main.dispatcher) {
            activation.activateScript.fail(DomainError.Server(code = "DEVICE_NOT_REGISTERED", httpStatus = 404))
            val vm = viewModel()
            advanceUntilIdle()

            vm.onEvent(SignInEvent.LogIn)
            advanceUntilIdle()

            assertThat(vm.state.value)
                .isEqualTo(
                    SignInUiState(
                        versionName = "0.2.0",
                        button = SignInButton.TRY_AGAIN,
                        buttonEnabled = true,
                        busy = false,
                        error = SignInError.NotRegistered(SampleData.SERIAL_NUMBER),
                    ),
                )
        }

    @Test
    fun `each backend failure maps to its panel and the button is tappable again`() =
        runTest(main.dispatcher) {
            val cases =
                mapOf(
                    DomainError.Server(code = "DEVICE_DISABLED", httpStatus = 403) to SignInError.Disabled,
                    DomainError.Network(isTimeout = true) to SignInError.Network,
                    DomainError.Network(isTimeout = false) to SignInError.Network,
                    DomainError.Server(code = "VALIDATION_ERROR", httpStatus = 400) to SignInError.Generic,
                    DomainError.Server(code = "SERVER_ERROR", httpStatus = 503) to SignInError.Generic,
                    DomainError.Unknown to SignInError.Generic,
                )
            val vm = viewModel()
            advanceUntilIdle()
            for ((error, panel) in cases) {
                activation.activateScript.fail(error)

                vm.onEvent(SignInEvent.LogIn)
                advanceUntilIdle()

                assertThat(vm.state.value.error).isEqualTo(panel)
                assertThat(vm.state.value.buttonEnabled).isTrue()
                assertThat(vm.state.value.button).isEqualTo(SignInButton.TRY_AGAIN)
            }
        }

    @Test
    fun `Try again clears the panel while signing in and can succeed`() =
        runTest(main.dispatcher) {
            activation.activateScript.enqueue(Outcome.Failure(DomainError.Network(isTimeout = true)))
            activation.activateScript.succeed(SampleData.activation, delay = 1.seconds)
            val vm = viewModel()
            advanceUntilIdle()
            vm.onEvent(SignInEvent.LogIn)
            advanceUntilIdle()

            vm.onEvent(SignInEvent.LogIn)
            runCurrent()
            assertThat(vm.state.value.error).isNull()
            assertThat(vm.state.value.button).isEqualTo(SignInButton.SIGNING_IN)

            assertThat(vm.effect.first()).isEqualTo(SignInEffect.NavigateToMerchants)
        }
}
