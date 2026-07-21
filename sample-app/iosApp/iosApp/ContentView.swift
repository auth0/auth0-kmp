//
//  ContentView.swift
//  iosApp
//
//  Reads the Auth0 tenant config (injected from Config.xcconfig via Info.plist),
//

import SwiftUI

struct ContentView: View {
    var body: some View {
        // Always render the flow; when config is missing the view model reports
        // isConfigured == false and the login screen shows a banner, so the
        // chooser still appears instead of blocking the whole app.
        AuthFlowView(
            domain: Self.configValue("AUTH0_DOMAIN"),
            clientId: Self.configValue("AUTH0_CLIENT_ID")
        )
    }

    /// Reads a value injected into Info.plist from Config.xcconfig. Returns an
    /// empty string when the key is absent or still the empty placeholder.
    private static func configValue(_ key: String) -> String {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            return ""
        }
        return value
    }
}

/// Owns the shared AuthViewModel and the navigation stack. Split out from
/// ContentView so the view model is created exactly once, only after the tenant
/// config is known to be present.
private struct AuthFlowView: View {

    @State private var viewModel: AuthViewModel
    @State private var path: [Route] = []

    init(domain: String, clientId: String) {
        _viewModel = State(initialValue: AuthViewModel(domain: domain, clientId: clientId))
    }

    var body: some View {
        NavigationStack(path: $path) {
            ChooseSignInView(
                state: viewModel.state,
                onEmbeddedLogin: { path.append(.embeddedMethods) },
                onWebAuthLogin: { Task { await viewModel.webLogin() } }
            )
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .embeddedMethods:
                        EmbeddedMethodsView(
                            onPasswordLogin: { path.append(.embeddedLogin) },
                            onSignup: {
                                viewModel.resetSignup()
                                path.append(.signup)
                            },
                            onPasskeySignup: { path.append(.passkeySignup) },
                            onPasskeyLogin: { path.append(.passkeyLogin) }
                        )
                    case .embeddedLogin:
                        EmbeddedLoginView(viewModel: viewModel, isConfigured: viewModel.isConfigured)
                    case .signup:
                        SignupView(viewModel: viewModel, isConfigured: viewModel.isConfigured)
                    case .signupResult:
                        SignupResultView(viewModel: viewModel)
                    case .passkeySignup:
                        PasskeySignupView(viewModel: viewModel, isConfigured: viewModel.isConfigured)
                    case .passkeyLogin:
                        PasskeyLoginView(viewModel: viewModel, isConfigured: viewModel.isConfigured)
                    case .welcome:
                        WelcomeView(viewModel: viewModel)
                    }
                }
        }
        // The view model is the single source of truth; navigation reacts to its
        // state rather than the view model holding a navigation reference.
        .onChange(of: viewModel.state) { _, newState in
            switch newState {
            case .success:
                if path.last != .welcome { path.append(.welcome) }
            case .idle:
                path.removeAll()
            default:
                break
            }
        }
        // A successful createUser lands on the confirmation screen. It mints no
        // tokens, so login state is untouched and this is the only thing that
        // navigates there.
        .onChange(of: viewModel.signupState) { _, newState in
            if case .success = newState, path.last != .signupResult {
                path.append(.signupResult)
            }
        }
        // Cover the chooser with a splash while the Keychain check runs, so the
        // logged-out screen never flashes before a saved session is restored.
        .overlay {
            if viewModel.state == .restoring {
                SplashView()
            }
        }
        // Fires once when the view appears: checks the Keychain for a saved
        // session. restoreSession() resolves .restoring to either .success
        // (→ welcome) or .idle (→ chooser).
        .task {
            await viewModel.restoreSession()
        }
    }
}

/// Full-screen splash shown while the app checks storage for a saved session.
private struct SplashView: View {
    var body: some View {
        ZStack {
            Color(.systemBackground)
                .ignoresSafeArea()
            ProgressView()
        }
    }
}

#Preview {
    ContentView()
}
