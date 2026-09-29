package com.auth0.kmp.authentication.model

/**
 * How the one-time code or link is delivered when starting a passwordless flow.
 *
 * The [value] is sent as the `send` parameter to `/passwordless/start`.
 */
public enum class PasswordlessType(public val value: String) {

    /** Deliver a one-time code the user types back in to complete login. */
    CODE("code"),

    /** Deliver a magic link that completes login when opened in a browser. */
    LINK("link"),

    /** Deliver a magic link that opens the associated Android app. */
    LINK_ANDROID("link_android"),

    /** Deliver a magic link that opens the associated iOS app. */
    LINK_IOS("link_ios"),
}
