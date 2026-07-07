package com.auth0.kmp.webauth.browser

import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.error.WebAuthError
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private typealias RedirectResult = Result<String, WebAuthError>

class WebAuthResultBridgeTest {

    @BeforeTest
    fun reset() {
        // WebAuthResultBridge is a process-wide singleton; clear any slot a
        // prior test parked so cases start from an empty state.
        WebAuthResultBridge.clear()
    }

    @Test
    fun resolve_resumesParkedContinuationWithResult() = runTest {
        val parked = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> { WebAuthResultBridge.register(it) }
        }

        WebAuthResultBridge.resolve(Result.Success("myapp://cb?code=abc"))

        assertEquals(Result.Success("myapp://cb?code=abc"), parked.await())
    }

    @Test
    fun resolve_withNothingParked_isNoOp() = runTest {
        // No register() beforehand; resolve must not throw.
        WebAuthResultBridge.resolve(Result.Success("myapp://cb"))
    }

    @Test
    fun resolve_afterClear_doesNotResume() = runTest {
        val parked = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> { WebAuthResultBridge.register(it) }
        }

        WebAuthResultBridge.clear()
        WebAuthResultBridge.resolve(Result.Success("myapp://cb"))

        assertFalse(parked.isCompleted, "cleared continuation must not be resumed")
        parked.cancel()
    }

    @Test
    fun secondResolve_isNoOp_firstValueWins() = runTest {
        val parked = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> { WebAuthResultBridge.register(it) }
        }

        WebAuthResultBridge.resolve(Result.Success("first"))
        // Second delivery lands on an emptied slot; must be dropped, not crash.
        WebAuthResultBridge.resolve(Result.Failure(WebAuthError.UserCancelled))

        assertEquals(Result.Success("first"), parked.await())
    }

    @Test
    fun bridge_isReusableAcrossSequentialLogins() = runTest {
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> { WebAuthResultBridge.register(it) }
        }
        WebAuthResultBridge.resolve(Result.Success("first"))
        assertEquals(Result.Success("first"), first.await())

        val second = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> { WebAuthResultBridge.register(it) }
        }
        WebAuthResultBridge.resolve(Result.Failure(WebAuthError.UserCancelled))
        assertEquals(Result.Failure(WebAuthError.UserCancelled), second.await())
    }

    @Test
    fun register_whileOccupied_returnsFalse_andIncumbentStillResolves() = runTest {
        var firstAccepted = false
        val incumbent = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> {
                firstAccepted = WebAuthResultBridge.register(it)
            }
        }
        assertTrue(firstAccepted, "first register should take the free slot")

        // A second caller (as a different WebAuthClient would produce) hits the
        // occupied slot: register must reject it and leave the incumbent in place.
        var secondAccepted = true
        val newcomer = async(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<RedirectResult> {
                secondAccepted = WebAuthResultBridge.register(it)
                if (!secondAccepted) it.resume(Result.Failure(WebAuthError.TransactionActiveAlready))
            }
        }
        assertFalse(secondAccepted, "register while occupied must return false")
        assertEquals(Result.Failure(WebAuthError.TransactionActiveAlready), newcomer.await())

        // A single resolve must reach the incumbent, proving the newcomer never
        // displaced it in the slot.
        WebAuthResultBridge.resolve(Result.Success("incumbent"))
        assertEquals(Result.Success("incumbent"), incumbent.await())
    }

    @Test
    fun resolveOnDifferentThread_deliversToContinuationParkedOnAnotherThread() {
        // Mirrors production: register runs on the launching coroutine, resolve
        // runs later on the Activity's (different) thread.
        val registered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val delivered = AtomicReference<RedirectResult?>(null)

        thread(name = "park-thread") {
            runBlocking {
                val result = suspendCancellableCoroutine<RedirectResult> { cont ->
                    WebAuthResultBridge.register(cont)
                    registered.countDown()
                }
                delivered.set(result)
                finished.countDown()
            }
        }
        assertTrue(registered.await(2, TimeUnit.SECONDS), "continuation should have parked")

        thread(name = "resolve-thread") {
            WebAuthResultBridge.resolve(Result.Success("cross-thread"))
        }
        assertTrue(finished.await(2, TimeUnit.SECONDS), "parked continuation must be resumed cross-thread")
        assertEquals(Result.Success("cross-thread"), delivered.get())
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun concurrentRegister_fromManyThreads_onlyOneWins() {
        val n = 8
        val pool = newFixedThreadPoolContext(n, "bridge-race")
        try {
            val barrier = CyclicBarrier(n)
            val outcomes = ConcurrentLinkedQueue<Boolean>()
            runBlocking {
                val jobs = (1..n).map {
                    async(pool) {
                        suspendCancellableCoroutine<RedirectResult> { cont ->
                            barrier.await() // converge so the compareAndSet truly races
                            val accepted = WebAuthResultBridge.register(cont)
                            outcomes.add(accepted)
                            if (!accepted) cont.resume(Result.Failure(WebAuthError.TransactionActiveAlready))
                        }
                    }
                }
                while (outcomes.size < n) { /* wait until every register has returned */ }
                WebAuthResultBridge.resolve(Result.Success("winner"))
                val results = jobs.awaitAll()

                assertEquals(1, outcomes.count { it }, "exactly one register may win the race")
                assertEquals(n - 1, outcomes.count { !it }, "all other registers must be rejected")
                assertEquals(1, results.count { it == Result.Success("winner") })
            }
        } finally {
            pool.close()
        }
    }
}
