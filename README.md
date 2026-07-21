![Auth0 KMP SDK](https://cdn.auth0.com/website/sdk/banners/image.png)

Kotlin Multiplatform SDK for adding [Auth0](https://auth0.com) authentication to
Android and iOS apps from a single shared codebase. It provides browser-based
login/logout, direct authentication against the Authentication API, and secure,
platform-native credential storage — with a common Kotlin API and idiomatic
Swift interop on iOS.

[![License](https://img.shields.io/:license-Apache%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

🚀 [**Getting Started**](#getting-started) • 💡 [**Usage**](#usage) • 💬 [**Feedback**](#feedback)

## Getting Started

### Requirements

- **Kotlin** 2.3.21 (Kotlin Multiplatform)
- **Android** minSdk 24 or higher
- **iOS** 14.0 or higher
- An [Auth0 account](https://auth0.com/signup) with a **Native** application

### Configure Auth0

1. Create a **Native** application in the [Auth0 Dashboard](https://manage.auth0.com/#/applications).
2. Note your application's **Client ID** and **Domain**.
3. Add your platform callback and logout URLs to the application's **Allowed
   Callback URLs** and **Allowed Logout URLs** (see the platform setup sections
   below for the exact URL formats).

### Configure the SDK

Everything starts from an `Auth0Account`, which holds your tenant credentials and
optional settings. It is shared across every client.

```kotlin
import com.auth0.kmp.core.Auth0Account

val account = Auth0Account(
    clientId = "YOUR_CLIENT_ID",
    domain = "YOUR_DOMAIN", // e.g. your-tenant.us.auth0.com
)
```

`Auth0Account` also accepts:

- `configuration: NetworkingConfiguration` — networking/transport tuning.
- `useDPoP: Boolean = false` — opt in to sender-constrained (DPoP) tokens.

### Create the SDK entry point

`Auth0` is the recommended entry point when you use more than one feature. It
owns a single network transport shared by every client it vends, so you
configure the account once and reuse it everywhere.

```kotlin
import com.auth0.kmp.Auth0

val auth0 = Auth0(account)

val webAuth = auth0.webAuth               // browser login/logout
val authentication = auth0.authentication // direct Authentication API
val credentials = auth0.credentials()     // secure credential storage
```
 
When you are finished with the SDK, close it to release the
shared transport:

```kotlin
auth0.close()
```

### Android setup

**1. Provide an application `Context`.** The SDK captures it automatically via
`androidx.startup`. If you have disabled the startup initializer, provide it
explicitly once (for example in your `Application.onCreate`):

```kotlin
import com.auth0.kmp.core.Auth0Android

Auth0Android.init(context)
```

**2. Declare the callback scheme.** Web Auth needs the redirect scheme and domain
wired through manifest placeholders in your app module's `build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        manifestPlaceholders["auth0Scheme"] = applicationId // or a custom scheme
        manifestPlaceholders["auth0Domain"] = "YOUR_DOMAIN"
    }
}
```

**3. Register the callback URL** in the Auth0 Dashboard, using the scheme above:

```
{scheme}://YOUR_DOMAIN/android/YOUR_APP_PACKAGE_NAME/callback
```

**4. Credential storage and device backup.** The Credentials Manager stores
tokens in two files inside your app's private storage:

| Contents | Location |
|----------|----------|
| Encrypted credentials | `files/datastore/auth0_credentials.preferences_pb` |
| Encryption key material | `shared_prefs/auth0_credentials_keyset_prefs.xml` |

`auth0-credentials` ships backup rules that **exclude these two files** from
[Android Auto Backup](https://developer.android.com/guide/topics/data/autobackup)
and device-to-device transfer, so credentials never leave the device. No action
is required **unless your app defines its own backup rules.**

If your app sets `android:dataExtractionRules` or `android:fullBackupContent` on
its `<application>` element, the manifest merger reports a conflict, because these
attributes reference a single rules file each and cannot be combined — your app's
value replaces the SDK's. To resolve it, copy the two `<exclude>` entries into
your own rules files and tell the merger you are intentionally overriding the SDK:

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">
    <application
        android:dataExtractionRules="@xml/your_backup_rules"
        android:fullBackupContent="@xml/your_full_backup"
        tools:replace="android:dataExtractionRules,android:fullBackupContent">
        ...
    </application>
</manifest>
```

```xml
<!-- res/xml/your_backup_rules.xml — Android 12+ (API 31+) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="datastore/auth0_credentials.preferences_pb" />
        <exclude domain="sharedpref" path="auth0_credentials_keyset_prefs.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="datastore/auth0_credentials.preferences_pb" />
        <exclude domain="sharedpref" path="auth0_credentials_keyset_prefs.xml" />
    </device-transfer>
</data-extraction-rules>
```

```xml
<!-- res/xml/your_full_backup.xml — Android 11 and lower (API 30 and below) -->
<full-backup-content>
    <exclude domain="file" path="datastore/auth0_credentials.preferences_pb" />
    <exclude domain="sharedpref" path="auth0_credentials_keyset_prefs.xml" />
</full-backup-content>
```

> ⚠️ If you override the SDK's rules **without** re-adding these entries, the two
> credential files will be included in your backups again. The build still
> succeeds — the protection is lost silently.

### iOS setup

Add the generated `Auth0` framework to your Xcode project, then register your
callback URL scheme in the Dashboard:

```
{scheme}://YOUR_DOMAIN/ios/YOUR_BUNDLE_IDENTIFIER/callback
```

Kotlin `suspend` functions are exposed to Swift as `async` functions, and Kotlin
sealed types (such as `Result` and the error hierarchies) are bridged to Swift
enums via SKIE, so you can `switch` over them exhaustively.

## Usage

All client methods are `suspend` functions and return a `Result` — call them from
a coroutine (Kotlin) or `await` them (Swift).

### Web Auth login

Opens the system browser for Universal Login and returns the user's credentials.

```kotlin
import com.auth0.kmp.webauth.webAuthClient
import com.auth0.kmp.webauth.LoginOptions
import com.auth0.kmp.core.result.Result

val client = webAuthClient(account)

when (val result = client.login(LoginOptions(scope = "openid profile email offline_access"))) {
    is Result.Success -> {
        val credentials = result.data
        // credentials.accessToken, credentials.idToken, credentials.refreshToken
    }
    is Result.Failure -> {
        // result.error is a WebAuthError
    }
}
```

`LoginOptions` lets you tune the request: `scope`, `audience`, `connection`,
`organization`, `prompt`, `maxAge`, `redirectUri`, `scheme`, `ephemeral`, and
`extraParameters`.

### Web Auth logout

Clears the user's session in the browser.

```kotlin
import com.auth0.kmp.webauth.LogoutOptions

client.logout(LogoutOptions(federated = false))
```

`WebAuthClient` is `AutoCloseable`; call `close()` when you are done with it, and
`cancel()` to abort an in-flight login.

### Authentication API

Direct login against a database connection (realm), without opening a browser:

```kotlin
import com.auth0.kmp.authentication.authenticationClient

val auth = authenticationClient(account)

val result = auth.login(
    usernameOrEmail = "user@example.com",
    password = "a-secret-password",
    realm = "Username-Password-Authentication",
    scope = "openid profile email",
)
```

### Credentials Manager

Stores credentials securely (Android Keystore-backed DataStore; iOS Keychain) and
transparently renews the access token with the refresh token when it has expired.

```kotlin
import com.auth0.kmp.credentials.credentialsManager

val manager = credentialsManager(account)

// After a successful login:
manager.saveCredentials(credentials)

// Later — returns valid credentials, renewing them if needed:
when (val result = manager.getCredentials()) {
    is Result.Success -> useAccessToken(result.data.accessToken)
    is Result.Failure -> { /* result.error is a CredentialsManagerError */ }
}

// Check without triggering a renewal:
val loggedIn = manager.hasValidCredentials()

// On logout:
manager.clearCredentials()
```

`getCredentials` accepts optional `scope`, `minTtl`, `parameters`, `headers`, and
`forceRefresh` arguments. For custom persistence, pass a `storeKey` and your own
`Storage` implementation — either through the umbrella (`auth0.credentials(storeKey)`
or `auth0.credentials(storeKey, storage)`) or via the standalone `credentialsManager`
overloads.

### Handling results

Every operation returns `Result<D, E>`, a sealed type with `Success` and
`Failure`. Besides `when`, helpers are available:

```kotlin
import com.auth0.kmp.core.result.fold
import com.auth0.kmp.core.result.getOrNull

result.fold(
    onSuccess = { credentials -> /* ... */ },
    onFailure = { error -> /* ... */ },
)

val credentials = result.getOrNull()
```

## Modules

The SDK is split into focused modules. 

| Module | Provides |
|--------|----------|
| `auth0-kmp` | Umbrella: the `Auth0` entry point and the shared-transport composition root. Aggregates all feature modules. |
| `auth0-core` | `Auth0Account`, the `Result` type and error hierarchies, and shared building blocks used by the other modules. |
| `auth0-webauth` | Browser-based Universal Login and logout (`WebAuthClient`). |
| `auth0-authentication` | Direct calls against the Authentication API (`AuthenticationClient`). |
| `auth0-credentials` | Secure, platform-native credential storage and renewal (`CredentialsManager`). |

## Feedback

### Contributing

We appreciate feedback and contribution to this repo! Before you get started, please see the following:

- [Auth0's general contribution guidelines](https://github.com/auth0/open-source-template/blob/master/GENERAL-CONTRIBUTING.md)
- [Auth0's code of conduct guidelines](https://github.com/auth0/open-source-template/blob/master/CODE-OF-CONDUCT.md)

### Raise an Issue

To provide feedback or report a bug, [please raise an issue on our issue tracker](https://github.com/auth0/auth0-kmp/issues).

### Vulnerability Reporting

Please do not report security vulnerabilities on the public GitHub issue tracker. The [Responsible Disclosure Program](https://auth0.com/whitehat) details the procedure for disclosing security issues.

### Important Note

Portions of this SDK may have AI-assisted or generated code.

---


<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="https://cdn.auth0.com/website/sdks/logos/auth0_light_mode.png" width="150">
    <source media="(prefers-color-scheme: dark)" srcset="https://cdn.auth0.com/website/sdks/logos/auth0_dark_mode.png" width="150">
    <img alt="Auth0 Logo" src="https://cdn.auth0.com/website/sdks/logos/auth0_light_mode.png" width="150">
  </picture>
</p>
<p align="center">Auth0 is an easy-to-implement, adaptable authentication and authorization platform. To learn more check out <a href="https://auth0.com/why-auth0">Why Auth0?</a></p>
<p align="center">
This project is licensed under the Apache License 2.0. See the <a href="./LICENSE">LICENSE</a> file for more info.<br>
Copyright 2026 Okta, Inc.<br>
Licensed under the Apache License, Version 2.0 (the "<a href="./LICENSE">License</a>");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at<br>
&nbsp;&nbsp;&nbsp;&nbsp;<a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a><br>
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the <a href="./LICENSE">License</a> for the specific language governing permissions and
limitations under the License.
</p>
