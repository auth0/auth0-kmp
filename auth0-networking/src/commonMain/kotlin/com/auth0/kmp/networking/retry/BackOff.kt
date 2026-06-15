package com.auth0.kmp.networking.retry

import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration


/**
 * Strategy for spacing successive retry attempts.
 */
sealed interface Backoff {

    /** Waits the same [delay] before every retry. */
    data class Fixed(val delay: Duration) : Backoff


    /**
     * Grows the wait geometrically: [base] times [multiplier] raised to the
     * attempt number, capped at [maxDelay].
     *
     * @param base the delay before the first retry.
     * @param multiplier factor each successive delay is multiplied by.
     * @param maxDelay upper bound applied to every computed delay.
     * @param jitter when true, each delay is randomly shortened to between 50%
     *   and 100% of its computed value to avoid synchronized retry storms.
     */
    data class Exponential(
        val base: Duration,
        val multiplier: Double = 2.0,
        val maxDelay: Duration,
        val jitter: Boolean = true,
    ) : Backoff
}

/**
 * Computes the delay before the given 1-based retry [attempt].
 */
internal fun Backoff.delayFor(attempt: Int, random: Random = Random.Default): Duration =
    when (this) {
        is Backoff.Fixed -> delay
        is Backoff.Exponential -> {
            val raw = base * multiplier.pow(attempt - 1)
            val capped = minOf(raw, maxDelay)
            if (jitter) capped * random.nextDouble(0.5, 1.0) else capped
        }
    }



