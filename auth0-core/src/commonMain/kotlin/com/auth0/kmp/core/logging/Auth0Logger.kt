package com.auth0.kmp.core.logging

import com.auth0.kmp.core.annotation.InternalAuth0Api

/**
 * Platform logger used internally by the SDK to log diagnostic messages.
 *
 * Each message is associated with a [tag] identifying its source and a
 * human-readable [message], routed to the platform's native logging facility.
 */
@InternalAuth0Api
public interface Auth0Logger {

    /**
     * Logs a debug-level [message] under the given [tag].
     */
    public fun d(tag: String, message: String)

    /**
     * Logs an error-level [message] under the given [tag], optionally including
     * the [error] that caused it.
     */
    public fun e(tag: String, message: String, error: Throwable? = null)

    /**
     * Logs a warning-level [message] under the given [tag].
     */
    public fun w(tag: String, message: String)
}

/**
 * Returns the [Auth0Logger] backed by the current platform's logging facility.
 */
@InternalAuth0Api
public expect fun createLogger(): Auth0Logger

/**
 * Static entry point for internal SDK logging. Delegates to the platform
 * [Auth0Logger].
 */
@InternalAuth0Api
public object Auth0Log {

    @OptIn(InternalAuth0Api::class)
    private val delegate: Auth0Logger = createLogger()

    /**
     * Logs a debug-level [message] under the given [tag].
     */
    @OptIn(InternalAuth0Api::class)
    public fun d(tag: String, message: String) {
        if (isDebugBuild()) delegate.d(tag, message)
    }

    /**
     * Logs an error-level [message] under the given [tag], optionally including
     * the [error] that caused it.
     */
    @OptIn(InternalAuth0Api::class)
    public fun e(tag: String, message: String, error: Throwable? = null): Unit =
        delegate.e(tag, message, error)

    /**
     * Logs a warning-level [message] under the given [tag].
     */
    @OptIn(InternalAuth0Api::class)
    public fun w(tag: String, message: String): Unit = delegate.w(tag, message)
}
