# Auth0 KMP SDK — Architecture Decisions

Living record of the architecture decisions for the **auth0-kmp** SDK. This file
is committed and shared (unlike `CLAUDE.md`, which is gitignored and holds
collaboration rules only). Update it alongside Claude's memory whenever a
decision is made or changed, so we can always revisit the *why* if something
breaks.

Last updated: 2026-06-04

---

## Toolchain

| Thing | Version |
|---|---|
| Kotlin | 2.3.21 |
| AGP | 9.1.1 (`com.android.kotlin.multiplatform.library`) |
| Gradle | 9.3.1 |
| android compileSdk / minSdk | 36 / 24 |
| JVM target | 17 |

- New KMP default structure: `androidLibrary {}` DSL inside the `kotlin {}` block
  (AGP 9), replacing the old separate `android {}` block.
- Typesafe project accessors enabled (incubating); version catalog in
  `gradle/libs.versions.toml`.
- We build on Gradle 9; revisit Gradle-8 consumer compatibility later if needed.

---

## Module architecture

Target end-state modules (we build **only `auth0-core` + the umbrella first** and
grow additively — adding a module later is non-breaking; reshaping public
boundaries is breaking):

- **`auth0-core`** — pure `commonMain`, ZERO 3rd-party deps, ZERO platform code.
  Models (Credentials, UserInfo, Auth0Config), `Result<D,E>`, `Auth0Error` marker
  + common errors (NetworkError), public interfaces. Everyone depends on it; it
  depends on nothing.
- **`auth0-networking`** — Ktor HttpClient, `expect httpEngineFactory()`
  (OkHttp/Darwin), `safeCall{}` error mapping. Its OWN module (not merged into
  core) to keep core pure. Depends: core.
- **`auth0-authentication`** — Auth0 REST client + DTOs (kotlinx.serialization):
  /oauth/token, /userinfo, signup, MFA, refresh. Depends: core, networking.
- **`auth0-webauth`** — WebAuthClient (Auth-Code + PKCE), `BrowserLauncher`
  injected interface (NOT expect), AND the crypto expect/actual (`sha256`,
  `secureRandom`). Depends: core, networking, authentication.
- **`auth0-credentials`** — CredentialsStorage interface + platform
  Keystore/Keychain, auto-refresh, optional biometric gate. Depends: core
  (+ authentication).
- **`auth0-kmp`** (umbrella) — pure aggregator, `api`-exports all modules, iOS
  framework named `Auth0`, eventual facade/composition root. `api()` for
  JVM/Android transitive re-export + `export()` for iOS framework symbol folding.

### Deliberately NOT separate modules (with split trigger)

- No `auth0-pkce` module — PKCE has one consumer (webauth), lives there.
- No `auth0-crypto` module YET — the two crypto expect/actual fns live in webauth.
  **Trigger to promote:** when ID-token signature validation (JWKS/RSA verify)
  becomes a SECOND crypto consumer.

### Open / unplaced

- ID-token validation (JWKS, signature, claims) — either folds into
  `auth0-authentication` or triggers the `auth0-crypto` split. Decide when we add
  auth.

### Naming

