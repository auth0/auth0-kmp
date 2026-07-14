package com.auth0.kmp.core.dpop

internal actual fun createDPoPKeyStore(keyTag: String): DPoPKeyStore = IosDPoPKeyStore(keyTag)
