package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.PrinterConnection
import com.hitachi.kiosk.core.domain.device.PrinterDriver
import com.hitachi.kiosk.core.domain.device.PrinterState
import com.hitachi.kiosk.core.domain.error.DomainError

/**
 * A [PrinterDriver] that records every job and can be ready, offline, out of paper or fail mid-job (FR-022,
 * architecture §9).
 */
class FakePrinterDriver(
    override val connection: PrinterConnection = PrinterConnection.BUILT_IN,
    var state: PrinterState = PrinterState.READY,
) : PrinterDriver {
    /** When true, the next job sends half of its bytes and then fails. */
    var failMidJob: Boolean = false

    private val jobs = mutableListOf<ByteArray>()

    /** Bytes received per job, including the partial bytes of a failed job. */
    val writtenJobs: List<ByteArray> get() = jobs.toList()

    override suspend fun status(): PrinterState = state

    override suspend fun write(bytes: ByteArray): Outcome<Unit> {
        if (state != PrinterState.READY) {
            return Outcome.Failure(DomainError.Hardware(device = "printer", reason = state.name.lowercase()))
        }
        if (failMidJob) {
            failMidJob = false
            jobs += bytes.copyOf(bytes.size / 2)
            return Outcome.Failure(DomainError.Hardware(device = "printer", reason = "failed_mid_job"))
        }
        jobs += bytes.copyOf()
        return Outcome.Success(Unit)
    }
}
