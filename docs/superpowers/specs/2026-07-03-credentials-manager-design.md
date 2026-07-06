# Credentials Manager — Design

Date: 2026-07-03 (rewritten 2026-07-04 onto the `telemetry` base)
Branch: `credential_manager` (now branched from `telemetry`, i.e. `web_auth` +
the `b196f34` Auth0-Client / UserAgent commit)
Status: Approved design, pre-implementation

## Purpose

Persist Auth0 `Credentials` securely on-device and return them on demand,
transparently renewing an expired (or soon-to-expire) access token via the
refresh token before handing credentials back. Full parity with Auth0.Android
`SecureCredentialsManager` and Auth0.swift v3.0 `CredentialsManager`, minus the
features this SDK does not yet have (see Deferred).

## Scope (v1)

In scope: secure store (`saveCredentials`), retrieve-with-auto-renew
(`getCredentials`), clear (`clearCredentials`), `hasValidCredentials`,
account-scoped single-flight renewal.

Deferred (YAGNI — none exist in this SDK yet; each is an additive, non-breaking
extension later per Rule 3): DPoP, biometric gating, API-credentials
(`getApiCredentials(forAudience:)` + per-audience storage + MRRT exchange), SSO
credentials exchange, `/oauth/revoke`.

## Base branch & telemetry (locked)

`credential_manager` branches from `telemetry`, not `web_auth`. `telemetry`
adds `b196f34` ("Added support for UserAgent on API requests"), which injects
the `Auth0-Client` header into **every** request through the
`NetworkingConfiguration.defaultHeaders` seam
(`NetworkClientFactory.kt:22-26`, applied by Ktor `defaultRequest`).

Consequence: the Credentials Manager exposes **no** telemetry / UserAgent
surface. This matches both references — neither Auth0.swift nor Auth0.Android's
credentials manager touches telemetry; it is an API-client concern. Because
renewal goes through the core `TokenClient` built on `networkClient(account,
userAgent)`, each renewal request inherits `Auth0-Client` automatically with
zero code in the manager. The `tokenClient(account)` factory MUST thread the
same `Auth0UserAgent.default()` default the other factories use, so renewal
traffic is attributed identically.

## Module layout & dependency direction

```
auth0-core   (interfaces + contract types only — no platform code)
  ├─ CredentialsManager           interface            (credentials/)
  ├─ CredentialsManagerError      sealed : Auth0Error   (core/error/)
  ├─ TokenClient                  interface            (core/token/)
  ├─ TokenGrant                   interface            (core/token/)
  └─ tokenClient(account, ua)     factory              (core/token/)

auth0-credentials   (NEW module; depends on auth0-core only)
  ├─ commonMain
  │    ├─ Storage                        interface (PUBLIC keyed String KV)
  │    ├─ DefaultCredentialsManager       : CredentialsManager  (internal)
  │    ├─ RefreshTokenGrant               : TokenGrant          (internal)
  │    ├─ CredentialsSerializer           (Credentials <-> JSON string)
  │    ├─ credentialsManager(...)         factory (common — single entry point)
  │    └─ createStorage()                 expect fun -> Storage (platform default)
  ├─ androidMain
  │    └─ DataStoreTinkStorage : Storage    (Preferences DataStore + Tink AEAD)
  └─ iosMain
       └─ KeychainStorage : Storage         (raw Security framework, no app crypto)

auth0-authentication, auth0-webauth   (unchanged by this work)
```

Dependency arrows: `credentials -> core` only. Nothing depends on
`auth0-credentials` at compile time except the consumer app. `authentication`
and `webauth` are untouched — renewal no longer routes through the
authentication client (see "Why TokenClient, not TokenRenewer").

`settings.gradle.kts`: add `include(":auth0-credentials")`.

### Why the interface (and its error) live in core

Auth0.swift v3.0 `WebAuth` holds an optional `CredentialsManager` and
auto-stores on login success / clears on logout success
(`useCredentialsManager(_:)`). To support the same later, `auth0-webauth` must
reference the `CredentialsManager` type without depending on the credentials
implementation module. Hoisting the interface to core makes that a clean
one-directional dependency. Because `getCredentials()` returns
`Result<Credentials, CredentialsManagerError>`, the error type is named in the
interface's signatures and therefore must live in core too, and it is contract
(consumers switch on it) — matching the repo convention that an error
co-locates with the interface that returns it (`AuthenticationError` with
`AuthenticationClient`).

