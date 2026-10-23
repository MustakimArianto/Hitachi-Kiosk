package com.hitachi.kiosk.core.domain.id

/**
 * The `Idempotency-Key` of a mutating call (api-spec §2.2, BR-08). Callers create it and reuse it for every retry of
 * the same request; transport code never generates one (technical-design §5.1, §5.3).
 */
@JvmInline
value class IdempotencyKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "An idempotency key must not be blank" }
    }
}

/** Creates identifiers, so tests can script them (TR-INT-11). */
fun interface IdGenerator {
    fun newIdempotencyKey(): IdempotencyKey
}

/** UUID v4 keys (technical-design §3). */
class UuidIdGenerator : IdGenerator {
    override fun newIdempotencyKey(): IdempotencyKey =
        IdempotencyKey(
            java.util.UUID
                .randomUUID()
                .toString(),
        )
}
