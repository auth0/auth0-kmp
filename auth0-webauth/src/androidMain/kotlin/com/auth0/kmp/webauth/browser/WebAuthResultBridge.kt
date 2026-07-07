package com.auth0.kmp.webauth.browser

import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.error.WebAuthError
import kotlinx.coroutines.CancellableContinuation
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Process-wide hand-off between the suspending [AndroidBrowserAgent.launch] call
 * and the [WebAuthActivity] that receives the browser's redirect result.
 *
 * The OS instantiates [WebAuthActivity] from an `Intent`, so it holds no
 * reference to the caller; this object bridges the two. It holds a single slot:
 * [register] accepts a continuation only while none is in flight, so one Web
 * Auth operation runs at a time across the process.
 */
internal object WebAuthResultBridge {

    private val pending =
        AtomicReference<CancellableContinuation<Result<String, WebAuthError>>?>(null)

    /**
     * Parks [continuation] to be resumed when the redirect result arrives.
     *
     * @return `true` if the slot was free and [continuation] was parked; `false`
     *   if an operation is already in flight.
     */
    fun register(continuation: CancellableContinuation<Result<String, WebAuthError>>): Boolean =
        pending.compareAndSet(null, continuation)

    /** Resumes the parked continuation with [result], if one is waiting. */
    fun resolve(result: Result<String, WebAuthError>) {
        pending.getAndSet(null)?.resume(result)
    }

    /** Drops the parked continuation without resuming it. */
    fun clear() {
        pending.set(null)
    }
}