## Token transport: `TokenClient` + `TokenGrant` (auth0-core)

Renewal is a `/oauth/token` call. Rather than depend on the whole
`AuthenticationClient` (or a bespoke `TokenRenewer`), the SDK gets a small,
Open/Closed transport for the token endpoint in core:

```kotlin
@InternalAuth0Api
public interface TokenGrant {
    /**
     * The `/oauth/token` form parameters for this grant, including
     * `grant_type` and `client_id`.
     */
    public val parameters: Map<String, String>
}

@InternalAuth0Api
public interface TokenClient : AutoCloseable {
    /** Exchanges [grant] at `/oauth/token` for fresh [Credentials]. */
    public suspend fun fetchToken(grant: TokenGrant): Result<Credentials, TransportError>
    override fun close()
}
```

- `TokenClient` returns the generic `TransportError` (repo convention); each
  caller owns its own `toXxxError()` mapper. It holds the `NetworkClient` and a
  `Clock`, JSON-encodes `grant.parameters`, POSTs `/oauth/token`, decodes
  `TokenResponse`, and calls `toCredentials(clock)`.
- `TokenGrant` is caller-owned and open: new grant types (code-exchange,
  password-realm, later CIBA/device-code) are added by callers without touching
  core. This work adds one grant, in the credentials module:

```kotlin
// auth0-credentials/commonMain, internal
internal class RefreshTokenGrant(
    refreshToken: String,
    clientId: String,
    scope: String? = null,
    audience: String? = null,
    extra: Map<String, String> = emptyMap(),
) : TokenGrant {
    override val parameters: Map<String, String> = buildMap {
        put("grant_type", "refresh_token")
        put("client_id", clientId)
        put("refresh_token", refreshToken)
        scope?.let { put("scope", it) }
        audience?.let { put("audience", it) }
        putAll(extra)   // getCredentials(parameters=...) pass-through
    }
}
```

### Why `TokenClient`, not `TokenRenewer`

Putting refresh in core (behind `TokenClient`) makes the credentials manager
independent of `auth0-authentication` — it depends on `auth0-core` only, so
`credentials` and `authentication` are siblings with no edge between them.
The earlier `TokenRenewer` interface (which would have been implemented by
`auth0-authentication`) is **dropped**. `getCredentials` has `forceRefresh`;
the transport does not — `fetchToken` always fetches, "should I refresh" is a
manager decision.

### Factory (auth0-core)

```kotlin
@InternalAuth0Api
public fun tokenClient(
    account: Auth0Account,
    userAgent: UserAgent = Auth0UserAgent.default(),
): TokenClient   // built on networkClient(account, userAgent) -> inherits Auth0-Client
```

## CredentialsManager interface & error (auth0-core)

```kotlin
public interface CredentialsManager {
    public suspend fun saveCredentials(credentials: Credentials): Result<Unit, CredentialsManagerError>
    public suspend fun clearCredentials(): Result<Unit, CredentialsManagerError>
    public suspend fun hasValidCredentials(minTtl: Long = 0): Boolean
    public suspend fun getCredentials(
        scope: String? = null,
        minTtl: Int = 0,
        parameters: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        forceRefresh: Boolean = false,
    ): Result<Credentials, CredentialsManagerError>
}
```

Method names follow Auth0.Android (`saveCredentials` / `getCredentials` /
`clearCredentials` / `hasValidCredentials`). `getCredentials` mirrors
Auth0.Android's fuller surface (scope, minTtl, parameters, headers,
forceRefresh). `minTtl` is `Int` seconds on `getCredentials` and `Long` on
`hasValidCredentials` — matching each method's native signature. `audience` is
intentionally OFF `getCredentials` for v1: per-audience caching is the deferred
API-credentials feature; adding the param without per-audience storage would be
a half-kept promise. It arrives later as `getApiCredentials(audience, …)`
(additive), not a param here.

```kotlin
public sealed interface CredentialsManagerError : Auth0Error {
    public data object NoCredentials : CredentialsManagerError
    public data object NoRefreshToken : CredentialsManagerError
    public data class LargeMinTtl(val minTtl: Int, val lifetime: Long) : CredentialsManagerError
    public data class ApiError(
        val code: String,
        val description: String,
        val statusCode: Int,
    ) : CredentialsManagerError
    public data class Network(val cause: TransportError) : CredentialsManagerError
    public data class Unknown(val cause: TransportError) : CredentialsManagerError
    public data class StoreFailed(val cause: Throwable? = null) : CredentialsManagerError
    public data class DeserializationFailed(val cause: Throwable? = null) : CredentialsManagerError
}
```

