//
//  AuthViewModel.swift
//  iosApp
//
//

import Foundation
import Auth0

@MainActor
@Observable
final class AuthViewModel {

    enum State: Equatable {
        // Initial state while the app checks storage for a saved session; the
        // splash overlay is shown until this resolves to success or idle.
        case restoring
        case idle
        case loading
        case success(Credentials)
        case failure(any Auth0Error)

        static func == (lhs: State, rhs: State) -> Bool {
            switch (lhs, rhs) {
                  case (.restoring, .restoring), (.idle, .idle), (.loading, .loading):
                      return true
                  case let (.success(l), .success(r)):
                      return l.isEqual(r)
                  case let (.failure(l), .failure(r)):
                return (l as? NSObjectProtocol)?.isEqual(r) ?? false
                  default:
                      return false
                  }
        }
    }

    enum LoginMethod {
        case embedded
        case webAuth
        case passkey
    }

    // Sign-up outcome, kept separate from `State` on purpose: createUser mints no
    // tokens, so a successful sign-up must NOT trip the global success→Welcome
    // navigation. This drives its own screen (the confirmation) instead.
    enum SignupState: Equatable {
        case idle
        case loading
        case success(DatabaseUser)
        case failure(any Auth0Error)

        static func == (lhs: SignupState, rhs: SignupState) -> Bool {
            switch (lhs, rhs) {
            case (.idle, .idle), (.loading, .loading):
                return true
            case let (.success(l), .success(r)):
                return l == r
            case let (.failure(l), .failure(r)):
                return (l as? NSObjectProtocol)?.isEqual(r) ?? false
            default:
                return false
            }
        }
    }

    private(set) var state: State = .restoring

    private(set) var signupState: SignupState = .idle

    // Remembers the credentials entered on the sign-up screen so the confirmation
    // screen's "Log in" button can complete the sign-up → login hop without asking
    // the user to retype them. Cleared by resetSignup().
    private var lastSignup: (email: String, password: String, connection: String)?

    // Tracks how the current session was established, so logout only performs the
    // browser round-trip for Web Auth sessions (embedded login holds no SSO cookie).
    private var loginMethod: LoginMethod?

    let isConfigured: Bool

    private let client: (any AuthenticationClient)?

    private let webClient: (any WebAuthClient)?

    private let credentialsManager: (any CredentialsManager)?

    private let audience = "https://firstresourceserver/"

    init(domain: String, clientId: String) {
        isConfigured = !domain.isEmpty && !clientId.isEmpty
        guard isConfigured else {
            client = nil
            webClient = nil
            credentialsManager = nil
            // No SDK client to check storage with, so there is nothing to restore.
            state = .idle
            return
        }
        // Kotlin default arguments do NOT cross into Swift, so every parameter
        // the Kotlin side defaults must be supplied explicitly here. These match
        // the SDK's Kotlin defaults (NetworkingConfiguration()).
        let configuration = NetworkingConfiguration(
            logLevel: .body,
            connectTimeoutMillis: 10_000,
            requestTimeoutMillis: 10_000,
            defaultHeaders: [:]
        )
        let account = Auth0Account(
            clientId: clientId,
            domain: domain,
            configuration: configuration,
            useDPoP: false
        )
        let userAgent = Auth0UserAgent.companion.default()
        client = authenticationClient(account: account, userAgent: userAgent)
        webClient = webAuthClient(account: account, userAgent: userAgent)
        // Uses the account-scoped store key and platform (Keychain) storage; the
        // single-argument overload exists so Swift needs no internal factories.
        // Module-qualified because the stored property above shadows the global
        // factory function of the same name inside this class.
        credentialsManager = Auth0.credentialsManager(account: account)
    }

    // Checks the Keychain for a saved session on launch. A restored session goes
    // straight to the logged-in screen; anything else (no credentials, or an
    // expired token with no refresh token) is not an error to show — it just
    // means the user needs to sign in, so we fall back to idle (the chooser).
    func restoreSession() async {
        guard let credentialsManager else {
            state = .idle
            return
        }
        do {
            let result = try await credentialsManager.getCredentials(
                scope: nil,
                minTtl: 30,
                parameters: [:],
                headers: [:],
                forceRefresh: false
            )
            switch onEnum(of: result) {
            case .success(let success):
                if let credentials = success.data as? Credentials {
                    state = .success(credentials)
                } else {
                    state = .idle
                }
            case .failure:
                state = .idle
            }
        } catch {
            state = .idle
        }
    }

