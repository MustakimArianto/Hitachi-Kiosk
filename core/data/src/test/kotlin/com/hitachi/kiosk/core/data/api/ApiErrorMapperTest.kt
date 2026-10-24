package com.hitachi.kiosk.core.data.api

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException

class ApiErrorMapperTest {
    private val mapper = ApiErrorMapper(UnauthorizedHandler.None)

    private suspend fun failWith(error: Exception): Outcome<Unit> =
        mapper.execute<Unit, Unit>("E0", request = { throw error }) { }

    @Test
    fun `socket and call timeouts map to Network with isTimeout`() =
        runTest {
            assertThat(
                failWith(SocketTimeoutException()),
            ).isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = true)))
            assertThat(
                failWith(InterruptedIOException("timeout")),
            ).isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = true)))
        }

    @Test
    fun `a network error is reported for a real call but not for E14, and an HTTP error is not`() =
        runTest(UnconfinedTestDispatcher()) {
            val signal = NetworkErrorSignal()
            var reports = 0
            backgroundScope.launch { signal.reports.collect { reports++ } }
            val reporting = ApiErrorMapper(UnauthorizedHandler.None, signal)

            reporting.execute<Unit, Unit>("E14", request = { throw SocketTimeoutException() }) { }
            reporting.execute<Unit, Unit>("E14", request = { throw IOException() }) { }
            assertThat(reports).isEqualTo(0)

            reporting.execute<Unit, Unit>("E7", request = { throw SocketTimeoutException() }) { }
            assertThat(reports).isEqualTo(1)
            reporting.execute<Unit, Unit>("E10", request = { throw IOException() }) { }
            assertThat(reports).isEqualTo(2)

            reporting.execute<Unit, Unit>("E10", request = { Response.error(503, "".toResponseBody()) }) { }
            reporting.execute<Unit, Unit>("E10", request = { throw IllegalStateException() }) { }
            assertThat(reports).isEqualTo(2)
        }

    @Test
    fun `unexpected runtime exceptions map to Unknown`() =
        runTest {
            assertThat(failWith(IllegalStateException("boom"))).isEqualTo(Outcome.Failure(DomainError.Unknown))
        }

    @Test
    fun `coroutine cancellation is rethrown`() {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { failWith(CancellationException("cancelled")) }
        }
    }

    @Test
    fun `a success is mapped`() =
        runTest {
            val result = mapper.execute("E0", request = { Response.success("ok") }) { it.body() }

            assertThat(result).isEqualTo(Outcome.Success("ok"))
        }
}
