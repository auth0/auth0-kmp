package com.auth0.kmp.core.useragent

import android.os.Build

internal actual fun platformEnv(): Map<String, String> =
    mapOf("android" to Build.VERSION.SDK_INT.toString())
