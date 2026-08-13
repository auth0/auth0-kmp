# Change Log

## [1.0.0-beta.0](https://github.com/auth0/auth0-kmp/tree/1.0.0-beta.0) (2026-08-13)

First beta release of the Auth0 SDK for Kotlin Multiplatform, providing a single
shared API for Android and iOS.

**Added**

- **Web Auth** — browser-based Universal Login and logout via `WebAuthClient`,
  using Custom Tabs on Android and `ASWebAuthenticationSession` on iOS. Supports
  audience, scope, connection, organization, prompt, `maxAge`, custom schemes and
  redirect URIs, ephemeral sessions, and extra authorization parameters.
- **Authentication API** — `AuthenticationClient` for direct calls: database
  connection login, user signup, password reset, `/userinfo`, refresh-token
  renewal, and token revocation.
- **Passkeys** — passkey login and signup challenge/exchange, letting the host app
  run the platform WebAuthn ceremony.
- **Credentials Manager** — secure credential persistence with automatic
  access-token renewal. Encrypted DataStore backed by the Android Keystore on
  Android, Keychain on iOS, plus a `Storage` interface for custom persistence and
  namespaced multi-store support.
- **DPoP (RFC 9449)** — opt-in sender-constrained tokens via
  `Auth0Account(useDPoP = true)`, with hardware-backed key storage, proof
  generation, and nonce-retry handling.
- **ID token validation** — signature and claims validation with a typed
  `IdTokenValidationError` family.
- **Typed error handling** — every operation returns `Result<D, E>`; no domain
  error is thrown. Sealed per-domain error families (`WebAuthError`,
  `AuthenticationError`, `CredentialsManagerError`, `DPoPError`,
  `IdTokenValidationError`) .
- **Networking configuration** — configurable connect/request timeouts, default
  headers, cumulative log levels, per-request `RequestOptions`, and opt-in retry
  policies with fixed or exponential backoff with jitter.