- Flat scheme: `auth0-core`, `auth0-networking`, … ; artifacts
  `com.auth0:auth0-core` etc. (kept "kmp" in the umbrella artifact because plain
  `com.auth0:auth0` is ambiguous against Auth0's other SDKs).

---

## Error-handling architecture (decision #1a — LOCKED 2026-06-04)

Pattern: **marker interface + per-domain sealed hierarchies** (validated by
reference SDKs — Philip Lackner's `Result`, AndroidPoet/supabase-kmp).

```kotlin
// auth0-core (explicitApi OFF for now → no explicit `public` keyword; default is public)
interface Auth0Error                       // bare marker — NOT sealed, NO subtypes here

sealed interface NetworkError : Auth0Error {
    data object NoInternet : NetworkError
    data object Timeout : NetworkError
    data object Unauthorized : NetworkError                       // HTTP 401
    data object Forbidden : NetworkError                          // HTTP 403
    data class Server(val status: Int, val body: String?) : NetworkError  // other non-2xx
    data class Serialization(val message: String) : NetworkError
    data class Unknown(val message: String?) : NetworkError
}
```

**NetworkError decisions (2026-06-04):**
- `Unauthorized` (401) and `Forbidden` (403) are first-class cases →
  `safeCall{}` routes 401→Unauthorized, 403→Forbidden, all *other* non-2xx→`Server`
  (one representation per status; no ambiguity).
- `Server` keeps raw `status` + nullable `body` for inspection. Auth0-specific
  error JSON parsing is a DOMAIN concern (authentication), NOT here — core is
  transport-only.
- NO `isTransient` property and NO transient/non-transient sub-sealing — retry
  classification will be custom logic later (sub-sealing rejected because
  `Server`'s transient-ness depends on its `status` value, not its type).
- NO `cause: Throwable` field — keeps errors value-comparable for fakes/tests.

- `Auth0Error` **cannot** be sealed: Kotlin forbids sealed subtypes across module
  boundaries, and domain errors live in their own modules.
- Common errors (NetworkError) live in core as their own sealed type.
- Each domain module defines `sealed interface XxxError : Auth0Error`.

### How domain errors absorb network failures — Option A (the bridge)

Because the marker isn't sealed, `NetworkError` and `WebAuthError` are
**siblings** — a `NetworkError` is NOT a `WebAuthError`, so it can't fit into
`Result<D, WebAuthError>`. Resolution: each domain error carries a wrapper case.

```kotlin
// auth0-webauth
public sealed interface WebAuthError : Auth0Error {
    public data object UserCancelled : WebAuthError
    public data class IdTokenValidationFailed(val reason: String) : WebAuthError
    public data class Network(val cause: NetworkError) : WebAuthError   // ← bridge
}
```

Caller stays fully exhaustive (no `else`) and can drill into `.cause`.

- **Rejected Option B** (`Result<D, Auth0Error>`): marker isn't sealed → forces
  `else`, loses exhaustiveness. No public API does this.
- **Rejected Option C** (NetworkError also implements WebAuthError): would make
  core depend on domain modules → circular dep. Impossible.

### Bridge is a CONVENTION, not a mandate

Include `Network(cause)` ONLY in domains whose flow actually makes a network call
(authentication, webauth, credentials-refresh). Pure-local flows
(credentials-storage read, PKCE/crypto param generation) must NOT add it — an
unreachable `Network` branch is misleading noise. Cannot be enforced via the
marker (interfaces can't require a subtype), so it's a documented per-module
convention.

---

## Result type (LOCKED 2026-06-04)

Lives in **`auth0-core`** (the only module everyone depends on → no cycles).

```kotlin
public sealed interface Result<out D, out E : Auth0Error> {
    public data class Success<out D>(val data: D) : Result<D, Nothing>
    public data class Failure<out E : Auth0Error>(val error: E) : Result<Nothing, E>
}
```

- **Named `Result`, NOT `Auth0Result`.** User chose this eyes-open on the
  `kotlin.Result` stdlib shadowing (Claude argued for `Auth0Result`; user
  overruled — keep `Result` for now). Any file needing both ours and stdlib's
  must fully-qualify `kotlin.Result`.
- `E : Auth0Error` bound ties the error channel to our marker.
- `out` variance + `Nothing` in each case means `Success(x)`/`Failure(e)` assign
  anywhere the wider `Result` is expected, no boilerplate type args.

### Ergonomic helpers (as extension functions, not members)

`fold`, `getOrNull`, `errorOrNull`, `isSuccess`, `isFailure`, `map`. Added now
(small set); grow lazily as call sites need more.

---

## File & package organization (REVISED 2026-06-04 — sub-packaged by concern)

- **Sub-packaged by concern** (Option A). Superseded the earlier flat-package
  decision: flat felt crowded as the module grows, and `util` was rejected as a
  junk-drawer name. Current packages under `com.auth0.core`:
  - `com.auth0.core.error` → `Auth0Error.kt`, `NetworkError.kt`
  - `com.auth0.core.result` → `Result.kt` (type + helper extensions)
  - Future: models (Credentials, Auth0Config) → a `model` package; never a `util`.
- **One type per file**: `Auth0Error.kt`, `NetworkError.kt`, `Result.kt`
  (Result's ergonomic helper extensions live beside it in `Result.kt`).
- **Directory path matches package** (standing convention, ALL modules): e.g.
  `auth0-core/src/commonMain/kotlin/com/auth0/core/error/Auth0Error.kt`. Kotlin
  doesn't require it, but we enforce it for clarity.
- Note: `Result` (in `.result`) imports `Auth0Error` (in `.error`) across the
  package boundary — fine, same module.
- Revisit as the module fills out.

## Models

Package split reflects a **config vs domain-data** distinction:
- `Auth0Account` (config the consumer *supplies*) stays at root `com.auth0.core`.
- `com.auth0.core.model` holds **domain data the SDK returns** (Credentials, and
  later UserInfo/UserProfile). Name is singular `model` (consistent with
  `error`/`result`). NOT a catch-all for all data classes; NOT `dto` (wire-format
  DTOs live in auth0-authentication, not core).

### Auth0Account (LOCKED 2026-06-04)

Entry-point configuration — the tenant + application coordinates every flow needs.

```kotlin
// com.auth0.core (root package — stays here; it's config, not returned data)
data class Auth0Account(
    val clientId: String,
    val domain: String,
)
```

### Credentials (LOCKED 2026-06-04)

Tokens returned after successful authentication. In `com.auth0.core.model`.

```kotlin
// com.auth0.core.model
data class Credentials(
    val accessToken: String,
    val idToken: String,
    val tokenType: String,            // Swift's name (Android: `type`)
    val expiresAt: kotlin.time.Instant, // Android's name (Swift's `expiresIn` misleading)
    val refreshToken: String? = null,
    val scope: String? = null,
    val recoveryCode: String? = null,
)
```

- **Field set is identical across Auth0.Android & Auth0.swift** (7 fields, same
  nullability). Only two *names* diverged; we picked the clearer of each:
  `tokenType` (Swift) + `expiresAt` (Android — Swift's `expiresIn` is misleading,
  it stores an instant not a duration).
- **`expiresAt: kotlin.time.Instant`** — stdlib, keeps core dependency-free. NOT
  `java.util.Date`/`NSDate` (platform-specific, absent from commonMain), NOT
  kotlinx-datetime (3rd-party dep). Expiry checks are pure common Kotlin
  (`Clock.System.now() >= expiresAt`); convert to native Date only at a platform
  boundary that demands it (`.toNSDate()`/`.toJavaDate()`).
- **Required vs optional:** accessToken/idToken/tokenType/expiresAt required;
  refreshToken/scope/recoveryCode default to null. Deliberately did NOT copy
  Swift's "default everything to empty" (that allows silently-invalid creds).
- **No serialization annotations** — wire mapping (`access_token`, `expires_in`→
  `expiresAt`) lives on the DTO in auth0-authentication.
- Caveat: exact `kotlin.time.Instant` stability + bridging helper names at Kotlin
  2.3.21 to be confirmed when build runs (VPN/TLS blocker); fallback if needed is
  kotlinx-datetime in a non-core module.

- **Pure value holder** — no URL building, no networking. Deriving endpoint URLs
  from `domain` is the networking layer's job (keeps core dependency-free).
- **`data class`** — value equality + `copy` (matters for fakes/tests; supports
  growing non-breakingly via optional params).
- **Two params for now** (`clientId`, `domain`); will grow (custom domain,
  telemetry, etc.) as Auth0.Android/swift parity demands — added as optional
  params so call sites keep compiling.
- **No validation/normalization** in core — domain normalization belongs to the
  networking layer that owns the URL contract.
- **Naming:** chose `Auth0Account` (Auth0's own internal term for tenant+app).
  Rejected `Auth0Client` (implies behavior; this has none, and collides with the
  real AuthenticationClient/WebAuthClient) and `Auth0` (vague, plus would stutter
  to `Auth0.Auth0` for native-iOS consumers since the umbrella framework is named
  `Auth0`). `Auth0Config` was the runner-up.

### Distribution (context for framework naming)

Primary consumer for now is **KMP apps** (Gradle/klib dependency) — native iOS
already has Auth0.swift, so shipping a Swift Package/CocoaPod/.xcframework is
low-priority. BUT we keep the iOS `framework { baseName = "Auth0" }` wiring in the
build files anyway (cheap, preserves the option).

### UserInfo + Address (LOCKED 2026-06-04)

In `com.auth0.core.model`. Models the **OIDC `/userinfo` standard claims** (OpenID
Connect Core 1.0 §5.1) — NOT Auth0.Android's richer `UserProfile`.

- **Cross-SDK inconsistency flagged:** Auth0.swift `UserInfo` = pure OIDC standard
  claims; Auth0.Android `UserProfile` = a DIFFERENT, richer object with
  Management-API extras (identities, userMetadata, appMetadata, extraInfo,
  createdAt). We modelled the **OIDC shape** (Swift's), named `UserInfo`, because
  it maps to one well-defined endpoint (`GET /userinfo`) and is a stable standard.
  Android's metadata/identities belong to a future Management-API `UserProfile`
  model (additive, non-breaking) — deferred, not scoped.
- **Fields** = OIDC §5.1 standard claims. `sub` required; everything else
  optional (default null).
- **`address`** modelled as its own `Address` data class (OIDC §5.1.1 — a
  structured object with formatted/streetAddress/locality/region/postalCode/
  country), NOT a `Map<String,String>`. Own file per one-type-per-file rule.
- **Type mappings** (Swift platform types → dep-free common): `URL`→`String`
  (profile/picture/website), `TimeZone`→`String` (zoneinfo), `Locale`→`String`
  (locale), `Date`→`Instant?` (updatedAt, consistent with Credentials).
- **`customClaims` omitted** — not an OIDC standard claim, and `[String:Any]` has
  no clean dep-free representation in core. If needed later, expose via the
  auth0-authentication DTO (which can use kotlinx.serialization JsonObject), not
  core.

## Cross-cutting conventions

- Public suspend fns return `Result<T, E>`; sealed `Auth0Error`.
- Every seam is an interface (BrowserLauncher, CredentialsStorage, networking
  client) → fakes-not-mocks in tests.
- Keep `expect` surface tiny (crypto + http engine only) so jsMain/wasmJsMain is
  additive when Web lands in a later release.
- `appleMain` (not `iosMain`) — PoC-validated, to be applied in build files.
- `explicitApi()` — **deferred 2026-06-04** (kept OFF for now; revisit later). It's
  PoC-validated and we still intend to enable it, but not yet — turning it on
  later is a non-breaking churn pass (add `public` keywords), so deferring is
  safe.
- Manual DI between modules (constructor injection + factory functions, umbrella
  as composition root).

---

## Decision log

| Date | Decision | Notes |
|---|---|---|
| 2026-06-03 | Module architecture agreed | core + umbrella first, grow additively |
| 2026-06-03 | networking is its own module | keeps core pure |
| 2026-06-04 | Error model = marker + per-domain sealed (#1a) | Lackner/AndroidPoet pattern |
| 2026-06-04 | Network bridge = Option A (`Network(cause)` case) | convention, not mandate |
| 2026-06-04 | Result lives in core, named `Result` | user overruled `Auth0Result` |
| 2026-06-04 | Ergonomic helpers as extensions, small set now | fold/getOrNull/errorOrNull/isSuccess/isFailure/map |
| 2026-06-04 | Flat `com.auth0.core` package + one-type-per-file | SUPERSEDED below |
| 2026-06-04 | Sub-packaged by concern: `.error` + `.result` (Option A) | replaced flat; `util` rejected as junk-drawer; future models → `.model` |
| 2026-06-04 | Auth0Account data class (clientId, domain) | pure value holder; named Auth0Account (not Auth0/Auth0Client/Auth0Config) |
| 2026-06-04 | Primary consumer = KMP apps; keep iOS framework wiring | native iOS covered by Auth0.swift; framework block stays, low-priority |
| 2026-06-04 | Credentials model in `com.auth0.core.model` | 7 fields; tokenType+expiresAt names; expiresAt: kotlin.time.Instant |
| 2026-06-04 | `.model` package = returned domain data only | Auth0Account stays at root (config, not returned data) |
| 2026-06-04 | UserInfo = OIDC §5.1 standard claims + Address (§5.1.1) | chose Swift's OIDC shape over Android's Management-API UserProfile; customClaims omitted |
| 2026-06-04 | `explicitApi()` deferred (OFF for now) | still intended; enabling later is non-breaking |
| 2026-06-04 | NetworkError cases finalized | Unauthorized+Forbidden first-class; no isTransient; no Throwable cause |
