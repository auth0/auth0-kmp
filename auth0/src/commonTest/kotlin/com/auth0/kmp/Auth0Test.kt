package com.auth0.kmp

import com.auth0.kmp.authentication.AuthenticationClient
import com.auth0.kmp.authentication.error.AuthenticationError
import com.auth0.kmp.authentication.model.DatabaseUser
import com.auth0.kmp.authentication.model.PasskeyLoginChallenge
import com.auth0.kmp.authentication.model.PasskeyRegistrationChallenge
import com.auth0.kmp.authentication.model.PublicKeyCredentials
import com.auth0.kmp.authentication.model.SignupProfile
import com.auth0.kmp.authentication.passwordless.PasswordlessClient
import com.auth0.kmp.core.RequestOptions
import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.credentials.CredentialsManager
import com.auth0.kmp.core.credentials.CredentialsManagerError
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.model.ApiCredentials
import com.auth0.kmp.core.model.Credentials
import com.auth0.kmp.core.model.SsoCredentials
import com.auth0.kmp.core.model.UserInfo
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.credentials.Storage
import com.auth0.kmp.myaccount.MyAccountClient
import com.auth0.kmp.myaccount.error.MyAccountError
import com.auth0.kmp.myaccount.model.EmailAuthenticationMethod
import com.auth0.kmp.myaccount.model.EmailEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PasskeyAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasskeyEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PasswordAuthenticationMethod
import com.auth0.kmp.myaccount.model.PasswordEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethod
import com.auth0.kmp.myaccount.model.PhoneAuthenticationMethodType
import com.auth0.kmp.myaccount.model.PhoneEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PublicKeyCredentials as MyAccountPublicKeyCredentials
import com.auth0.kmp.myaccount.model.PushAuthenticationMethod
import com.auth0.kmp.myaccount.model.PushEnrollmentChallenge
import com.auth0.kmp.myaccount.model.RecoveryCodeAuthenticationMethod
import com.auth0.kmp.myaccount.model.RecoveryCodeEnrollmentChallenge
import com.auth0.kmp.myaccount.model.TotpAuthenticationMethod
import com.auth0.kmp.myaccount.model.TotpEnrollmentChallenge
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

    override suspend fun <T> request(
        request: NetworkRequest,
        retryPolicy: RetryPolicy,
        deserialize: (body: String, headers: Map<String, List<String>>) -> T,
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
    ): Result<UserInfo, AuthenticationError> = error("not used")

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

    override suspend fun ssoExchange(
        refreshToken: String,
        options: RequestOptions,
    ): Result<SsoCredentials, AuthenticationError> = error("not used")

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

    override fun passwordlessClient(): PasswordlessClient = error("not used")
}

private class FakeCredentialsManager : CredentialsManager {
    override suspend fun saveCredentials(
        credentials: Credentials,
    ): Result<Unit, CredentialsManagerError> = error("not used")

    override suspend fun clearCredentials(): Result<Unit, CredentialsManagerError> =
        error("not used")

    override suspend fun hasValidCredentials(minTtl: Int): Boolean = error("not used")

    override suspend fun getCredentials(
        scope: String?,
        minTtl: Int,
        parameters: Map<String, String>,
        headers: Map<String, String>,
        forceRefresh: Boolean,
    ): Result<Credentials, CredentialsManagerError> = error("not used")

    override suspend fun getSsoCredentials(
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Result<SsoCredentials, CredentialsManagerError> = error("not used")

    override suspend fun getApiCredentials(
        audience: String,
        scope: String?,
        minTtl: Int,
        parameters: Map<String, String>,
        headers: Map<String, String>,
        forceRefresh: Boolean,
    ): Result<ApiCredentials, CredentialsManagerError> = error("not used")

    override suspend fun clearApiCredentials(
        audience: String,
        scope: String?,
    ): Result<Unit, CredentialsManagerError> = error("not used")

    override suspend fun hasValidApiCredentials(
        audience: String,
        scope: String?,
        minTtl: Int,
    ): Boolean = error("not used")
}

private class FakeMyAccountClient : MyAccountClient {
    override suspend fun passkeyEnrollmentChallenge(
        userIdentityId: String?,
        connection: String?,
        options: RequestOptions,
    ): Result<PasskeyEnrollmentChallenge, MyAccountError> = error("not used")

