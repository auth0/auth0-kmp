package com.auth0.kmp.core.logging

import android.util.Log
import com.auth0.kmp.core.annotation.InternalAuth0Api

@InternalAuth0Api
public actual fun createLogger(): Auth0Logger = AndroidLogger

@OptIn(InternalAuth0Api::class)
private object AndroidLogger : Auth0Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun e(tag: String, message: String, error: Throwable?) {
        Log.e(tag, message, error)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
}
