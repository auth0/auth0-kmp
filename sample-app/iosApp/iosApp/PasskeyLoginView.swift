//
//  PasskeyLoginView.swift
//  iosApp
//
//  Signs in with an existing passkey. Same shape as PasskeySignupView: the view
//  owns the platform ceremony and hands it to the view model as a closure.
//

import SwiftUI
import Auth0

struct PasskeyLoginView: View {

    let viewModel: AuthViewModel
    let isConfigured: Bool

    @State private var connection = "Username-Password-Authentication"

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("Sign in with a passkey")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.xl)

                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Connection (database)").font(.subheadline.weight(.medium))
                    TextField("", text: $connection)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .modifier(InputFieldStyle())
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Button {
                    Task {
                        await viewModel.passkeyLogin(connection: connection) { options in
                            try await PasskeyCeremony.authenticate(options: options)
                        }
                    }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView().tint(.brandOnPrimary)
                        } else {
                            Text("Sign in with passkey").fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: Sizes.buttonHeight)
                    .foregroundStyle(Color.brandOnPrimary)
                    .background(Color.brandPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: Sizes.cornerLarge))
                }
                .disabled(isLoading || !isConfigured)
                .padding(.top, Spacing.lg)

                statusView
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, Spacing.lg)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.lg)
        }
        .navigationTitle("Passkey login")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var isLoading: Bool {
        if case .loading = viewModel.state { return true }
        return false
    }

    @ViewBuilder
    private var statusView: some View {
        if case .failure(let error) = viewModel.state {
            Text(passkeyErrorMessage(error))
                .foregroundStyle(.red)
        }
    }
}

// Renders any Auth0Error a passkey flow can produce: an SDK AuthenticationError
// (challenge or token step) or a CeremonyError (the on-device ceremony). Shared
// by both passkey screens.
func passkeyErrorMessage(_ error: any Auth0Error) -> String {
    if let ceremony = error as? CeremonyError {
        return ceremony.message
    }
    if let authError = error as? AuthenticationError {
        switch onEnum(of: authError) {
        case .apiError(let e):
            return "API error [\(e.code)]: \(e.errorDescription)"
        case .invalidInput(let e):
            return "Invalid input: \(e.message)"
        case .network:
            return "Network error"
        case .idTokenValidation:
            return "ID token validation failed"
        case .unknown:
            return "Unknown error"
        }
    }
    return "Unknown error"
}
