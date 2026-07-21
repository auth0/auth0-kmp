//
//  SignupResultView.swift
//  iosApp
//
//  Confirmation screen after a successful createUser. Shows the returned
//  DatabaseUser and offers a one-tap login using the credentials just entered.
//

import SwiftUI
import Auth0

struct SignupResultView: View {

    let viewModel: AuthViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Text("Account created")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.xl)

                if let user = signedUpUser {
                    userRow("User ID", user.id)
                    if let email = user.email { userRow("Email", email) }
                    if let verified = user.emailVerified {
                        userRow("Email verified", String(describing: verified))
                    }
                    if let username = user.username { userRow("Username", username) }
                    if let name = user.name { userRow("Name", name) }
                    if let nickname = user.nickname { userRow("Nickname", nickname) }
                }

                Spacer(minLength: Spacing.md)

                Button {
                    Task { await viewModel.completeSignupLogin() }
                } label: {
                    Text("Log in")
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
        .navigationTitle("Account created")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var signedUpUser: DatabaseUser? {
        if case .success(let user) = viewModel.signupState { return user }
        return nil
    }

    @ViewBuilder
    private func userRow(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).font(.subheadline.weight(.semibold))
            Text(value).font(.body).foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
