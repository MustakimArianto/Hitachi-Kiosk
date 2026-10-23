package com.hitachi.kiosk.core.domain.device

import com.hitachi.kiosk.core.domain.Outcome

/**
 * Moves ESC/POS bytes to one printer (FR-022, technical-design §9.2 TD-08). Rendering happens before this port, so
 * drivers only transport bytes.
 */
interface PrinterDriver {
    val connection: PrinterConnection

    /** Current printer state, from the SDK paper sensor or `DLE EOT 4` where the printer answers. */
    suspend fun status(): PrinterState

    /** Sends one job. A failure carries a [com.hitachi.kiosk.core.domain.error.DomainError.Hardware]. */
    suspend fun write(bytes: ByteArray): Outcome<Unit>
}

enum class PrinterConnection { BUILT_IN, USB, BLUETOOTH }

enum class PrinterState { READY, OFFLINE, OUT_OF_PAPER }
