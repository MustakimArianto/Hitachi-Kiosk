package com.hitachi.kiosk.core.data.mapper

import com.hitachi.kiosk.core.domain.log.KioskLog
import kotlin.time.Instant

/** An enum value by its exact wire name, or `null` when it is missing or unknown. */
internal inline fun <reified E : Enum<E>> enumOrNull(value: String?): E? =
    value?.let { wire -> enumValues<E>().firstOrNull { it.name == wire } }

/**
 * An enum value the app cannot work without (for example a payment status). An unknown value fails the mapping,
 * which [com.hitachi.kiosk.core.data.api.ApiErrorMapper] reports as `DomainError.Unknown`: guessing a payment or
 * order status would be worse than an error (A-15, FR-019).
 */
internal inline fun <reified E : Enum<E>> requiredEnum(
    value: String?,
    field: String,
): E = requireNotNull(enumOrNull<E>(value)) { "Unknown or missing $field" }

/** An enum value with a safe [fallback]; an unknown or missing value logs a warning with the field name only. */
internal inline fun <reified E : Enum<E>> enumOr(
    value: String?,
    fallback: E,
    field: String,
): E =
    enumOrNull<E>(value) ?: fallback.also {
        KioskLog.w("api_enum_fallback", "field" to field, "fallback" to fallback.name)
    }

/** Maps each entry whose enum value is known and drops the others with a warning (for example a `COUNTER` method). */
internal inline fun <T, reified E : Enum<E>, R : Any> List<T>.mapKnown(
    field: String,
    wire: (T) -> String?,
    transform: (T, E) -> R,
): List<R> =
    mapNotNull { item ->
        val value = enumOrNull<E>(wire(item))
        if (value == null) {
            KioskLog.w("api_enum_dropped", "field" to field)
            null
        } else {
            transform(item, value)
        }
    }

/** ISO-8601 UTC timestamps (api-spec §2.3). An invalid value throws [IllegalArgumentException]. */
internal fun String.toInstant(): Instant = Instant.parse(this)

internal fun String?.toInstantOrNull(): Instant? = this?.toInstant()
