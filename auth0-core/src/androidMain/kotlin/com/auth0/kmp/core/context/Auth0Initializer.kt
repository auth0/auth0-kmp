package com.auth0.kmp.core.context

import android.content.Context
import androidx.startup.Initializer
import com.auth0.kmp.core.Auth0
import com.auth0.kmp.core.annotation.InternalAuth0Api

/**
 * Captures the application [Context] at app launch via `androidx.startup`, so
 * the SDK's Android features work without any consumer initialization code.
 */
@InternalAuth0Api
public class Auth0Initializer : Initializer<Auth0> {

    override fun create(context: Context): Auth0 {
        ApplicationContextHolder.set(context)
        return Auth0
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
