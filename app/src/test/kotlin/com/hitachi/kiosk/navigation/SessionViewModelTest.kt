package com.hitachi.kiosk.navigation

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The app returns to Sign in when an activated kiosk loses its activation (FR-003, TR-NET-06; TC-013). */
class SessionViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val activation = FakeActivationRepository()

    @Test
    fun `losing the activation signs out once, and the starting state does not`() =
        runTest(main.dispatcher) {
            val vm = SessionViewModel(activation)
            var signOuts = 0
            backgroundScope.launch { vm.signedOut.collect { signOuts++ } }
            runCurrent()
            assertThat(signOuts).isEqualTo(0)

            activation.emit(SampleData.activatedState)
            activation.emit(SampleData.boundState)
            runCurrent()
            assertThat(signOuts).isEqualTo(0)

            activation.emit(DeviceState.NotActivated)
            runCurrent()
            assertThat(signOuts).isEqualTo(1)
        }

    @Test
    fun `a bound kiosk at subscription signs out when the activation is lost`() =
        runTest(main.dispatcher) {
            activation.emit(SampleData.boundState)
            val vm = SessionViewModel(activation)
            var signOuts = 0
            backgroundScope.launch { vm.signedOut.collect { signOuts++ } }
            runCurrent()

            activation.emit(DeviceState.NotActivated)
            runCurrent()

            assertThat(signOuts).isEqualTo(1)
        }
}
