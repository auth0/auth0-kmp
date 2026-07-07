package com.auth0.kmp.networking.retry

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackOffTest {

    /** Returns [fraction] from [nextDouble], recording the requested range. */
    private class FixedRandom(private val fraction: Double) : Random() {
        var lastFrom: Double? = null
            private set
        var lastUntil: Double? = null
            private set

        override fun nextBits(bitCount: Int): Int = 0

        override fun nextDouble(from: Double, until: Double): Double {
            lastFrom = from
            lastUntil = until
            return fraction
        }
    }

    @Test
    fun fixed_returnsSameDelay_regardlessOfAttempt() {
        val backoff = Backoff.Fixed(250.milliseconds)

        assertEquals(250.milliseconds, backoff.delayFor(1))
        assertEquals(250.milliseconds, backoff.delayFor(5))
    }

    @Test
    fun exponential_noJitter_growsGeometrically() {
        val backoff = Backoff.Exponential(
            base = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 10.seconds,
            jitter = false,
        )

        assertEquals(100.milliseconds, backoff.delayFor(1))
        assertEquals(200.milliseconds, backoff.delayFor(2))
        assertEquals(400.milliseconds, backoff.delayFor(3))
        assertEquals(800.milliseconds, backoff.delayFor(4))
    }

    @Test
    fun exponential_noJitter_isCappedAtMaxDelay() {
        val backoff = Backoff.Exponential(
            base = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 500.milliseconds,
            jitter = false,
        )

        assertEquals(400.milliseconds, backoff.delayFor(3))
        assertEquals(500.milliseconds, backoff.delayFor(4))
        assertEquals(500.milliseconds, backoff.delayFor(10))
    }

    @Test
    fun exponential_jitter_atLowerBound_halvesTheDelay() {
        val backoff = Backoff.Exponential(
            base = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 10.seconds,
            jitter = true,
        )

        assertEquals(100.milliseconds, backoff.delayFor(2, FixedRandom(0.5)))
    }

    @Test
    fun exponential_jitter_atUpperBound_keepsFullDelay() {
        val backoff = Backoff.Exponential(
            base = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 10.seconds,
            jitter = true,
        )

        assertEquals(200.milliseconds, backoff.delayFor(2, FixedRandom(1.0)))
    }

    @Test
    fun exponential_jitter_appliesAfterCap() {
        val backoff = Backoff.Exponential(
            base = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 500.milliseconds,
            jitter = true,
        )

        assertEquals(250.milliseconds, backoff.delayFor(10, FixedRandom(0.5)))
    }

    @Test
    fun exponential_jitter_drawsFromHalfToFullRange() {
        val backoff = Backoff.Exponential(
            base = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 10.seconds,
            jitter = true,
        )
        val random = FixedRandom(0.75)

        backoff.delayFor(1, random)

        assertEquals(0.5, random.lastFrom)
        assertEquals(1.0, random.lastUntil)
    }
}
