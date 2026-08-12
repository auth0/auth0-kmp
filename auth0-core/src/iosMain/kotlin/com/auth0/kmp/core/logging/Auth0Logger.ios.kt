package com.auth0.kmp.core.logging

import com.auth0.kmp.core.annotation.InternalAuth0Api
import platform.Foundation.NSLog
import platform.Foundation.NSString

@InternalAuth0Api
public actual fun createLogger(): Auth0Logger = IosLogger

@OptIn(InternalAuth0Api::class)
private object IosLogger : Auth0Logger {
    override fun d(tag: String, message: String) {
        log("$tag: $message")
    }

    override fun e(tag: String, message: String, error: Throwable?) {
        val text = buildString {
            append(tag).append(": ").append(message)
            if (error != null) {
                append('\n').append(error.stackTraceToString())
            }
        }
        log(text)
    }

    override fun w(tag: String, message: String) {
        log("$tag: $message")
    }

    /**
     * Writes [text] to the system log as a single `%@` argument, so any `%`
     * sequences it contains are logged verbatim rather than interpreted as
     * format specifiers.
     */
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun log(text: String) {
        NSLog("%@", text as NSString)
    }
}
