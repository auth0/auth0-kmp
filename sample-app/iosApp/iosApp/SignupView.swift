//
//  SignupView.swift
//  iosApp
//
//  Creates a database user via AuthenticationClient.createUser. A success is
//  driven by the view model's signupState (not the login state), which navigates
//  to the confirmation screen — so this view only renders loading and failure.
//

import SwiftUI
import Auth0

struct SignupView: View {

    let viewModel: AuthViewModel
    let isConfigured: Bool

    @State private var email = ""
    @State private var password = ""
    @State private var connection = "Username-Password-Authentication"

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("Create your account")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.xl)

                labeledField("Email") {
                    TextField("", text: $email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .modifier(InputFieldStyle())
                }

                labeledField("Password") {
                    SecureField("", text: $password)
                        .textContentType(.newPassword)
                        .modifier(InputFieldStyle())
                }
                .padding(.top, Spacing.md)

                labeledField("Connection (database)") {
                    TextField("", text: $connection)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .modifier(InputFieldStyle())
                }
                .padding(.top, Spacing.md)

                Button {
                    Task {
                        await viewModel.createUser(
                            email: email,
                            password: password,
                            connection: connection
                        )
                    }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView().tint(.brandOnPrimary)
                        } else {
                            Text("Sign up").fontWeight(.semibold)
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
        .navigationTitle("Sign up")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var isLoading: Bool {
        if case .loading = viewModel.signupState { return true }
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

    // Success navigates away to the confirmation screen, so only failures render.
    // createUser only ever fails with an AuthenticationError here.
    @ViewBuilder
    private var statusView: some View {
        if case .failure(let error) = viewModel.signupState,
           let authError = error as? AuthenticationError {
            Text(message(for: authError))
                .foregroundStyle(.red)
        }
    }

    private func message(for error: AuthenticationError) -> String {
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