    func login(
        email: String,
        password: String,
        realm: String,
        options: RequestOptions = RequestOptions(
            parameters: [:],
            headers: [:],
            retryPolicy: RetryPolicy.companion.None
        )
    ) async {
        guard let client else { return }
        state = .loading
        do {
            // Request offline_access so the tenant issues a refresh token, letting
            // getCredentials() renew an expired access token on a later launch.
            let result = try await client.login(
                usernameOrEmail: email,
                password: password,
                realm: realm,
                audience: audience,
                scope: "openid profile email offline_access",
                options: options
            )
            // SKIE turns the Kotlin sealed `Result` into a Swift enum we can
            // switch over exhaustively. The payload generic erases to AnyObject,
            // so we downcast to the concrete SDK types.
            switch onEnum(of: result) {
            case .success(let success):
                if let credentials = success.data as? Credentials {
                    loginMethod = .embedded
                    await saveCredentials(credentials)
                    state = .success(credentials)
                } else {
                    state = .idle
                }
            case .failure(let failure):
                if let error = failure.error as? AuthenticationError {
                    state = .failure(error)
                } else {
                    state = .idle
                }
            }
        } catch {
            // The suspend bridge is `async throws`; domain failures come back as
            // Result.Failure (handled above), so a thrown error here is unexpected
            // (e.g. cancellation). Reset for this sample.
            state = .idle
        }
    }
    
    
    func webLogin() async {
        guard let webClient else { return }
        state = .loading
        do {
                     let result = try await webClient.login(
                         options: LoginOptions(
                             scope: "openid profile email offline_access",
                             audience: audience,
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
                             loginMethod = .webAuth
                             await saveCredentials(credentials)
                             state = .success(credentials)
                         } else {
                             state = .idle
                         }
                     case .failure(let failure):
                         if let error = failure.error as? WebAuthError {
                             state = .failure(error)
                         } else {
                             state = .idle
                         }
                     }
                 } catch {
                     state = .idle
                 }
    }

    // Creates a database user. On success we land on the confirmation screen
    // (via signupState); createUser returns a DatabaseUser, not tokens, so the
    // login state is untouched and the user explicitly opts into logging in next.
    func createUser(email: String, password: String, connection: String) async {
        guard let client else { return }
        signupState = .loading
        do {
            let result = try await client.createUser(
                profile: SignupProfile(
                    email: email,
                    phoneNumber: nil,
                    username: nil,
                    name: nil,
                    givenName: nil,
                    familyName: nil,
                    nickname: nil,
                    picture: nil
                ),
                password: password,
                connection: connection,
                userMetadata: [:],
                options: defaultOptions()
            )
            switch onEnum(of: result) {
            case .success(let success):
                if let user = success.data as? DatabaseUser {
                    lastSignup = (email, password, connection)
                    signupState = .success(user)
                } else {
                    signupState = .idle
                }
            case .failure(let failure):
                if let error = failure.error as? AuthenticationError {
                    signupState = .failure(error)
                } else {
                    signupState = .idle
                }
            }
        } catch {
            signupState = .idle
        }
    }

    // Completes the sign-up → login hop using the credentials captured by
    // createUser. Delegates to login(), so a success flows through the normal
    // success→Welcome navigation.
    func completeSignupLogin() async {
        guard let signup = lastSignup else { return }
        await login(email: signup.email, password: signup.password, realm: signup.connection)
    }

    // Resets the sign-up screen before it is shown, so a stale confirmation from a
    // previous attempt never appears.
    func resetSignup() {
        signupState = .idle
        lastSignup = nil
    }

    // Passkey sign-up. The SDK is HTTP-only for passkeys, so the ceremony (the
    // Face ID sheet + new-passkey creation) is injected as a closure the caller
    // builds from the platform. Flow: signup challenge → run ceremony → send the
    // registration credential + the challenge's auth_session straight to
    // loginWithPasskey (no separate login challenge — verified against Auth0.Android).
    func passkeySignup(
        email: String,
        connection: String,
        runCeremony: (AuthnParamsPublicKey) async throws -> PublicKeyCredentials
    ) async {
        guard let client else { return }
        state = .loading
        do {
            let challengeResult = try await client.passkeySignupChallenge(
                profile: SignupProfile(
                    email: email,
                    phoneNumber: nil,
                    username: nil,
                    name: nil,
                    givenName: nil,
                    familyName: nil,
                    nickname: nil,
                    picture: nil
                ),
                userMetadata: [:],
                realm: connection,
                organization: nil,
                options: defaultOptions()
            )
            let challenge: PasskeyRegistrationChallenge
            switch onEnum(of: challengeResult) {
            case .success(let success):
                guard let value = success.data as? PasskeyRegistrationChallenge else {
                    state = .idle
                    return
                }
                challenge = value
            case .failure(let failure):
                state = (failure.error as? AuthenticationError).map(State.failure) ?? .idle
                return
            }

            let credential: PublicKeyCredentials
            do {
                credential = try await runCeremony(challenge.authParamsPublicKey)
            } catch let error as Auth0Error {
                state = .failure(error)
                return
            } catch {
                state = .idle
                return
            }

            await finishPasskeyLogin(
                authSession: challenge.authSession,
                credential: credential,
                realm: connection
            )
        } catch {
            state = .idle
        }
    }

