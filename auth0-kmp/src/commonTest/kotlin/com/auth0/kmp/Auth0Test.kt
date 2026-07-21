package com.auth0.kmp

import com.auth0.kmp.authentication.AuthenticationClient
import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.model.DatabaseUser
import com.auth0.kmp.authentication.model.PasskeyLoginChallenge
import com.auth0.kmp.authentication.model.PasskeyRegistrationChallenge
import com.auth0.kmp.authentication.model.PublicKeyCredentials
import com.auth0.kmp.authentication.model.SignupProfile
import com.auth0.kmp.authentication.model.UserProfile
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.credentials.Storage
import com.auth0.kmp.networking.NetworkClient
import com.auth0.kmp.networking.request.NetworkRequest
import com.auth0.kmp.networking.retry.RetryPolicy
import com.auth0.kmp.webauth.LoginOptions
import com.auth0.kmp.webauth.LogoutOptions
import com.auth0.kmp.webauth.WebAuthClient
import com.auth0.kmp.webauth.error.WebAuthError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(InternalAuth0Api::class)
private class FakeNetworkClient : NetworkClient {
    var closeCount = 0
        private set

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (String) -> T,
    ): Result<T, TransportError> = error("not used")

    override fun close() {
        closeCount++
    }
}

private class FakeWebAuthClient : WebAuthClient {
    override suspend fun login(options: LoginOptions): Result<Credentials, WebAuthError> =
        error("not used")

    override suspend fun logout(options: LogoutOptions): Result<Unit, WebAuthError> =
        error("not used")

    override fun cancel() = error("not used")
}

