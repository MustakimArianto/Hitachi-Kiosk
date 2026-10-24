package com.hitachi.kiosk.core.hardware.terminal

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.TerminalEvent
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UnavailableCardTerminalTest {
    private val terminal = UnavailableCardTerminal()

    @Test
    fun `a sale fails at once with a terminal error and cancel does nothing`() =
        runTest {
            val events = terminal.startSale(Rupiah(172_700), "TRX-1").toList()

            assertThat(events).containsExactly(TerminalEvent.Error("adapter_unavailable"))
            terminal.cancel()
        }

    @Test
    fun `the self-test reports a hardware error (FR-025)`() =
        runTest {
            assertThat(terminal.selfTest())
                .isEqualTo(Outcome.Failure(DomainError.Hardware("card_terminal", "adapter_unavailable")))
        }
}
