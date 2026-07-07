package com.auth0.kmp.core.useragent

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformEnv(): Map<String, String> =
    NSProcessInfo.processInfo.operatingSystemVersion().useContents {
        mapOf("ios" to "$majorVersion.$minorVersion")
    }
