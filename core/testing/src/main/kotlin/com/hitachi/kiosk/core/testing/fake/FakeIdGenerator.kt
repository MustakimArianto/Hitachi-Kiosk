package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.id.IdempotencyKey

/** Predictable keys `key-1`, `key-2`, … so tests can assert reuse (TR-INT-11). */
class FakeIdGenerator(
    private val prefix: String = "key",
) : IdGenerator {
    private var counter = 0

    override fun newIdempotencyKey(): IdempotencyKey = IdempotencyKey("$prefix-${++counter}")
}
