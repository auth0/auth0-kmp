package com.auth0.kmp.core.context

import android.content.Context
import com.auth0.kmp.core.annotation.InternalAuth0Api

/**
 * Process-wide store for the application [Context] the SDK's Android features
 * rely on. Populated automatically at app launch, or explicitly via
 * [com.auth0.kmp.core.Auth0Android.init].
 */
@InternalAuth0Api
public object ApplicationContextHolder {

    private var applicationContext: Context? = null

    /**
     * The stored application [Context].
     *
     * @throws IllegalStateException if no context has been captured yet.
     */
    public val context: Context
        get() = applicationContext ?: error(
            "Auth0 SDK could not obtain an Android Context. This normally " +
                "happens automatically at app launch. If the automatic " +
                "initializer was removed call Auth0Android.init(context) first."
        )

    /** Stores the application context derived from [context]. */
    public fun set(context: Context) {
        applicationContext = context.applicationContext
    }
}