The renewal-facing cases (`ApiError` / `Network` / `Unknown`) deliberately
mirror `AuthenticationError`'s shape, since renewal failures come back as the
same `TransportError`. The module owns a mapper next to the error:

```kotlin
internal fun TransportError.toCredentialsManagerError(): CredentialsManagerError = when (this) {
    TransportError.NoInternet,
    TransportError.Timeout -> CredentialsManagerError.Network(this)
    is TransportError.Server -> parseAuth0ErrorBody(body)
        ?.let { CredentialsManagerError.ApiError(it.error, it.errorDescription ?: it.error, status) }
        ?: CredentialsManagerError.Unknown(this)
    is TransportError.Serialization,
    is TransportError.Unknown -> CredentialsManagerError.Unknown(this)
}
```

`LargeMinTtl` is the post-renewal parity case (Auth0.Android `LARGE_MIN_TTL`):
the fresh token's own lifetime is still shorter than the requested `minTtl`, so
no renewal can satisfy the caller. `StoreFailed` / `DeserializationFailed` wrap
local storage/serialization faults. `RenewFailed` and `ClearFailed` from the
old design are **removed** — renewal faults now flow through the
`TransportError` mapper, and a failed clear surfaces as `StoreFailed`.
The error lives in `core/error/`.

## DefaultCredentialsManager logic (auth0-credentials/commonMain, internal)

Holds: `Storage`, `TokenClient`, `Clock` (injected for testable expiry),
`Json`, `clientId`, `storeKey`, and the account-scoped `Mutex`.

- **saveCredentials(credentials)** — serialize to JSON,
  `storage.store(storeKey, json)`; map any throw to `StoreFailed(cause)`.
- **clearCredentials()** — `storage.remove(storeKey)`; throw -> `StoreFailed(cause)`.
- **hasValidCredentials(minTtl)** — read + deserialize; `false` if
  absent/unparseable; else `expiresAt - now > minTtl` (refresh-token presence
  is irrelevant here, matching native).
- **getCredentials(scope, minTtl, parameters, headers, forceRefresh)** — the
  10-step flow below, entirely under the account `Mutex`.

### getCredentials — 10-step flow

1. Acquire the account-scoped `Mutex` (single-flight; see below).
2. `storage.retrieve(storeKey)` — `null` => `NoCredentials`.
3. Deserialize the blob — failure => `DeserializationFailed(cause)`.
4. Decide if renewal is needed: `forceRefresh` OR the access token expires
   within `minTtl` OR the requested `scope` differs from the stored scope.
5. If no renewal needed => return the stored credentials.
6. Renewal needed but `refreshToken` is null/blank => `NoRefreshToken`.
7. Build `RefreshTokenGrant(refreshToken, clientId, scope, extra = parameters)`
   and call `tokenClient.fetchToken(grant)`. (Per-request `headers` are threaded
   to the transport for this call.)
8. On `Result.Failure` => `error.toCredentialsManagerError()` (Network / ApiError
   / Unknown). Stored credentials are left untouched.
9. On success, **merge** — carry the refresh token forward only:
   `renewed.copy(refreshToken = renewed.refreshToken?.takeIf { it.isNotBlank() } ?: stored.refreshToken)`.
   `scope` is taken from the renewal response as-is (verified parity:
   Auth0.Android `SecureCredentialsManager.kt:933-942` and Auth0.swift
   `Credentials.swift:226-228` both carry forward only the refresh token).
10. Post-renewal `minTtl` guard: if the merged token still expires within
    `minTtl` => `LargeMinTtl(minTtl, lifetime)`. Otherwise persist the merged
    credentials (`StoreFailed` on throw) and return them.

### Single-flight guard (account-scoped Mutex)

Concurrent `getCredentials` callers must not fire parallel refreshes or race on
the write. The whole `getCredentials` body (and `saveCredentials` /
`clearCredentials`) runs under a `kotlinx.coroutines.sync.Mutex.withLock`.

The lock is **account-scoped**, not instance-scoped: two
`DefaultCredentialsManager` instances built for the same account+storeKey must
share one lock, or they could still race on the same storage slot. An internal
registry keyed by `(clientId, storeKey)` hands out one `Mutex` per key. This
matches Auth0.Android's account-shared `serialExecutor` and Auth0.swift's
`SynchronizationBarrier`.

