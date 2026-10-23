package com.hitachi.kiosk.core.domain.id

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class IdGeneratorTest {
    @Test
    fun `UUID keys are unique version 4 UUIDs`() {
        val generator = UuidIdGenerator()
        val keys = List(100) { generator.newIdempotencyKey() }

        assertThat(keys.toSet()).hasSize(100)
        keys.forEach { assertThat(UUID.fromString(it.value).version()).isEqualTo(4) }
    }

    @Test
    fun `a blank key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { IdempotencyKey(" ") }
    }
}
