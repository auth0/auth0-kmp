//
//  EmbeddedLoginView.swift
//  iosApp
//

import SwiftUI
import Auth0

struct EmbeddedLoginView: View {

    let viewModel: AuthViewModel
    let isConfigured: Bool

    @State private var email = ""
    @State private var password = ""
    @State private var realm = "Username-Password-Authentication"

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("Log in to continue")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.xl)

                if !isConfigured {
                    configBanner
                        .padding(.bottom, Spacing.lg)
                }

                labeledField("Email or username") {
                    TextField("", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .modifier(InputFieldStyle())
                }

                labeledField("Password") {
                    SecureField("", text: $password)
                        .textContentType(.password)
                        .modifier(InputFieldStyle())
                }
                .padding(.top, Spacing.md)

                labeledField("Realm (database connection)") {
                    TextField("", text: $realm)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .modifier(InputFieldStyle())
                }
                .padding(.top, Spacing.md)

                Button {
                    Task {
                        await viewModel.login(email: email, password: password, realm: realm)
                    }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView().tint(.brandOnPrimary)
                        } else {
                            Text("Log in").fontWeight(.semibold)
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
        .navigationTitle("Embedded Login")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var isLoading: Bool {
        if case .loading = viewModel.state { return true }
        return false
    }

    @ViewBuilder
    private func labeledField<Field: View>(_ label: String, @ViewBuilder field: () -> Field) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).font(.subheadline.weight(.medium))
            field()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var configBanner: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("Auth0 not configured")
                .font(.subheadline.weight(.semibold))
            Text("Set AUTH0_DOMAIN and AUTH0_CLIENT_ID in Config.xcconfig, then rebuild.")
                .font(.caption)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .background(Color.red.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: Sizes.cornerLarge))
        .foregroundStyle(.red)
    }

    @ViewBuilder
    private var statusView: some View {
        // Success navigates away to the Welcome screen, so only failures render here.
        if case .failure(let error) = viewModel.state {
            Text(message(for: error))
                .foregroundStyle(.red)
        }
    }

    private func message(for error: AuthenticationError) -> String {
        // SKIE turns the Kotlin sealed AuthenticationError into a Swift enum.
        switch onEnum(of: error) {
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
}
