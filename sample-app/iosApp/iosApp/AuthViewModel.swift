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
        case failure(AuthenticationError)

        static func == (lhs: State, rhs: State) -> Bool {
            switch (lhs, rhs) {
                  case (.idle, .idle), (.loading, .loading):
                      return true
                  case let (.success(l), .success(r)):
                      return l.isEqual(r)
                  case let (.failure(l), .failure(r)):
                      return l.isEqual(r) 
                  default:
                      return false
                  }
        }
    }

    private(set) var state: State = .idle

    let isConfigured: Bool

    private let client: (any AuthenticationClient)?

    init(domain: String, clientId: String) {
        isConfigured = !domain.isEmpty && !clientId.isEmpty
        guard isConfigured else {
            client = nil
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

    func logout() {
        state = .idle
    }

    deinit {
        client?.close()
    }
}
