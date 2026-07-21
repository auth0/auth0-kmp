//
//  EmbeddedMethodsView.swift
//  iosApp
//
//  Sub-chooser shown after "Embedded Login": the four database-connection flows
//  the AuthenticationClient supports directly (no browser).
//

import SwiftUI

struct EmbeddedMethodsView: View {
    let onPasswordLogin: () -> Void
    let onSignup: () -> Void
    let onPasskeySignup: () -> Void
    let onPasskeyLogin: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Text("Embedded login")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.md)

                methodCard(
                    title: "Log in with password",
                    description: "Email and password against a database connection",
                    action: onPasswordLogin
                )
                methodCard(
                    title: "Sign up",
                    description: "Create a new database user",
                    action: onSignup
                )
                methodCard(
                    title: "Sign up with passkey",
                    description: "Register a passkey with Face ID or Touch ID",
                    action: onPasskeySignup
                )
                methodCard(
                    title: "Log in with passkey",
                    description: "Sign in with an existing passkey",
                    action: onPasskeyLogin
                )
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.lg)
        }
        .navigationTitle("Embedded Login")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func methodCard(
        title: String,
        description: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .font(.headline)
                Text(description)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.md)
            .overlay(
                RoundedRectangle(cornerRadius: Sizes.cornerLarge)
                    .stroke(Color.brandBorder, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}
