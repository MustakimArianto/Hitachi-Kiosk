package com.hitachi.kiosk.core.domain

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.error.DomainError
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RetryTest {
    @Test
    fun `transient errors are network, rate limiting and server errors`() {
        assertThat(DomainError.Network().isTransient()).isTrue()
        assertThat(DomainError.Network(isTimeout = true).isTransient()).isTrue()
        assertThat(DomainError.Server("SERVER_ERROR", 500).isTransient()).isTrue()
        assertThat(DomainError.Server("GATEWAY_ERROR", 503).isTransient()).isTrue()
        assertThat(DomainError.Server("RATE_LIMITED", 429).isTransient()).isTrue()
    }

    @Test
    fun `business errors are never retried`() {
        assertThat(DomainError.Server("VALIDATION_ERROR", 400).isTransient()).isFalse()
        assertThat(DomainError.Server("HTTP_UNKNOWN").isTransient()).isFalse()
        assertThat(DomainError.Unauthorized.isTransient()).isFalse()
        assertThat(DomainError.PriceChanged(null, null, null).isTransient()).isFalse()
        assertThat(DomainError.ItemUnavailable(emptyList(), emptyList()).isTransient()).isFalse()
        assertThat(DomainError.Conflict("MERCHANT_CLOSED").isTransient()).isFalse()
        assertThat(DomainError.Hardware("printer", "offline").isTransient()).isFalse()
        assertThat(DomainError.Unknown.isTransient()).isFalse()
    }

    @Test
    fun `a success on the first call is returned without waiting`() =
        runTest {
            var calls = 0
            val outcome =
                retryWithBackoff {
                    calls++
                    Outcome.Success("ok")
                }

            assertThat(outcome).isEqualTo(Outcome.Success("ok"))
            assertThat(calls).isEqualTo(1)
            assertThat(currentTime).isEqualTo(0)
        }

    @Test
    fun `transient failures are retried after 1, 2 and 4 seconds`() =
        runTest {
            val callTimes = mutableListOf<Long>()
            val outcome =
                retryWithBackoff<String> {
                    callTimes += currentTime
                    Outcome.Failure(DomainError.Network())
                }

            assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.Network()))
            assertThat(callTimes).containsExactly(0L, 1_000L, 3_000L, 7_000L).inOrder()
        }

    @Test
    fun `a retry that succeeds stops the backoff`() =
        runTest {
            var calls = 0
            val outcome =
                retryWithBackoff {
                    calls++
                    if (calls < 2) Outcome.Failure(DomainError.Server("SERVER_ERROR", 503)) else Outcome.Success(calls)
                }

            assertThat(outcome).isEqualTo(Outcome.Success(2))
            assertThat(currentTime).isEqualTo(1_000)
        }

    @Test
    fun `a non-transient failure is returned at once`() =
        runTest {
            var calls = 0
            val outcome =
                retryWithBackoff<Unit> {
                    calls++
                    Outcome.Failure(DomainError.Unauthorized)
                }

            assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.Unauthorized))
            assertThat(calls).isEqualTo(1)
        }
}
