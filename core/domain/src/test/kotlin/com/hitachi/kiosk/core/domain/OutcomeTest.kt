package com.hitachi.kiosk.core.domain

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.error.DomainError
import org.junit.Test

class OutcomeTest {
    private val success: Outcome<Int> = Outcome.Success(2)
    private val failure: Outcome<Int> = Outcome.Failure(DomainError.Unknown)

    @Test
    fun `getOrNull returns the value only on success`() {
        assertThat(success.getOrNull()).isEqualTo(2)
        assertThat(failure.getOrNull()).isNull()
    }

    @Test
    fun `errorOrNull returns the error only on failure`() {
        assertThat(success.errorOrNull()).isNull()
        assertThat(failure.errorOrNull()).isEqualTo(DomainError.Unknown)
    }

    @Test
    fun `map transforms a success and keeps a failure`() {
        assertThat(success.map { it * 10 }).isEqualTo(Outcome.Success(20))
        assertThat(failure.map { it * 10 }).isEqualTo(failure)
    }

    @Test
    fun `flatMap chains a success and short-circuits a failure`() {
        assertThat(success.flatMap { Outcome.Success("v$it") }).isEqualTo(Outcome.Success("v2"))
        assertThat(success.flatMap { Outcome.Failure(DomainError.Unauthorized) })
            .isEqualTo(Outcome.Failure(DomainError.Unauthorized))
        var called = false
        assertThat(
            failure.flatMap {
                called = true
                Outcome.Success(it)
            },
        ).isEqualTo(failure)
        assertThat(called).isFalse()
    }
}