## Storage interface & platform actuals (auth0-credentials)

`Storage` is a **public**, injected, secure keyed String KV — the Swift-style
secure-storage contract. It is not `@InternalAuth0Api`: consumers may supply
their own implementation (e.g. an existing keystore), exactly as Auth0.swift
(`CredentialsStorage`) and Auth0.Android (`Storage`) allow.

```kotlin
public interface Storage {
    public suspend fun retrieve(key: String): String?   // null = missing
    public suspend fun store(key: String, value: String)
    public suspend fun remove(key: String)
}
```

Multi-slot keyed KV is the model (Auth0.Android writes 7+ keys into one
storage). We keep `storeKey` + `storage` as **two separate parameters** so a
single secure store can hold many related entries scoped by key — collapsing
them into one single-slot store would strip that configurability. `null` for a
missing key is the KMP idiom and keeps the manager's "missing => NoCredentials"
a simple null check. Actuals throw on genuine I/O/crypto failure; the manager
maps to `StoreFailed`.

Deliberate divergence from Auth0.swift's `Data`+throws `CredentialsStorage`: we
use `String` + nullable-`retrieve`. `Credentials` serializes to a JSON string;
nullable-return-for-absent is idiomatic KMP. Semantics identical.

### Main-safety

`retrieve` / `store` / `remove` are `suspend` and do blocking I/O + crypto.
Each platform actual confines that work with `withContext(Dispatchers.IO)`, so
callers can invoke `getCredentials` from any dispatcher without blocking the
main thread. The commonMain manager does not itself switch dispatchers.

### Android — DataStoreTinkStorage (androidMain)

- Preferences **DataStore** holds the ciphertext (base64) per key.
- **Tink** does AEAD directly on a stable release
  (`com.google.crypto.tink:tink-android`, STABLE): an `Aead` from an
  `AndroidKeysetManager`-managed keyset, the keyset itself wrapped by an
  `android-keystore://…` master key. This is Google's stated replacement for the
  deprecated `EncryptedSharedPreferences`. We do NOT hand-roll Keystore AES-GCM
  (Auth0.Android's hand-rolled `CryptoUtil` exists only to support API 19; our
  `minSdk = 24` makes that unnecessary).
- The AndroidKeyStore master key is non-exportable and device-bound —
  credentials cannot be extracted or restored to another device (the device-only
  guarantee, Android side).
- `store` = Tink-encrypt -> base64 -> DataStore write; `retrieve` = read ->
  base64-decode -> Tink-decrypt (null if absent); `remove` = DataStore remove.
- The Tink keyset lives in its **own** (Keystore-wrapped) prefs file, separate
  from the ciphertext DataStore, so wiping credentials never risks the keyset.
- The Android `Context` needed to build DataStore comes from the SDK-wide
  `ApplicationContextHolder` (already on the base branch) — the common
  `createStorage()` actual reads it, so no `Context` parameter is needed on the
  factory.
- androidMain deps: `androidx.datastore:datastore-preferences`,
  `com.google.crypto.tink:tink-android`.

### iOS — KeychainStorage (iosMain)

- Keychain via raw Security framework cinterop (`SecItemAdd` /
  `SecItemCopyMatching` / `SecItemUpdate` / `SecItemDelete`),
  `kSecClassGenericPassword`.
- **No app-level crypto** — the Keychain provides at-rest protection at the OS
  level (parity with Auth0.swift SimpleKeychain).
- Attributes (verified against SimpleKeychain source):
  - `kSecAttrService` = bundle identifier (per-app scope), same as
    SimpleKeychain's default `Bundle.main.bundleIdentifier`.
  - `kSecAttrAccount` = the storage `key`. Multiple accounts / multiple manager
    instances differentiate by key, so distinct Auth0 accounts don't collide.
  - `kSecAttrAccessible` = **`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`**
    — device-only. This is a deliberate divergence from SimpleKeychain's default
    (`kSecAttrAccessibleAfterFirstUnlock`): the `…ThisDeviceOnly` variant keeps
    tokens available after first unlock (so background refresh works) but
    excludes them from encrypted backups / restore-to-another-device.
  - `kSecAttrSynchronizable` = unset (iCloud Keychain sync OFF). Sync is
    controlled by this attribute, independent of accessibility; leaving it unset
    keeps tokens off iCloud.
