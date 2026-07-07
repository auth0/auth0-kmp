package com.auth0.kmp.webauth.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Exported, invisible Activity that catches the custom-scheme redirect from a
 * plain Custom Tab (the fallback used when the browser does not support Auth Tab)
 * and trampolines it back into the singleTask [WebAuthActivity].
 *
 * Declared in the SDK manifest with an `<intent-filter>` templated on the
 * consumer's `auth0Scheme` / `auth0Domain` manifest placeholders.
 */
internal class RedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val relay = Intent(this, WebAuthActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            data = intent?.data
        }
        startActivity(relay)
        finish()
    }
}
