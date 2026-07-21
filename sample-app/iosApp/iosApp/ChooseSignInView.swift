//
//  ChooseSignInView.swift
//  iosApp
//

import SwiftUI
import Auth0

struct ChooseSignInView: View {
    let state: AuthViewModel.State
    let onEmbeddedLogin: () -> Void
    let onWebAuthLogin: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            brandBadge
                .padding(.top, Spacing.xl)

            Text("Choose how to sign in")
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.lg)
                .padding(.bottom, Spacing.xl)

            optionCard(
                title: "Embedded Login",
                description: "Total brand control and low user friction",
                enabled: true,
                action: onEmbeddedLogin
            )

            optionCard(
                title: "Web Auth",
                description: "Hosted Universal Login in a secure browser tab",
                enabled: true,
                action: onWebAuthLogin
            )
            .padding(.top, Spacing.md)

            // Web Auth runs from this screen (it navigates nowhere on failure), so
            // surface its error here — otherwise a cancelled or failed browser
            // round-trip leaves the user on the chooser with no explanation.
            if case .failure(let error) = state, let webError = error as? WebAuthError {
                Text(message(for: webError))
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, Spacing.lg)
            }

            Spacer()
        }
        .padding(.horizontal, Spacing.lg)
    }

    // SKIE turns the Kotlin sealed WebAuthError into a Swift enum we can switch
    // over exhaustively; render a human message for each case.
    private func message(for error: WebAuthError) -> String {
        switch onEnum(of: error) {
        case .userCancelled:
            return "Login was cancelled"
        case .transactionActiveAlready:
            return "A login is already in progress"
        case .invalidState:
            return "Login could not be verified (state mismatch)"
        case .browserError(let e):
            return "Browser error: \(e.message ?? "unknown")"
        case .authorizationError(let e):
            return "Authorization error [\(e.code)]: \(e.errorDescription)"
        case .apiError(let e):
            return "API error [\(e.code)]: \(e.errorDescription) (HTTP \(e.statusCode))"
        case .network:
            return "Network error"
        case .unknown:
            return "Unknown error"
        case .idTokenValidation:
            return "ID token validation failed"
        case .dPoP:
            return "DPoP error"
        }
    }

    private var brandBadge: some View {
        Text("A0")
            .font(.title.weight(.bold))
            .foregroundStyle(Color.brandOnPrimary)
            .frame(width: 56, height: 56)
            .background(Color.brandPrimary)
            .clipShape(RoundedRectangle(cornerRadius: Sizes.cornerLarge))
    }

    @ViewBuilder
    private func optionCard(
        title: String,
        description: String,
        enabled: Bool,
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
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
    }
}
