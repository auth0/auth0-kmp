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
    }

    private(set) var state: State = .restoring

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

            enableLogging: true,
            connectTimeoutMillis: 10_000,
            requestTimeoutMillis: 10_000,
            defaultHeaders: [:]
        )
        let account = Auth0Account(
            clientId: clientId,
            domain: domain,
            configuration: configuration
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

    func login(email: String, password: String, realm: String) async {
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
                scope: "openid profile email offline_access"
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
