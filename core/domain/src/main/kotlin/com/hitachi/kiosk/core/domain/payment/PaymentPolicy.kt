package com.hitachi.kiosk.core.domain.payment

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The timings and switches of [PaymentStateMachine] (technical-design §8.4). The defaults are the product values:
 * tests pass the same ones and move virtual time, so no test needs a custom policy.
 *
 * @property pollInterval E10 polling period (FR-017, TR-NET-10).
 * @property minProcessing the least time "Confirming payment" is shown before Complete (FR-017).
 * @property unknownLimit how long the machine keeps polling without a backend answer before it fails (A-17, TR-INT-06).
 * @property terminalTimeout how long the EDC terminal may stay silent before the sale is cancelled (A-16, TR-INT-05).
 * @property relayTerminalResults send the terminal result to the backend with E12. ADR-0003 (OQ-03) decides whether
 *   the EDC reports to the kiosk or to the gateway; switch it off when the terminal reports to the backend directly.
 */
data class PaymentPolicy(
    val pollInterval: Duration = 2.seconds,
    val minProcessing: Duration = 800.milliseconds,
    val unknownLimit: Duration = 60.seconds,
    val terminalTimeout: Duration = 90.seconds,
    val relayTerminalResults: Boolean = true,
)