    override suspend fun verifyPasskeyEnrollment(
        credential: MyAccountPublicKeyCredentials,
        challenge: PasskeyEnrollmentChallenge,
        options: RequestOptions,
    ): Result<PasskeyAuthenticationMethod, MyAccountError> = error("not used")

    override suspend fun totpEnrollmentChallenge(
        options: RequestOptions,
    ): Result<TotpEnrollmentChallenge, MyAccountError> = error("not used")

    override suspend fun verifyTotpEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions,
    ): Result<TotpAuthenticationMethod, MyAccountError> = error("not used")

    override suspend fun pushNotificationEnrollmentChallenge(
        options: RequestOptions,
    ): Result<PushEnrollmentChallenge, MyAccountError> = error("not used")

    override suspend fun verifyPushNotificationEnrollment(
        authenticationMethodId: String,
        authSession: String,
        options: RequestOptions,
    ): Result<PushAuthenticationMethod, MyAccountError> = error("not used")

    override suspend fun emailEnrollmentChallenge(
        email: String,
        options: RequestOptions,
    ): Result<EmailEnrollmentChallenge, MyAccountError> = error("not used")

    override suspend fun verifyEmailEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions,
    ): Result<EmailAuthenticationMethod, MyAccountError> = error("not used")

    override suspend fun phoneEnrollmentChallenge(
        phoneNumber: String,
        preferredAuthenticationMethod: PhoneAuthenticationMethodType?,
        options: RequestOptions,
    ): Result<PhoneEnrollmentChallenge, MyAccountError> = error("not used")

    override suspend fun verifyPhoneEnrollment(
        authenticationMethodId: String,
        authSession: String,
        otpCode: String,
        options: RequestOptions,
    ): Result<PhoneAuthenticationMethod, MyAccountError> = error("not used")

    override suspend fun recoveryCodeEnrollmentChallenge(
        options: RequestOptions,
    ): Result<RecoveryCodeEnrollmentChallenge, MyAccountError> = error("not used")

    override suspend fun verifyRecoveryCodeEnrollment(
        authenticationMethodId: String,
        authSession: String,
        options: RequestOptions,
    ): Result<RecoveryCodeAuthenticationMethod, MyAccountError> = error("not used")

    override suspend fun passwordEnrollmentChallenge(
        userIdentityId: String?,
        connection: String?,
        options: RequestOptions,
    ): Result<PasswordEnrollmentChallenge, MyAccountError> = error("not used")

    override suspend fun verifyPasswordEnrollment(
        authenticationMethodId: String,
        authSession: String,
        newPassword: String,
        options: RequestOptions,
    ): Result<PasswordAuthenticationMethod, MyAccountError> = error("not used")
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
    var myAccountCount = 0
        private set
    var webAuthNetwork: NetworkClient? = null
        private set
    var authNetwork: NetworkClient? = null
        private set
    var credentialsNetwork: NetworkClient? = null
        private set
    var myAccountNetwork: NetworkClient? = null
        private set
    var lastStoreKey: String? = null
        private set
    var lastStorage: Storage? = null
        private set
    var lastAccessToken: String? = null
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
    val myAccount: (NetworkClient, String) -> MyAccountClient = { network, token ->
        myAccountCount++
        myAccountNetwork = network
        lastAccessToken = token
        FakeMyAccountClient()
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
    buildMyAccount = builders.myAccount,
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
        assertEquals(0, builders.myAccountCount)

        sdk.webAuth

        assertEquals(1, builders.webAuthCount)
        assertEquals(0, builders.authCount)
        assertEquals(0, builders.credentialsCount)
        assertEquals(0, builders.myAccountCount)
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
        sdk.myAccount("token")

        assertSame(network, builders.webAuthNetwork)
        assertSame(network, builders.authNetwork)
        assertSame(network, builders.credentialsNetwork)
        assertSame(network, builders.myAccountNetwork)
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
    fun myAccount_buildsFreshEachCall_passingAccessToken() {
        val builders = Builders()
        val sdk = auth(builders = builders)

        sdk.myAccount("token-a")
        sdk.myAccount("token-b")

        assertEquals(2, builders.myAccountCount)
        assertEquals("token-b", builders.lastAccessToken)
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
        assertEquals(0, builders.myAccountCount)
        assertEquals(1, network.closeCount)
    }
}
