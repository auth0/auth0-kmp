//
//  PasswordlessView.swift
//  iosApp
//
//  Email passwordless: send a one-time code, then exchange it for credentials.
//  The "start" step drives viewModel.passwordlessState (idle → sending → codeSent);
//  the "verify" step drives the shared viewModel.state, so success navigates to
//  Welcome like every other login.
//

import SwiftUI
import Auth0

struct PasswordlessView: View {

    let viewModel: AuthViewModel
    let isConfigured: Bool

    @State private var email = ""
    @State private var code = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("Passwordless (email)")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.md)

                Text("We'll email you a one-time code, then you enter it to sign in.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, Spacing.xl)

                labeledField("Email") {
                    TextField("", text: $email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .disabled(codeSent)
                        .modifier(InputFieldStyle())
                }

                Button {
                    Task { await viewModel.passwordlessStart(email: email) }
                } label: {
                    Group {
                        if isSending {
                            ProgressView().tint(.brandOnPrimary)
                        } else {
                            Text(codeSent ? "Resend code" : "Send code").fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: Sizes.buttonHeight)
                    .foregroundStyle(Color.brandOnPrimary)
                    .background(Color.brandPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: Sizes.cornerLarge))
                }
                .disabled(isSending || isVerifying || !isConfigured || email.isEmpty)
                .padding(.top, Spacing.md)

                if codeSent {
                    labeledField("One-time code") {
                        TextField("", text: $code)
                            .textContentType(.oneTimeCode)
                            .keyboardType(.numberPad)
                            .modifier(InputFieldStyle())
                    }
                    .padding(.top, Spacing.lg)

                    Button {
                        Task { await viewModel.passwordlessVerify(email: email, code: code) }
                    } label: {
                        Group {
                            if isVerifying {
                                ProgressView()
                            } else {
                                Text("Verify & log in").fontWeight(.semibold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: Sizes.buttonHeight)
                        .overlay(
                            RoundedRectangle(cornerRadius: Sizes.cornerLarge)
                                .stroke(Color.brandPrimary, lineWidth: 1)
                        )
                    }
                    .disabled(isVerifying || code.isEmpty)
                    .padding(.top, Spacing.md)
                }

                statusView
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, Spacing.lg)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.lg)
        }
        .navigationTitle("Passwordless")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var isSending: Bool {
        viewModel.passwordlessState == .sending
    }

    private var codeSent: Bool {
        viewModel.passwordlessState == .codeSent
    }

    private var isVerifying: Bool {
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

    @ViewBuilder
    private var statusView: some View {
        // Start-step failures render from passwordlessState; verify-step failures
        // come through the shared state. Verify success navigates away to Welcome.
        if case .failure(let error) = viewModel.passwordlessState, let authError = error as? AuthenticationError {
            Text(message(for: authError)).foregroundStyle(.red)
        } else if codeSent {
            Text("Code sent — check your inbox.").foregroundStyle(Color.brandPrimary)
        }
        if case .failure(let error) = viewModel.state, let authError = error as? AuthenticationError {
            Text(message(for: authError)).foregroundStyle(.red)
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
