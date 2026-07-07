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
                onEmbeddedLogin: { path.append(.embeddedLogin) },
                onWebAuthLogin: { Task { await viewModel.webLogin() } }
            )
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .embeddedLogin:
                        EmbeddedLoginView(viewModel: viewModel, isConfigured: viewModel.isConfigured)
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
    }
}

#Preview {
    ContentView()
}
