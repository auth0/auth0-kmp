# Examples

Practical examples for the Auth0 Kotlin Multiplatform SDK. For installation and
first-run setup, start with the [README](./README.md).

Every example is written in **common Kotlin** and runs unchanged on Android and
iOS. Where a platform genuinely differs, it is called out inline. Swift
equivalents are in collapsible sections under the examples that need them.

Each example assumes an `auth0` entry point built from your tenant credentials, as
described in [Configure the SDK](./README.md#configure-the-sdk):

```kotlin
val account = Auth0Account(clientId = "YOUR_CLIENT_ID", domain = "YOUR_DOMAIN")
val auth0 = Auth0(account)
```

- [Web Auth](#web-auth-android--ios)
  - [Log in](#log-in)
  - [Log out](#log-out)
  - [Specify an audience](#specify-an-audience)
  - [Specify a scope](#specify-a-scope)
  - [Log in to an organization](#log-in-to-an-organization)
  - [Log in with a specific connection](#log-in-with-a-specific-connection)
  - [Ephemeral sessions](#ephemeral-sessions)
  - [Custom scheme and redirect URI](#custom-scheme-and-redirect-uri)
  - [Extra authorization parameters](#extra-authorization-parameters)
  - [Cancelling a login](#cancelling-a-login)
  - [Web Auth errors](#web-auth-errors)
- [Authentication API](#authentication-api-android--ios)
  - [Log in with a database connection](#log-in-with-a-database-connection)
  - [Sign up with a database connection](#sign-up-with-a-database-connection)
  - [Reset a password](#reset-a-password)
  - [Retrieve user information](#retrieve-user-information)
  - [Renew credentials](#renew-credentials)
  - [Revoke a refresh token](#revoke-a-refresh-token)
  - [Passkeys](#passkeys)
  - [Authentication API errors](#authentication-api-errors)
- [Credentials Manager](#credentials-manager-android--ios)
  - [Store credentials](#store-credentials)
  - [Retrieve credentials](#retrieve-credentials)
  - [Check for stored credentials](#check-for-stored-credentials)
  - [Force a renewal](#force-a-renewal)
  - [Clear credentials](#clear-credentials)
  - [Multiple credential stores](#multiple-credential-stores)
  - [Custom storage](#custom-storage)
  - [Credentials Manager errors](#credentials-manager-errors)
- [DPoP](#dpop-android--ios)
- [Networking](#networking-android--ios)
  - [Timeouts](#timeouts)
  - [Logging](#logging)
  - [Default headers](#default-headers)
  - [Per-request options](#per-request-options)
  - [Retries](#retries)
- [Handling results](#handling-results)
- [Telemetry](#telemetry)
- [Resource management](#resource-management)


## Web Auth (Android / iOS)

Browser-based [Universal Login](https://auth0.com/docs/authenticate/login/auth0-universal-login).
On Android this uses Custom Tabs; on iOS, `ASWebAuthenticationSession`.

### Log in

```kotlin
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.webauth.LoginOptions

when (val result = auth0.webAuth.login()) {
    is Result.Success -> {
        val credentials = result.data
        println(credentials.accessToken)
    }
    is Result.Failure -> {
        // result.error is a WebAuthError
    }
}
```

The default scope is `openid profile email offline_access`. `offline_access` is
what makes Auth0 return a refresh token, which the
[Credentials Manager](#credentials-manager-android--ios) needs in order to renew
expired credentials.

<details>
  <summary>Swift</summary>

Kotlin default arguments do not cross into Swift, so every `LoginOptions`
parameter must be supplied explicitly. `Result` is bridged by SKIE to a Swift
enum you can switch over with `onEnum(of:)`.

```swift
let result = try await auth0.webAuth.login(
    options: LoginOptions(
        scope: "openid profile email offline_access",
        audience: nil,
        connection: nil,
        organization: nil,
        prompt: nil,
        maxAge: nil,
        redirectUri: nil,
        scheme: nil,
        ephemeral: false,
        extraParameters: [:]
    )
)

switch onEnum(of: result) {
case .success(let success):
    if let credentials = success.data as? Credentials {
        print(credentials.accessToken)
    }
case .failure(let failure):
    if let error = failure.error as? WebAuthError {
        // handle
    }
}
```
</details>

### Log out

Clears the user's session in the browser. This requires a browser round-trip, so
it applies to sessions established through Web Auth.

```kotlin
import com.auth0.kmp.webauth.LogoutOptions

auth0.webAuth.logout()
```

To also log the user out of their federated identity provider:

```kotlin
auth0.webAuth.logout(LogoutOptions(federated = true))
```

> [!NOTE]
> Logging out of the browser session does not remove locally stored tokens. Call
> [`clearCredentials()`](#clear-credentials) as well.

### Specify an audience

Request an access token for a specific API.

```kotlin
auth0.webAuth.login(
    LoginOptions(audience = "https://api.example.com"),
)
```

### Specify a scope

```kotlin
auth0.webAuth.login(
    LoginOptions(scope = "openid profile email offline_access read:reports"),
)
```

> [!IMPORTANT]
> Setting `scope` replaces the default. Include `openid` for OIDC and
> `offline_access` if you need a refresh token.

### Log in to an organization

```kotlin
auth0.webAuth.login(
    LoginOptions(organization = "org_abc123"),
)
```

### Log in with a specific connection

Skips the login page's connection selector and goes straight to one connection.

```kotlin
auth0.webAuth.login(
    LoginOptions(connection = "google-oauth2"),
)
```

### Ephemeral sessions

Starts a session that leaves no cookies behind, so the next login always prompts.

```kotlin
auth0.webAuth.login(
    LoginOptions(ephemeral = true),
)
```

> [!NOTE]
> On iOS this sets `prefersEphemeralWebBrowserSession` on
> `ASWebAuthenticationSession`. On Android, no shared cookie jar is used for the
> transaction.

### Custom scheme and redirect URI

By default the SDK derives the callback URL from your platform configuration. To
override it:

```kotlin
auth0.webAuth.login(
    LoginOptions(scheme = "myapp"),
)
```

Or supply the whole redirect URI:

```kotlin
auth0.webAuth.login(
    LoginOptions(redirectUri = "myapp://YOUR_DOMAIN/android/com.example.app/callback"),
)
```

> [!IMPORTANT]
> Any scheme or redirect URI you use here must also be registered in your Auth0
> application's **Allowed Callback URLs**, and — on Android — must match the
> `auth0Scheme` manifest placeholder. See the README's platform setup sections.

### Extra authorization parameters

Appends arbitrary query parameters to the `/authorize` request.

```kotlin
auth0.webAuth.login(
    LoginOptions(
        extraParameters = mapOf(
            "screen_hint" to "signup",
            "login_hint" to "user@example.com",
        ),
    ),
)
```

> [!NOTE]
> `extraParameters` cannot override parameters the SDK sets itself (such as
> `state`, `nonce`, `code_challenge`, `client_id`, or `redirect_uri`). SDK-owned
> values always win, so a login transaction stays secure.

### Cancelling a login

```kotlin
auth0.webAuth.cancel()
```

The in-flight `login()` call completes with `WebAuthError.UserCancelled`.

### Web Auth errors

`login()` and `logout()` fail with a `WebAuthError`:

```kotlin
import com.auth0.kmp.webauth.error.WebAuthError

when (val result = auth0.webAuth.login()) {
    is Result.Success -> handle(result.data)
    is Result.Failure -> when (val error = result.error) {
        WebAuthError.UserCancelled -> { /* user dismissed the browser */ }
        WebAuthError.TransactionActiveAlready -> { /* a login is already running */ }
        WebAuthError.InvalidState -> { /* state mismatch — possible CSRF */ }
        is WebAuthError.BrowserError -> log(error.message)
        is WebAuthError.AuthorizationError -> log(error.code, error.errorDescription)
        is WebAuthError.ApiError -> log(error.code, error.statusCode)
        is WebAuthError.Network -> retryLater(error.cause)
        is WebAuthError.IdTokenValidation -> log(error.cause)
        is WebAuthError.DPoP -> log(error.cause)
        is WebAuthError.Unknown -> log(error.cause)
    }
}
```

## Authentication API (Android / iOS)

Direct calls against the [Authentication API](https://auth0.com/docs/api/authentication),
without a browser. Reach it through `auth0.authentication`.

> [!IMPORTANT]
> Password-based grants require your Auth0 application to have the **Password**
> or **Password Realm** grant type enabled, and are not available to
> [Native applications](https://auth0.com/docs/get-started/applications) by
> default. Web Auth is the recommended flow for mobile apps.

### Log in with a database connection

```kotlin
val result = auth0.authentication.login(
    usernameOrEmail = "user@example.com",
    password = "a-secret-password",
    realm = "Username-Password-Authentication",
    scope = "openid profile email offline_access",
)
```

`login()` also accepts `audience` and a `RequestOptions`.

### Sign up with a database connection

Creates the user and returns a `DatabaseUser` — not credentials. Log the user in
afterwards to obtain tokens.

```kotlin
import com.auth0.kmp.authentication.model.SignupProfile

val result = auth0.authentication.createUser(
    profile = SignupProfile(
        email = "user@example.com",
        name = "Jane Doe",
    ),
    password = "a-secret-password",
    connection = "Username-Password-Authentication",
    userMetadata = mapOf("plan" to "free"),
)
```

`SignupProfile` also accepts `phoneNumber`, `username`, `givenName`,
`familyName`, `nickname`, and `picture`.

### Reset a password

Sends a password-reset email. Succeeds with `Unit`.

```kotlin
auth0.authentication.resetPassword(
    email = "user@example.com",
    connection = "Username-Password-Authentication",
)
```

### Retrieve user information

Calls `/userinfo` with an access token and returns the OIDC profile.

```kotlin
when (val result = auth0.authentication.userInfo(credentials.accessToken)) {
    is Result.Success -> {
        val user = result.data
        println("${user.sub} ${user.email}")
        val plan = user.customClaims["plan"]
    }
    is Result.Failure -> { /* handle */ }
}
```

Non-standard claims are collected in `customClaims`, keyed by claim name.

### Renew credentials

Exchanges a refresh token for fresh credentials.

```kotlin
val result = auth0.authentication.renew(
    refreshToken = credentials.refreshToken!!,
)
```

Pass `audience` or `scope` to narrow the new token. Requesting a scope not
granted originally will fail.

> [!TIP]
> If you use the [Credentials Manager](#credentials-manager-android--ios), you do
> not need to call this — `getCredentials()` renews expired credentials for you.

### Revoke a refresh token

Invalidates a refresh token server-side. Call this on logout.

```kotlin
auth0.authentication.revoke(refreshToken = credentials.refreshToken!!)
```

### Passkeys

Passkey support is split so your app can run the platform WebAuthn ceremony: the
SDK fetches a challenge, your app signs it, then the SDK exchanges the result for
credentials.

> [!NOTE]
> Passkeys must be enabled on your Auth0 tenant. On Android this requires
> minSdk 28 and the AndroidX Credential Manager; on iOS, the
> `AuthenticationServices` framework.

**Signing up with a passkey**

```kotlin
// 1. Ask Auth0 for a registration challenge.
val challenge = auth0.authentication.passkeySignupChallenge(
    profile = SignupProfile(email = "user@example.com", name = "Jane Doe"),
    realm = "Username-Password-Authentication",
)

// 2. Run the platform passkey-creation ceremony with challenge.authParamsPublicKey,
//    then exchange the resulting credential.
val result = auth0.authentication.loginWithPasskey(
    authSession = challenge.authSession,
    authResponse = publicKeyCredentials, // from the platform ceremony
    realm = "Username-Password-Authentication",
)
```

**Logging in with a passkey**

```kotlin
val challenge = auth0.authentication.passkeyLoginChallenge(
    realm = "Username-Password-Authentication",
)

val result = auth0.authentication.loginWithPasskey(
    authSession = challenge.authSession,
    authResponse = publicKeyCredentials,
    realm = "Username-Password-Authentication",
)
```

Both challenge calls accept an `organization`. See the sample apps for complete,
platform-specific ceremony code.

### Authentication API errors

```kotlin
import com.auth0.kmp.authentication.error.AuthenticationError

when (val result = auth0.authentication.login(/* ... */)) {
    is Result.Success -> handle(result.data)
    is Result.Failure -> when (val error = result.error) {
        is AuthenticationError.ApiError ->
            log(error.code, error.errorDescription, error.statusCode)
        is AuthenticationError.InvalidInput -> log(error.message)
        is AuthenticationError.Network -> retryLater(error.cause)
        is AuthenticationError.IdTokenValidation -> log(error.cause)
        is AuthenticationError.Unknown -> log(error.cause)
    }
}
```

`ApiError.code` carries Auth0's error code — for example `invalid_grant` for bad
credentials, or `too_many_attempts` when brute-force protection triggers.

## Credentials Manager (Android / iOS)

Persists credentials in platform-native secure storage — Android Keystore-backed
DataStore, iOS Keychain — and renews expired access tokens automatically.

```kotlin
val credentialsManager = auth0.credentials()
```

### Store credentials

```kotlin
when (val result = auth0.webAuth.login()) {
    is Result.Success -> credentialsManager.saveCredentials(result.data)
    is Result.Failure -> { /* handle */ }
}
```

### Retrieve credentials

Returns valid credentials, renewing them with the refresh token if the access
token has expired.

```kotlin
when (val result = credentialsManager.getCredentials()) {
    is Result.Success -> callApi(result.data.accessToken)
    is Result.Failure -> { /* result.error is a CredentialsManagerError */ }
}
```

Renewed credentials are saved back to storage automatically.

To require a minimum remaining lifetime, or to request a narrower token:

```kotlin
credentialsManager.getCredentials(
    scope = "read:reports",
    minTtl = 60, // seconds the access token must still be valid for
)
```

If `minTtl` cannot be satisfied even after renewal, the call fails with
`CredentialsManagerError.LargeMinTtl`.

### Check for stored credentials

Returns whether valid credentials exist, without renewing anything. Useful for
deciding which screen to show at launch.

```kotlin
if (credentialsManager.hasValidCredentials()) {
    showHome()
} else {
    showLogin()
}
```

### Force a renewal

```kotlin
credentialsManager.getCredentials(forceRefresh = true)
```

### Clear credentials

```kotlin
credentialsManager.clearCredentials()
```

> [!NOTE]
> This only removes the local copy. To end the browser session as well, call
> [`logout()`](#log-out); to invalidate the refresh token server-side, call
> [`revoke()`](#revoke-a-refresh-token).

### Multiple credential stores

Each store is namespaced by a key, so one app can hold several independent
sessions.

```kotlin
val work = auth0.credentials("credentials_work")
val personal = auth0.credentials("credentials_personal")
```

### Custom storage

Supply your own persistence by implementing `Storage`:

```kotlin
import com.auth0.kmp.credentials.Storage

class InMemoryStorage : Storage {
    private val entries = mutableMapOf<String, String>()

    override suspend fun retrieve(key: String): String? = entries[key]
    override suspend fun store(key: String, value: String) { entries[key] = value }
    override suspend fun remove(key: String) { entries.remove(key) }
}

val credentialsManager = auth0.credentials("credentials_custom", InMemoryStorage())
```

> [!WARNING]
> The default implementations encrypt credentials at rest. A custom `Storage` is
> responsible for its own protection — the example above is for tests only. Throw
> `StorageCryptoException` from `retrieve` or `store` to signal a
> cryptographic failure, which surfaces as `CredentialsManagerError.CryptoFailed`.

### Credentials Manager errors

```kotlin
import com.auth0.kmp.core.credentials.CredentialsManagerError

when (val result = credentialsManager.getCredentials()) {
    is Result.Success -> callApi(result.data.accessToken)
    is Result.Failure -> when (val error = result.error) {
        CredentialsManagerError.NoCredentials -> showLogin()
        CredentialsManagerError.NoRefreshToken -> showLogin()
        is CredentialsManagerError.LargeMinTtl -> log(error.minTtl, error.lifetime)
        is CredentialsManagerError.ApiError -> log(error.code, error.errorDescription)
        is CredentialsManagerError.Network -> retryLater(error.cause)
        is CredentialsManagerError.StoreFailed -> log(error.cause)
        is CredentialsManagerError.DeserializationFailed -> log(error.cause)
        is CredentialsManagerError.CryptoFailed -> showLogin()
        CredentialsManagerError.DPoPKeyMissing -> showLogin()
        CredentialsManagerError.DPoPKeyMismatch -> showLogin()
        CredentialsManagerError.DPoPNotConfigured -> log("enable useDPoP")
        is CredentialsManagerError.DPoPKeyUnavailable -> log(error.cause)
        is CredentialsManagerError.Unknown -> log(error.cause)
    }
}
```

> [!NOTE]
> When credentials cannot be decrypted (`CryptoFailed`) or a DPoP key is missing
> or no longer matches the stored tokens, the manager clears the stored
> credentials — the user must log in again.

## DPoP (Android / iOS)

[DPoP](https://datatracker.ietf.org/doc/html/rfc9449) binds tokens to a
device-held key, so a stolen access token cannot be replayed elsewhere. Enable it
once on the account:

```kotlin
val account = Auth0Account(
    clientId = "YOUR_CLIENT_ID",
    domain = "YOUR_DOMAIN",
    useDPoP = true,
)
```

Every client then applies DPoP automatically: the key pair is generated and
stored in platform hardware-backed storage, and proofs are attached to token
requests and to renewals. There is no per-call API.

> [!IMPORTANT]
> DPoP must be enabled for your application in the Auth0 Dashboard as well. Tokens
> obtained without DPoP cannot be renewed with DPoP enabled and vice versa — the
> Credentials Manager reports `DPoPKeyMismatch` or `DPoPNotConfigured` and clears
> the store, so change this setting at a natural logout boundary.

## Networking (Android / iOS)

### Timeouts

```kotlin
import com.auth0.kmp.core.NetworkingConfiguration

val account = Auth0Account(
    clientId = "YOUR_CLIENT_ID",
    domain = "YOUR_DOMAIN",
    configuration = NetworkingConfiguration(
        connectTimeoutMillis = 15_000,
        requestTimeoutMillis = 20_000,
    ),
)
```

Both default to 10 seconds.

### Logging

```kotlin
import com.auth0.kmp.core.NetworkLogLevel

NetworkingConfiguration(logLevel = NetworkLogLevel.HEADERS)
```

Levels are cumulative: `NONE` (default), `BASIC` (method, URL, status), `HEADERS`,
`BODY`.

> [!WARNING]
> `HEADERS` and `BODY` print tokens and other credentials to the system log. Use
> them only in debug builds, never in a release.

### Default headers

Sent with every request the SDK makes.

```kotlin
NetworkingConfiguration(
    defaultHeaders = mapOf("X-Correlation-Id" to correlationId),
)
```

### Per-request options

Most Authentication API methods take a `RequestOptions` for one-off parameters,
headers, and retry behaviour.

```kotlin
import com.auth0.kmp.core.RequestOptions

auth0.authentication.userInfo(
    accessToken = credentials.accessToken,
    options = RequestOptions(
        headers = mapOf("X-Request-Id" to requestId),
    ),
)
```

### Retries

Retries are off by default (`RetryPolicy.None`). Opt in per request:

```kotlin
import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.networking.retry.Backoff
import com.auth0.kmp.networking.retry.RetryPolicy
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val policy = RetryPolicy(
    maxAttempts = 3,
    backoff = Backoff.Exponential(
        base = 200.milliseconds,
        multiplier = 2.0,
        maxDelay = 2.seconds,
        jitter = true,
    ),
    retryOn = { error ->
        error is TransportError.Timeout ||
            error is TransportError.NoInternet ||
            (error is TransportError.Server && error.status >= 500)
    },
)

auth0.authentication.userInfo(
    accessToken = credentials.accessToken,
    options = RequestOptions(retryPolicy = policy),
)
```

`Backoff.Fixed(delay)` is available for a constant wait. Keep `jitter = true` for
exponential backoff so retrying clients do not synchronize.

> [!WARNING]
> Only retry idempotent operations. Retrying a login or signup can produce
> duplicate side effects.

## Handling results

Every SDK operation returns `Result<D, E>` — a sealed type with `Success` and
`Failure`. No SDK operation throws for a domain error, so failures cannot be
missed by accident.

```kotlin
when (val result = auth0.webAuth.login()) {
    is Result.Success -> handle(result.data)
    is Result.Failure -> handle(result.error)
}
```

Helpers are available for cases where exhaustive matching is more than you need:

```kotlin
import com.auth0.kmp.core.result.flatMap
import com.auth0.kmp.core.result.fold
import com.auth0.kmp.core.result.getOrNull
import com.auth0.kmp.core.result.map

// Collapse both branches into one value.
val message = result.fold(
    onSuccess = { "Signed in as ${it.idToken}" },
    onFailure = { "Login failed: $it" },
)

// Null on failure.
val credentials = result.getOrNull()

// Transform the success value, preserving the error.
val accessToken = result.map { it.accessToken }

// Chain another operation that can itself fail.
val profile = result.flatMap { credentials ->
    auth0.authentication.userInfo(credentials.accessToken)
}
```

Every error family implements the `Auth0Error` marker interface, and each one is
sealed, so `when` over a specific family is exhaustive without an `else`.
Transport failures are wrapped as a `Network` case carrying a `TransportError`:

```kotlin
import com.auth0.kmp.core.error.isUnauthorized

is WebAuthError.Network -> when (val cause = error.cause) {
    TransportError.NoInternet -> showOffline()
    TransportError.Timeout -> retryLater()
    is TransportError.Server ->
        if (cause.isUnauthorized) showLogin() else log(cause.status, cause.body)
    is TransportError.Serialization -> log(cause.message)
    is TransportError.Unknown -> log(cause.message)
}
```

`isUnauthorized` and `isForbidden` are extension properties covering HTTP 401 and
403.

<details>
  <summary>Swift</summary>

SKIE bridges `Result` to a Swift enum, so `onEnum(of:)` gives you an exhaustive
`switch`. Because the generic payload arrives as `Any`, cast it to the type you
expect.

```swift
switch onEnum(of: result) {
case .success(let success):
    if let credentials = success.data as? Credentials {
        print(credentials.accessToken)
    }
case .failure(let failure):
    if let error = failure.error as? WebAuthError {
        // handle
    }
}
```

Kotlin `suspend` functions are exposed as Swift `async` functions and are called
with `try await`.
</details>

```kotlin
import com.auth0.kmp.core.useragent.Auth0UserAgent
import com.auth0.kmp.webauth.webAuthClient

val userAgent = Auth0UserAgent.default().withLibrary("my-wrapper", "1.4.0")
val client = webAuthClient(account, userAgent)
```

## Resource management

`Auth0` owns a single network transport shared by every client it vends, which is
why it is the recommended entry point. Close it when you are done — for example
when the owning ViewModel or scope is destroyed:

```kotlin
auth0.close()
```

Clients can also be built standalone, without the umbrella. Each then owns its
own transport and must be closed individually:

```kotlin
import com.auth0.kmp.authentication.authenticationClient
import com.auth0.kmp.credentials.credentialsManager
import com.auth0.kmp.webauth.webAuthClient

val webAuth = webAuthClient(account)
val authentication = authenticationClient(account)
val credentials = credentialsManager(account)
```

> [!IMPORTANT]
> Close a client only if you created it standalone. Clients obtained from `Auth0`
> share its transport — closing one of those would break the others. Call
> `auth0.close()` instead.
