package com.auth0.kmp.core

import android.content.Context
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.context.ApplicationContextHolder

/**
 * Android initialization entry point for the Auth0 SDK.
 *
 * The SDK's Android features (browser-based Web Auth login) require an
 * application [Context]. It is captured automatically at
 * app launch through an `androidx.startup` initializer, so a typical app needs
 * no initialization code and can create clients such as `webAuthClient(account)`
 * directly.
 *
 * Call [init] only when that automatic capture cannot run:
 * - the SDK's `androidx.startup` initializer was removed from the merged
 *   manifest (`tools:node="remove"`),
 * - the SDK is used from a secondary process that the initializer did not run
 *   in, or
 * - a unit test needs to supply a context.
 *
 * When no context has been captured, the first Android feature that needs one
 * fails with an [IllegalStateException] instructing you to call [init].
 */
public object Auth0Android {

    /**
     * Supplies the SDK with an application [Context].
     *
     * Most apps never call this; see [Auth0Android] for the cases that require it. The
     * given context's application context is retained. Safe to call more than
     * once — the most recent call wins.
     *
     * @param context any [Context]; its application context is retained.
     */
    @OptIn(InternalAuth0Api::class)
    public fun init(context: Context) {
        ApplicationContextHolder.set(context)
    }
}
