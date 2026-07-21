//
//  PasskeySignupView.swift
//  iosApp
//
//  Registers a new passkey. The view owns the platform ceremony (PasskeyCeremony)
//  and hands it to the view model as a closure, keeping AuthenticationServices out
//  of the view model. Success flows through the login state to the Welcome screen.
//

import SwiftUI
import Auth0

struct PasskeySignupView: View {

    let viewModel: AuthViewModel
    let isConfigured: Bool

    @State private var email = ""
    @State private var connection = "Username-Password-Authentication"

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("Sign up with a passkey")
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

                labeledField("Connection (database)") {
                    TextField("", text: $connection)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .modifier(InputFieldStyle())
                }
                .padding(.top, Spacing.md)

                Button {
                    Task {
                        await viewModel.passkeySignup(email: email, connection: connection) { options in
                            try await PasskeyCeremony.register(options: options)
                        }
                    }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView().tint(.brandOnPrimary)
                        } else {
                            Text("Create passkey").fontWeight(.semibold)
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
        .navigationTitle("Passkey sign up")
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

    // Success navigates away to Welcome, so only failures render. A failure is
    // either an SDK AuthenticationError or a ceremony CeremonyError — both are
    // Auth0Error, so read the message generically.
    @ViewBuilder
    private var statusView: some View {
        if case .failure(let error) = viewModel.state {
            Text(passkeyErrorMessage(error))
                .foregroundStyle(.red)
        }
    }
}
