### Changes

Please describe both what is changing and why this is important. Include:

- Endpoints added, deleted, deprecated, or changed
- Classes and methods added, deleted, deprecated, or changed
- A summary of usage if this is a new feature or change to a public API (this should also be added to relevant documentation once released)

### Affected modules and targets

- Modules touched: <!-- auth0 / auth0-core / auth0-authentication / auth0-webauth / auth0-credentials / build-logic / sample-app -->
- Source sets touched: <!-- commonMain / androidMain / iosMain / commonTest / androidHostTest / iosTest -->

- [ ] This change is confined to shared code (`commonMain`) and needs no platform-specific work
- [ ] This change adds or modifies an `expect` declaration, and every `actual` has been updated

### References

Please include relevant links supporting this change such as a:

- support ticket
- community post
- StackOverflow post
- support forum thread

### Testing

Please describe how this can be tested by reviewers. Be specific about anything not tested and reasons why. Since this library has unit testing, tests should be added for new functionality and existing tests should complete without errors.

- [ ] This change adds unit test coverage

- [ ] This change has been verified on Android (`./gradlew testAndroidHostTest`)

- [ ] This change has been verified on iOS (`./gradlew iosSimulatorArm64Test`)

- [ ] This change has been manually verified in the Android and/or iOS sample app

### Checklist

- [ ] I have read this repo's [contributing guide](https://github.com/auth0/auth0-kmp/blob/main/CONTRIBUTING.md)

- [ ] I have read the [Auth0 general contribution guidelines](https://github.com/auth0/open-source-template/blob/master/GENERAL-CONTRIBUTING.md)

- [ ] I have read the [Auth0 Code of Conduct](https://github.com/auth0/open-source-template/blob/master/CODE-OF-CONDUCT.md)

- [ ] All existing and new tests complete without errors

- [ ] Public API additions carry KDoc describing their intent and contract

- [ ] This change is backwards compatible, or the breaking change is called out above and justified
