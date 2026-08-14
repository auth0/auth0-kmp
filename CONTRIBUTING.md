# Contributing

Thanks for your interest in improving the Auth0 SDK for Kotlin Multiplatform.

> [!IMPORTANT]
> Tests must be added for all new functionality, and existing tests must be
> updated for all changed or fixed functionality. All tests must pass on **both**
> Android and iOS. All new public API must be documented with KDoc.

Before you start, please read:

- [Auth0's general contribution guidelines](https://github.com/auth0/open-source-template/blob/master/GENERAL-CONTRIBUTING.md)
- [Auth0's code of conduct](https://github.com/auth0/open-source-template/blob/master/CODE-OF-CONDUCT.md)

## Before you open a pull request

For anything beyond a small fix — a new feature, a public API change, or a change
in behaviour — **open an issue first** so we can agree on the approach. This SDK
has a published, versioned public API, so API shape is a design decision rather
than an implementation detail. Discussing it up front avoids work being rejected
after the fact.

Please do not open a public pull request or issue for a security vulnerability.
See [SECURITY.md](SECURITY.md).

## Setting up your environment

Build prerequisites, module layout, test commands, and sample app configuration
are documented in **[DEVELOPMENT.md](DEVELOPMENT.md)**. Start there.

The short version:

```bash
git clone https://github.com/auth0/auth0-kmp.git
cd auth0-kmp
./gradlew build
```

## What to know before you write code

This is a **library**, not an application. A few consequences shape most
contributions:

### Keep application concerns out of the SDK modules

The SDK modules must not depend on UI toolkits, application entry points, or
app-only dependencies — consumers inherit everything we depend on. Anything
demo-only belongs in `sample-app/`, never in a published module.

### Put shared logic in `commonMain`

Prefer implementing behaviour once in `commonMain`. Use `expect`/`actual` only
where the platform genuinely differs (secure storage, browser session, logging,
cryptography). If you add an `expect` declaration, you must add an `actual` for
**every** target, or the build will fail on the target you missed.

### Design for non-breaking evolution

We follow [Semantic Versioning](https://semver.org). Once released, public API is
a contract. When adding API, prefer shapes that can grow later:

- Add optional parameters with default values rather than new overloads.
- Prefer sealed hierarchies and interfaces over concrete classes for types
  consumers only ever receive.
- Do not remove or rename public declarations; deprecate them with
  `@Deprecated` and a `ReplaceWith` where possible.

### Mark internals as internal

Anything a consumer should not call must be either Kotlin `internal` or annotated
with `@InternalAuth0Api`. Do not widen visibility purely for testing —
`internal` declarations are already visible to the module's own test source sets.

### KDoc describes intent, not rationale

KDoc on a class, function, or property states **what it is and what it's for** —
the contract a consumer relies on. Design rationale, trade-offs, and history do
not belong in doc comments.

## Coding conventions

- Kotlin official code style (`kotlin.code.style=official`).
- Declare visibility explicitly on every top-level and member declaration —
  write `public` rather than relying on it being the default. This makes the
  published surface obvious in review, which matters for a library.
- Match the style of the surrounding file. Do not reformat unrelated code in
  a functional change — mixed diffs are hard to review.

## Testing

Tests live in three source sets per module: `commonTest` (shared),
`androidHostTest` (JVM host), and `iosTest` where present. Put a test in
`commonTest` unless it genuinely needs a platform API.

```bash
# everything
./gradlew allTests

# per platform
./gradlew testAndroidHostTest
./gradlew iosSimulatorArm64Test

# a single class
./gradlew :auth0-core:testAndroidHostTest --tests "com.auth0.kmp.core.SomeTest"
```

**Prefer fakes over mocks.** A hand-written fake that implements the interface is
easier to read, works identically across all targets, and does not tie the test
to a JVM-only mocking framework. Since the SDK programs to interfaces, a fake is
usually a few lines.

CI runs Android host tests on `ubuntu-latest` and iOS simulator tests on
`macos-latest`; both must pass.

## Submitting your change

1. Fork the repository and create a branch from `main`.
2. Make your change, with tests and KDoc.
3. Run `./gradlew build` and the test commands above locally.
4. Open a pull request and fill in the template. Explain what changed and why,
   call out any public API additions, and note anything you did not test.

Please keep pull requests focused on a single concern. Unrelated cleanups in the
same PR make review slower and bisecting harder later.

## Reporting bugs and requesting features

Use the [issue templates](https://github.com/auth0/auth0-kmp/issues/new/choose).
For bugs, the most useful thing you can tell us is **whether the problem
reproduces on Android, on iOS, or on both** — that alone usually narrows the
cause to shared code or to one platform's `actual` implementation.

For questions and general discussion, the [Auth0 Community](https://community.auth0.com)
forums are usually a faster route than an issue.
