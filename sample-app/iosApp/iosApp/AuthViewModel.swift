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
        case idle
        case loading
        case success(Credentials)
        case failure(any Auth0Error)

        static func == (lhs: State, rhs: State) -> Bool {
            switch (lhs, rhs) {
                  case (.idle, .idle), (.loading, .loading):
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

    private(set) var state: State = .idle

    // Tracks how the current session was established, so logout only performs the
    // browser round-trip for Web Auth sessions (embedded login holds no SSO cookie).
    private var loginMethod: LoginMethod?

    let isConfigured: Bool

    private let client: (any AuthenticationClient)?
    
    private let webClient: (any WebAuthClient)?

    init(domain: String, clientId: String) {
        isConfigured = !domain.isEmpty && !clientId.isEmpty
        guard isConfigured else {
            client = nil
            webClient = nil
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
        client = authenticationClient(account: account)
        webClient = webAuthClient(account: account)
    }

    func login(email: String, password: String, realm: String) async {
        guard let client else { return }
        state = .loading
        do {
            let result = try await client.login(
                usernameOrEmail: email,
                password: password,
                realm: realm,
                audience: nil,
                scope: "openid profile email"
            )
            // SKIE turns the Kotlin sealed `Result` into a Swift enum we can
            // switch over exhaustively. The payload generic erases to AnyObject,
            // so we downcast to the concrete SDK types.
            switch onEnum(of: result) {
            case .success(let success):
                if let credentials = success.data as? Credentials {
                    loginMethod = .embedded
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
                             loginMethod = .webAuth
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
            loginMethod = nil
            state = .idle
        }
    }

    deinit {
        client?.close()
        webClient?.close()
    }
}
