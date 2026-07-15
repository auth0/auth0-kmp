package com.auth0.kmp.core.context

import android.content.Context
import androidx.startup.Initializer
import com.auth0.kmp.core.Auth0Android
import com.auth0.kmp.core.annotation.InternalAuth0Api

/**
 * Captures the application [Context] at app launch via `androidx.startup`, so
 * the SDK's Android features work without any consumer initialization code.
 */
@InternalAuth0Api
public class Auth0Initializer : Initializer<Auth0Android> {

    override fun create(context: Context): Auth0Android {
        ApplicationContextHolder.set(context)
        return Auth0Android
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
