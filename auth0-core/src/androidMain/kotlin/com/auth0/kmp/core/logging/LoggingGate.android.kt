package com.auth0.kmp.core.logging

import android.content.pm.ApplicationInfo
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.context.ApplicationContextHolder

@OptIn(InternalAuth0Api::class)
internal actual fun isDebugBuild(): Boolean =
    runCatching {
        val ctx = ApplicationContextHolder.context
        (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(false)
