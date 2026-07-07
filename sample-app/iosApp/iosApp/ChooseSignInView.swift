//
//  ChooseSignInView.swift
//  iosApp
//

import SwiftUI

struct ChooseSignInView: View {
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

            Spacer()
        }
        .padding(.horizontal, Spacing.lg)
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