    // Passkey login. Same shape as sign-up but with the login challenge and an
    // existing passkey; the assertion + auth_session go to loginWithPasskey.
    func passkeyLogin(
        connection: String,
        runCeremony: (AuthParamsPublicKey) async throws -> PublicKeyCredentials
    ) async {
        guard let client else { return }
        state = .loading
        do {
            let challengeResult = try await client.passkeyLoginChallenge(
                realm: connection,
                organization: nil,
                options: defaultOptions()
            )
            let challenge: PasskeyLoginChallenge
            switch onEnum(of: challengeResult) {
            case .success(let success):
                guard let value = success.data as? PasskeyLoginChallenge else {
                    state = .idle
                    return
                }
                challenge = value
            case .failure(let failure):
                state = (failure.error as? AuthenticationError).map(State.failure) ?? .idle
                return
            }

            let credential: PublicKeyCredentials
            do {
                credential = try await runCeremony(challenge.authParamsPublicKey)
            } catch let error as Auth0Error {
                state = .failure(error)
                return
            } catch {
                state = .idle
                return
            }

            await finishPasskeyLogin(
                authSession: challenge.authSession,
                credential: credential,
                realm: connection
            )
        } catch {
            state = .idle
        }
    }

    // Shared tail of both passkey flows: exchange the platform credential for
    // tokens. A passkey session is a local session (no browser cookie), so logout
    // treats it like embedded — hence loginMethod = .passkey.
    private func finishPasskeyLogin(
        authSession: String,
        credential: PublicKeyCredentials,
        realm: String
    ) async {
        guard let client else { return }
        do {
            let result = try await client.loginWithPasskey(
                authSession: authSession,
                authResponse: credential,
                realm: realm,
                organization: nil,
                audience: nil,
                scope: "openid email offline_access",
                options: defaultOptions()
            )
            switch onEnum(of: result) {
            case .success(let success):
                if let credentials = success.data as? Credentials {
                    loginMethod = .passkey
                    await saveCredentials(credentials)
                    state = .success(credentials)
                } else {
                    state = .idle
                }
            case .failure(let failure):
                state = (failure.error as? AuthenticationError).map(State.failure) ?? .idle
            }
        } catch {
            state = .idle
        }
    }

    // The SDK's RequestOptions default cannot cross from Kotlin into Swift, so
    // this rebuilds it explicitly — matching the login() parameter above.
    private func defaultOptions() -> RequestOptions {
        RequestOptions(
            parameters: [:],
            headers: [:],
            retryPolicy: RetryPolicy.companion.None
        )
    }

    func logout() {
        if loginMethod == .webAuth, let webClient {
            // Keep the current .success state (credentials stay on screen) during
            // the browser round-trip; only clear the view once logout succeeds.
            Task {
                do {
                    let result = try await webClient.logout(
                        options: LogoutOptions(
                            returnTo: nil,
                            scheme: nil,
                            federated: false,
                            extraParameters: [:]
                        )
                    )
                    switch onEnum(of: result) {
                    case .success:
                        await clearCredentials()
                        loginMethod = nil
                        state = .idle
                    // Logout did not complete: leave the user on the Welcome screen
                    // since the session was not cleared.
                    case .failure:
                        break
                    }
                } catch {
                    // Thrown error (e.g. cancellation): leave the view unchanged.
                }
            }
        } else {
            // Embedded login holds no browser session, so there is no round-trip:
            // just drop the stored credentials and return to the chooser.
            Task {
                await clearCredentials()
                loginMethod = nil
                state = .idle
            }
        }
    }

    // Persists credentials to the Keychain so a later launch can restore the session.
    // saveCredentials is `async throws` on the Swift side (SKIE bridges the Kotlin
    // suspend fun); a storage failure comes back as Result.Failure. This is a sample,
    // so a save failure is not surfaced: the user stays logged in for this run and
    // would only need to sign in again on the next launch.
    private func saveCredentials(_ credentials: Credentials) async {
        guard let credentialsManager else { return }
        do {
            _ = try await credentialsManager.saveCredentials(credentials: credentials)
        } catch {
            // Cancellation or an unexpected throw: ignore, the session stays valid in-memory.
        }
    }

    // Removes the stored credentials on logout so the next launch starts at the chooser.
    private func clearCredentials() async {
        guard let credentialsManager else { return }
        do {
            _ = try await credentialsManager.clearCredentials()
        } catch {
            // Nothing stored / cancellation: ignore.
        }
    }

    deinit {
        client?.close()
        webClient?.close()
    }
}