- `store` = upsert; `retrieve` = copy-matching -> UTF-8 (null on
  `errSecItemNotFound`); `remove` = delete (ignore not-found).

### Device-only security (both platforms, locked)

Credentials are local to the device on both platforms: Android via the
non-exportable AndroidKeyStore-bound key, iOS via
`…AfterFirstUnlockThisDeviceOnly` + no iCloud sync. Tokens are never written to
a backup that could restore them onto a different device.

## Factory (auth0-credentials/commonMain)

A single common factory — no per-platform overloads and no `Context` parameter,
because the Android `Context` is resolved internally from
`ApplicationContextHolder`:

```kotlin
public fun credentialsManager(
    account: Auth0Account,
    storeKey: String = "credentials_${account.clientId}",
    storage: Storage = createStorage(),
): CredentialsManager {
    val tokenClient = tokenClient(account)              // inherits Auth0-Client
    return DefaultCredentialsManager(
        storage = storage,
        tokenClient = tokenClient,
        clock = Clock.System,
        clientId = account.clientId,
        storeKey = storeKey,
    )
}

internal expect fun createStorage(): Storage   // Android: DataStoreTinkStorage; iOS: KeychainStorage
```

- `storeKey` defaults to `"credentials_${account.clientId}"` so two managers for
  different Auth0 accounts don't collide in one secure store; consumers can
  override it verbatim.
- `storage` defaults to the platform secure store via `createStorage()` but is
  injectable (Rule 4 — program to interfaces; test with a fake).
- `account` supplies both the transport coordinates and the `clientId` used for
  the default `storeKey` and the account-scoped `Mutex` registry key.

## Deferred / follow-ups

- Auto-store-on-login / clear-on-logout wiring into `WebAuthClient` (the
  Auth0.swift v3.0 `useCredentialsManager` seam) — the reason the interface is
  in core.
- API credentials, SSO credentials, DPoP, biometrics, revoke.
- (Resolved, no longer a follow-up: the "web_auth Context holder swap" note from
  the previous draft is moot — the base branch already provides
  `ApplicationContextHolder`, and the factory reads it directly.)

## Testing strategy

Fakes not mocks (Rule 5). Test cases reviewed before any test file is written
(Rule 6).

`commonTest` (bulk — pure logic):
- `FakeStorage : Storage` — in-memory map + failure toggle (exercises
  `StoreFailed`).
- `FakeTokenClient : TokenClient` — canned `Result`, records the `TokenGrant`
  parameters (proves refresh_token / scope / params pass-through) and call-count
  (proves single-flight).
- Fixed/mutable fake `Clock` for deterministic expiry.

`DefaultCredentialsManager` cases (final list confirmed before writing):
- saveCredentials then getCredentials (valid) => same creds, no renew.
- getCredentials, nothing stored => `NoCredentials`.
- stored blob unparseable => `DeserializationFailed`.
- expired + refresh token => renews, persists, returns renewed; grant carried the
  stored refresh token.
- expired + no refresh token => `NoRefreshToken`.
- forceRefresh=true on valid creds => renews anyway.
- minTtl within window => renews; outside => does not.
- scope differs from stored => renews (grant carries new scope); same => does not.
- renew transport failure => Network / ApiError / Unknown per mapper; stored
  creds untouched.
- refresh-token carry-forward: renewed creds null/blank refresh token => merged
  keeps the stored one; non-blank => merged uses the new one.
- post-renewal token still shorter-lived than minTtl => `LargeMinTtl`.
- saveCredentials throws => `StoreFailed`; clearCredentials throws => `StoreFailed`.
- single-flight: N concurrent getCredentials on expired creds => `TokenClient`
  called exactly once, all callers get the same renewed creds.
- account-scoped lock: two managers, same (clientId, storeKey) => share one lock
  (still single renewal across both).
- hasValidCredentials(minTtl) true / false / absent.

Platform tests (thin — `Storage` actuals only):
- Android: `DataStoreTinkStorage` round-trips store/retrieve/remove; retrieve
  absent => null. Tink/Keystore may require an instrumented (on-device) test
  rather than host — confirm host vs device at build time.
- iOS (iosTest): `KeychainStorage` round-trips against the simulator keychain;
  verify `…ThisDeviceOnly` accessibility and no synchronizable attribute.
