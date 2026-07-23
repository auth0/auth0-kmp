package com.auth0.kmp.core.logging

import com.auth0.kmp.core.annotation.InternalAuth0Api
import platform.Foundation.NSLog

@InternalAuth0Api
public actual fun createLogger(): Auth0Logger = IosLogger

@OptIn(InternalAuth0Api::class)
private object IosLogger : Auth0Logger {
    override fun d(tag: String, message: String) {
        NSLog("%@", "$tag: $message")
    }

    override fun e(tag: String, message: String, error: Throwable?) {
        val text = buildString {
            append(tag).append(": ").append(message)
            if (error != null) {
                append('\n').append(error.stackTraceToString())
            }
        }
        NSLog("%@", text)
    }

    override fun w(tag: String, message: String) {
        NSLog("%@", "$tag: $message")
    }
}
