//
//  WelcomeView.swift
//  iosApp
//

import SwiftUI
import Auth0

struct WelcomeView: View {

    let viewModel: AuthViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Text("You're logged in")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.xl)

                if let credentials = loggedInCredentials {
                    tokenField("Access token", credentials.accessToken)
                    tokenField("ID token", credentials.idToken)
                    credentialRow("Token type", credentials.tokenType)
                    credentialRow("Expires at", String(describing: credentials.expiresAt))
                    credentialRow("Expires in", expiresIn(credentials))
                    credentialRow("Refresh token", credentials.refreshToken ?? "— not granted")
                    credentialRow("Scope", credentials.scope ?? "—")
                }

                Spacer(minLength: Spacing.lg)

                Button {
                    viewModel.logout()
                } label: {
                    Text("Log out")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .frame(height: Sizes.buttonHeight)
                        .foregroundStyle(Color.brandOnPrimary)
                        .background(Color.brandPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: Sizes.cornerLarge))
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.lg)
        }
        .navigationTitle("Welcome")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
    }

    private var loggedInCredentials: Credentials? {
        if case .success(let credentials) = viewModel.state { return credentials }
        return nil
    }

    // Kotlin's `Instant` exposes `epochSeconds`; compare to now for a human delta.
    private func expiresIn(_ credentials: Credentials) -> String {
        let secondsLeft = credentials.expiresAt.epochSeconds - Int64(Date().timeIntervalSince1970)
        return secondsLeft > 0 ? "\(secondsLeft) s" : "expired"
    }

    // A short single-line claim: bold label above the value.
    @ViewBuilder
    private func credentialRow(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).font(.subheadline.weight(.semibold))
            Text(value).font(.body).foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // A long token value: shown in full, selectable, in a bordered monospace card.
    @ViewBuilder
    private func tokenField(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).font(.subheadline.weight(.semibold))
            Text(value)
                .font(.caption.monospaced())
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.md)
                .overlay(
                    RoundedRectangle(cornerRadius: Sizes.cornerLarge)
                        .stroke(Color.brandBorder, lineWidth: 1)
                )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
