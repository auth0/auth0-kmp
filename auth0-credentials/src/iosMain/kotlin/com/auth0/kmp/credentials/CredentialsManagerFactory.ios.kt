package com.auth0.kmp.credentials

internal actual fun createStorage(): Storage = KeychainStorage()
