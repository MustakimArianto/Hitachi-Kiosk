package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Scripted answers for one fake call (NFR-012, technical-design §14). One-shot answers queued with [enqueue] are used
 * first, in order; after that every call gets the [always] answer. Delays run on the test dispatcher's virtual time.
 */
class Script<T>(
    initial: Outcome<T>,
) {
    private data class Step<T>(
        val outcome: Outcome<T>,
        val delay: Duration,
    )

    private val lock = Any()
    private val queue = ArrayDeque<Step<T>>()
    private var fallback = Step(initial, Duration.ZERO)

    /** Number of calls answered so far. */
    var callCount: Int = 0
        private set

    /** Every later call (after the queued ones) answers [outcome] after [delay]. */
    fun always(
        outcome: Outcome<T>,
        delay: Duration = Duration.ZERO,
    ) {
        synchronized(lock) { fallback = Step(outcome, delay) }
    }

    /** Shorthand for [always] with a success. */
    fun succeed(
        value: T,
        delay: Duration = Duration.ZERO,
    ) = always(Outcome.Success(value), delay)

    /** Shorthand for [always] with a failure. */
    fun fail(
        error: DomainError,
        delay: Duration = Duration.ZERO,
    ) = always(Outcome.Failure(error), delay)

    /** The next call answers [outcome] after [delay], once. */
    fun enqueue(
        outcome: Outcome<T>,
        delay: Duration = Duration.ZERO,
    ) {
        synchronized(lock) { queue.addLast(Step(outcome, delay)) }
    }

    /** Removes the queued one-shot answers. */
    fun clearQueue() {
        synchronized(lock) { queue.clear() }
    }

    /** Answers one call. [extraDelay] is added to the scripted delay (slow network scenario). */
    suspend fun next(extraDelay: Duration = Duration.ZERO): Outcome<T> {
        val step =
            synchronized(lock) {
                callCount++
                queue.removeFirstOrNull() ?: fallback
            }
        delay(step.delay + extraDelay)
        return step.outcome
    }
}
