package com.auth0.kmp.credentials

import com.auth0.kmp.core.Auth0Account
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(InternalAuth0Api::class)
private class FakeNetworkClient : NetworkClient {
    var closed = false
        private set

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> = error("not used")

    override fun close() {
        closed = true
    }
}

class CredentialsManagerFactoryTest {

    @OptIn(InternalAuth0Api::class)
    @Test
    fun borrowOverload_doesNotCloseInjectedNetwork() {
        val network = FakeNetworkClient()
        val manager = credentialsManager(
            account = Auth0Account(clientId = "cid", domain = "test.auth0.com"),
            networkClient = network,
            storeKey = "credentials_cid",
            storage = FakeStorage(),
        )
        manager.close()
        assertFalse(network.closed)
    }
}
