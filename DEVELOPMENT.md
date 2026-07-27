# Development

This guide covers building the SDK, running the test suites, and running the
Android and iOS sample apps locally.

## Prerequisites

- **JDK 17** (the modules and sample compile against Java 17 bytecode).
- **Android Studio** (latest stable) with the Android SDK, `compileSdk 36`, and an
  emulator or device on API 24+.
- **Xcode 16.x** on macOS for the iOS sample and iOS/Kotlin-native targets.
- An [Auth0 account](https://auth0.com/signup) with a **Native** application, for
  running the sample apps against a real tenant.

## Project layout

The build is a single Gradle project with these modules (see `settings.gradle.kts`):

| Module | Purpose |
| --- | --- |
| `:auth0-core` | Shared models, networking, error model, DPoP, platform `expect`/`actual`. |
| `:auth0-authentication` | Authentication API client (direct login). |
| `:auth0-webauth` | Browser-based Web Auth (login/logout). |
| `:auth0-credentials` | Secure credential storage and renewal. |
| `:auth0` | Umbrella that produces the iOS `Auth0` framework (exports the modules above). |
| `:sample-app:androidApp` | Android dogfooding sample. |

The iOS sample (`sample-app/iosApp`) is a standalone Xcode project, not a Gradle
module — it consumes the framework produced by `:auth0`.

## Building

Build everything:

```bash
./gradlew build
```

Compile a single module for a specific target (faster while iterating):

```bash
# Android
./gradlew :auth0-core:compileAndroidMain

# iOS simulator (arm64)
./gradlew :auth0-core:compileKotlinIosSimulatorArm64
```

## Running the tests

Tests live in three source sets per module: `commonTest` (shared),
`androidHostTest` (JVM host), and, where present, `iosTest`.

Run all unit tests:

```bash
./gradlew allTests
```

Run a module's Android host tests:

```bash
./gradlew :auth0-core:testAndroidHostTest
./gradlew :auth0-webauth:testAndroidHostTest
./gradlew :auth0-authentication:testAndroidHostTest
./gradlew :auth0-credentials:testAndroidHostTest
```

Run a module's iOS simulator tests:

```bash
./gradlew :auth0-core:iosSimulatorArm64Test
```

Run a single test class:

```bash
./gradlew :auth0-core:testAndroidHostTest --tests "com.auth0.kmp.core.SomeTest"
```

## Configuring Auth0 for the sample apps

Tenant credentials are read from **git-ignored** files so no secrets land in
source control. Never commit your real domain or client ID.

In your Auth0 application, add the callback and logout URLs for each platform
(see the [README](README.md) for the exact URL formats).

### Android sample config

Add your tenant values to the root `local.properties` (git-ignored):

```properties
auth0.domain=YOUR_DOMAIN
auth0.clientId=YOUR_CLIENT_ID
```

These are injected into `BuildConfig` and the manifest placeholders at build
time. If they are missing, the sample builds and runs but shows a "not
configured" state.

### iOS sample config

Copy the committed template and fill in your values:

```bash
cp sample-app/iosApp/iosApp/Config.xcconfig.template \
   sample-app/iosApp/iosApp/Config.xcconfig
```

Then edit `Config.xcconfig` (git-ignored):

```
AUTH0_DOMAIN = your-tenant.us.auth0.com
AUTH0_CLIENT_ID = YOUR_CLIENT_ID
```

`AUTH0_DOMAIN` is the host only, with no scheme — the SDK prepends `https://`.
The values flow into `Info.plist` and are read at runtime.

## Running the Android sample

Open the project in Android Studio and run the `sample-app:androidApp`
configuration on an emulator or device, or from the command line:

```bash
./gradlew :sample-app:androidApp:installDebug
```

## Running the iOS sample

1. Ensure the Android sample config step is not required — iOS uses its own
   `Config.xcconfig` (above).
2. Open `sample-app/iosApp/iosApp.xcodeproj` in Xcode.
3. Select an iOS Simulator destination and press **Run** (⌘R).

The Xcode project has a build phase that runs
`./gradlew :auth0:embedAndSignAppleFrameworkForXcode`, so the `Auth0`
framework is rebuilt and embedded automatically on each build — you do not need
to build it separately.