private class FakeAuthenticationClient : AuthenticationClient {
    override suspend fun login(
        usernameOrEmail: String,
        password: String,
        realm: String,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> = error("not used")

    override suspend fun createUser(
        profile: SignupProfile,
        password: String,
        connection: String,
        userMetadata: Map<String, String>,
        options: RequestOptions,
    ): Result<DatabaseUser, AuthenticationError> = error("not used")

    override suspend fun resetPassword(
        email: String,
        connection: String,
        options: RequestOptions,
    ): Result<Unit, AuthenticationError> = error("not used")

    override suspend fun userInfo(
        accessToken: String,
        tokenType: String,
        options: RequestOptions,
    ): Result<UserProfile, AuthenticationError> = error("not used")

    override suspend fun revoke(
        refreshToken: String,
        options: RequestOptions,
    ): Result<Unit, AuthenticationError> = error("not used")

    override suspend fun renew(
        refreshToken: String,
        audience: String?,
        scope: String?,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> = error("not used")

    override suspend fun passkeyLoginChallenge(
        realm: String?,
        organization: String?,
        options: RequestOptions,
    ): Result<PasskeyLoginChallenge, AuthenticationError> = error("not used")

    override suspend fun passkeySignupChallenge(
        profile: SignupProfile,
        userMetadata: Map<String, String>,
        realm: String?,
        organization: String?,
        options: RequestOptions,
    ): Result<PasskeyRegistrationChallenge, AuthenticationError> = error("not used")

    override suspend fun loginWithPasskey(
        authSession: String,
        authResponse: PublicKeyCredentials,
        realm: String?,
        organization: String?,
        audience: String?,
        scope: String,
        options: RequestOptions,
    ): Result<Credentials, AuthenticationError> = error("not used")
}

private class FakeCredentialsManager : CredentialsManager {
    override suspend fun saveCredentials(
        credentials: Credentials,
    ): Result<Unit, CredentialsManagerError> = error("not used")

    override suspend fun clearCredentials(): Result<Unit, CredentialsManagerError> =
        error("not used")

    override suspend fun hasValidCredentials(minTtl: Long): Boolean = error("not used")

    override suspend fun getCredentials(
        scope: String?,
        minTtl: Int,
        parameters: Map<String, String>,
        headers: Map<String, String>,
        forceRefresh: Boolean,
    ): Result<Credentials, CredentialsManagerError> = error("not used")
}

/** Records which network client each builder saw and how many times it ran. */
@OptIn(InternalAuth0Api::class)
private class Builders {
    var webAuthCount = 0
        private set
    var authCount = 0
        private set
    var credentialsCount = 0
        private set
    var webAuthNetwork: NetworkClient? = null
        private set
    var authNetwork: NetworkClient? = null
        private set
    var credentialsNetwork: NetworkClient? = null
        private set
    var lastStoreKey: String? = null
        private set
    var lastStorage: Storage? = null
        private set

    val webAuth: (NetworkClient) -> WebAuthClient = { network ->
        webAuthCount++
        webAuthNetwork = network
        FakeWebAuthClient()
    }
    val authentication: (NetworkClient) -> AuthenticationClient = { network ->
        authCount++
        authNetwork = network
        FakeAuthenticationClient()
    }
    val credentials: (NetworkClient, String, Storage?) -> CredentialsManager =
        { network, storeKey, storage ->
            credentialsCount++
            credentialsNetwork = network
            lastStoreKey = storeKey
            lastStorage = storage
            FakeCredentialsManager()
        }
}

@OptIn(InternalAuth0Api::class)
private fun auth(
    network: FakeNetworkClient = FakeNetworkClient(),
    builders: Builders = Builders(),
    defaultStoreKey: String = "credentials_cid",
): Auth0 = Auth0(
    networkClient = network,
    defaultStoreKey = defaultStoreKey,
    buildWebAuth = builders.webAuth,
    buildAuthentication = builders.authentication,
    buildCredentials = builders.credentials,
)

@OptIn(InternalAuth0Api::class)
class Auth0Test {

    @Test
    fun clientsAreLazy_untouchedNeverBuilt() {
        val builders = Builders()
        val sdk = auth(builders = builders)

        assertEquals(0, builders.webAuthCount)
        assertEquals(0, builders.authCount)
        assertEquals(0, builders.credentialsCount)

        sdk.webAuth

        assertEquals(1, builders.webAuthCount)
        assertEquals(0, builders.authCount)
        assertEquals(0, builders.credentialsCount)
    }

    @Test
    fun webAuth_sameInstanceOnRepeatedAccess() {
        val builders = Builders()
        val sdk = auth(builders = builders)

        assertSame(sdk.webAuth, sdk.webAuth)
        assertEquals(1, builders.webAuthCount)
    }

    @Test
    fun authentication_sameInstanceOnRepeatedAccess() {
        val builders = Builders()
        val sdk = auth(builders = builders)

        assertSame(sdk.authentication, sdk.authentication)
        assertEquals(1, builders.authCount)
    }

    @Test
    fun allClientsShareTheOneTransport() {
        val network = FakeNetworkClient()
        val builders = Builders()
        val sdk = auth(network = network, builders = builders)

        sdk.webAuth
        sdk.authentication
        sdk.credentials()

        assertSame(network, builders.webAuthNetwork)
        assertSame(network, builders.authNetwork)
        assertSame(network, builders.credentialsNetwork)
    }

    @Test
    fun credentials_defaultsStoreKeyAndNullStorage() {
        val builders = Builders()
        val sdk = auth(builders = builders, defaultStoreKey = "credentials_cid")

        sdk.credentials()

        assertEquals("credentials_cid", builders.lastStoreKey)
        assertEquals(null, builders.lastStorage)
    }

    @Test
    fun credentials_buildsFreshEachCall() {
        val builders = Builders()
        val sdk = auth(builders = builders)

        sdk.credentials()
        sdk.credentials()

        assertEquals(2, builders.credentialsCount)
    }

    @Test
    fun close_closesSharedTransportOnce() {
        val network = FakeNetworkClient()
        val sdk = auth(network = network)

        sdk.close()

        assertEquals(1, network.closeCount)
    }

    @Test
    fun close_doesNotForceInitLazyClients() {
        val network = FakeNetworkClient()
        val builders = Builders()
        val sdk = auth(network = network, builders = builders)

        sdk.close()

        assertEquals(0, builders.webAuthCount)
        assertEquals(0, builders.authCount)
        assertEquals(0, builders.credentialsCount)
        assertEquals(1, network.closeCount)
    }
}
