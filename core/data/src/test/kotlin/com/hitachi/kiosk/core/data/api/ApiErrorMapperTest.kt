package com.hitachi.kiosk.core.data.api

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response
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
