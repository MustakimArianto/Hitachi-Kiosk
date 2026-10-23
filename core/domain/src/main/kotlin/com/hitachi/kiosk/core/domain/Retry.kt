package com.hitachi.kiosk.core.domain

import com.hitachi.kiosk.core.domain.error.DomainError
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Backoff delays for idempotent calls: 1 s, 2 s, 4 s (NFR-010, technical-design §5.3). */
val DefaultRetryDelays: List<Duration> = listOf(1.seconds, 2.seconds, 4.seconds)

/**
 * Transient failures worth retrying: no network, and server or rate-limit errors (technical-design §5.3).
 * Business errors (price changed, unauthorized, conflicts) are never retried.
 */
fun DomainError.isTransient(): Boolean =
    when (this) {
        is DomainError.Network -> {
            true
        }

        is DomainError.Server -> {
            httpStatus == HTTP_TOO_MANY_REQUESTS ||
                (httpStatus != null && httpStatus >= HTTP_SERVER_ERROR)
        }

        else -> {
            false
        }
    }

/**
 * Runs [block] and retries it after each delay in [delays] while the failure matches [retryOn] (NFR-010).
 * Retries are written in the repositories, not in OkHttp, so they stay visible and testable (architecture §7).
 * Only call this for GETs and for mutations that carry an `Idempotency-Key` (TR-NET-04).
 */
suspend fun <T> retryWithBackoff(
    delays: List<Duration> = DefaultRetryDelays,
    retryOn: (DomainError) -> Boolean = DomainError::isTransient,
    block: suspend () -> Outcome<T>,
): Outcome<T> {
    var outcome = block()
    for (wait in delays) {
        val error = outcome.errorOrNull() ?: return outcome
        if (!retryOn(error)) return outcome
        delay(wait)
        outcome = block()
    }
    return outcome
}

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
