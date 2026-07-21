//
//  PasskeyCeremony.swift
//  iosApp
//
//  Runs the on-device WebAuthn ceremony with Apple's AuthenticationServices and
//  adapts the result into the SDK's `PublicKeyCredentials`. The SDK is HTTP-only
//  for passkeys (it never touches platform WebAuthn), so this app-side glue is
//  what turns an Auth0 challenge into a platform credential and back.
//

import Foundation
import AuthenticationServices
import UIKit
import Auth0

/// Thrown when the platform ceremony cannot produce a credential (user cancelled
/// Face ID / Touch ID, no passkey available, missing Associated Domains, etc.).
/// Conforms to `Auth0Error` so it can flow through the view model's failure state
/// exactly like an SDK error, and carries a human-readable message for the UI.
final class CeremonyError: Auth0Error, Error {
    let message: String
    init(_ message: String) { self.message = message }
}

/// Namespace for the two ceremonies. Each entry point is `@MainActor` because it
/// reads the key window and drives UIKit; both are `async throws` so the caller
/// can `await` the delegate-based Apple API as a straight-line suspend function.
enum PasskeyCeremony {

    /// Registration ceremony (passkey sign-up): asks the platform to create a new
    /// passkey for the relying party in the signup challenge, then packages the
    /// attestation into `PublicKeyCredentials` for `loginWithPasskey`.
    @MainActor
    static func register(options: AuthnParamsPublicKey) async throws -> PublicKeyCredentials {
        let coordinator = PasskeyCoordinator(anchor: keyWindow())
        return try await coordinator.register(options: options)
    }

    /// Assertion ceremony (passkey login): asks the platform to sign the login
    /// challenge with an existing passkey, then packages the assertion into
    /// `PublicKeyCredentials` for `loginWithPasskey`.
    @MainActor
    static func authenticate(options: AuthParamsPublicKey) async throws -> PublicKeyCredentials {
        let coordinator = PasskeyCoordinator(anchor: keyWindow())
        return try await coordinator.authenticate(options: options)
    }

    /// The window Apple anchors its passkey sheet to. We prefer the foreground
    /// active scene's key window; if none is key yet we bind a fresh window to a
    /// window scene (the bare `ASPresentationAnchor()` init is deprecated in iOS 26).
    /// A ceremony is only started from an on-screen view, so a scene always exists.
    @MainActor
    private static func keyWindow() -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        if let keyWindow = scene?.windows.first(where: { $0.isKeyWindow }) {
            return keyWindow
        }
        return UIWindow(windowScene: scene!)
    }
}

/// One-shot coordinator that bridges Apple's delegate callbacks to a Swift
/// `async` continuation. A fresh instance is created per ceremony; it keeps
/// itself alive across the async gap (the controller holds its delegate only
/// weakly) and releases that self-reference the moment a callback resumes.
private final class PasskeyCoordinator: NSObject,
    ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {

    private let anchor: ASPresentationAnchor
    private var continuation: CheckedContinuation<PublicKeyCredentials, Error>?
    private var selfRetain: PasskeyCoordinator?

    init(anchor: ASPresentationAnchor) {
        self.anchor = anchor
    }

    func register(options: AuthnParamsPublicKey) async throws -> PublicKeyCredentials {
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(
            relyingPartyIdentifier: options.relyingParty.id
        )
        // The SDK hands us base64url strings (the WebAuthn wire format); Apple
        // wants raw bytes, so decode challenge + user id before building the request.
        guard let challenge = Data(base64url: options.challenge),
              let userID = Data(base64url: options.user.id) else {
            throw CeremonyError("Challenge or user id was not valid base64url")
        }
        let request = provider.createCredentialRegistrationRequest(
            challenge: challenge,
            name: options.user.name,
            userID: userID
        )
        request.userVerificationPreference =
            ASAuthorizationPublicKeyCredentialUserVerificationPreference(
                rawValue: options.authenticatorSelection.userVerification
            )
        return try await perform(request: request)
    }

    func authenticate(options: AuthParamsPublicKey) async throws -> PublicKeyCredentials {
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(
            relyingPartyIdentifier: options.rpId
        )
        guard let challenge = Data(base64url: options.challenge) else {
            throw CeremonyError("Challenge was not valid base64url")
        }
        let request = provider.createCredentialAssertionRequest(challenge: challenge)
        request.userVerificationPreference =
            ASAuthorizationPublicKeyCredentialUserVerificationPreference(
                rawValue: options.userVerification
            )
        return try await perform(request: request)
    }

    /// Starts the Apple sheet and suspends until a delegate callback fires. We set
    /// `selfRetain` before `performRequests()` so this object outlives the async
    /// gap even though `ASAuthorizationController` references its delegate weakly.
    private func perform(request: ASAuthorizationRequest) async throws -> PublicKeyCredentials {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            self.selfRetain = self
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    // MARK: ASAuthorizationControllerPresentationContextProviding

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        anchor
    }

    // MARK: ASAuthorizationControllerDelegate

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        defer { selfRetain = nil }
        let result: Swift.Result<PublicKeyCredentials, Error>
        switch authorization.credential {
        case let registration as ASAuthorizationPlatformPublicKeyCredentialRegistration:
            result = .success(Self.credentials(from: registration))
        case let assertion as ASAuthorizationPlatformPublicKeyCredentialAssertion:
            result = .success(Self.credentials(from: assertion))
        default:
            result = .failure(CeremonyError("Unexpected credential type from the platform"))
        }
        continuation?.resume(with: result)
        continuation = nil
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        defer { selfRetain = nil }
        // ASAuthorizationError.canceled is the user dismissing the sheet; surface a
        // friendly message rather than the raw NSError for every failure kind.
        let message: String
        if let authError = error as? ASAuthorizationError, authError.code == .canceled {
            message = "Passkey request was cancelled"
        } else {
            message = error.localizedDescription
        }
        continuation?.resume(throwing: CeremonyError(message))
        continuation = nil
    }

    // MARK: Apple result → SDK model

    private static func credentials(
        from registration: ASAuthorizationPlatformPublicKeyCredentialRegistration
    ) -> PublicKeyCredentials {
        let id = registration.credentialID.base64urlEncodedString()
        // Kotlin default arguments do NOT cross into Swift, so every field of the
        // data class must be supplied explicitly — hence the trailing nils.
        let response = AuthenticatorResponse(
            clientDataJSON: registration.rawClientDataJSON.base64urlEncodedString(),
            attestationObject: registration.rawAttestationObject?.base64urlEncodedString(),
            authenticatorData: nil,
            signature: nil,
            userHandle: nil,
            transports: nil
        )
        return PublicKeyCredentials(
            id: id,
            rawId: id,
            type: "public-key",
            response: response,
            authenticatorAttachment: "platform",
            clientExtensionResults: nil
        )
    }

    private static func credentials(
        from assertion: ASAuthorizationPlatformPublicKeyCredentialAssertion
    ) -> PublicKeyCredentials {
        let id = assertion.credentialID.base64urlEncodedString()
        let response = AuthenticatorResponse(
            clientDataJSON: assertion.rawClientDataJSON.base64urlEncodedString(),
            attestationObject: nil,
            authenticatorData: assertion.rawAuthenticatorData.base64urlEncodedString(),
            signature: assertion.signature.base64urlEncodedString(),
            userHandle: assertion.userID.base64urlEncodedString(),
            transports: nil
        )
        return PublicKeyCredentials(
            id: id,
            rawId: id,
            type: "public-key",
            response: response,
            authenticatorAttachment: "platform",
            clientExtensionResults: nil
        )
    }
}

// MARK: - base64url <-> Data

private extension Data {
    /// Decodes an unpadded base64url string (the WebAuthn wire format) into bytes.
    init?(base64url: String) {
        var base64 = base64url
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        // Standard base64 requires the length to be a multiple of 4; base64url
        // drops the '=' padding, so add it back before decoding.
        let remainder = base64.count % 4
        if remainder > 0 {
            base64.append(String(repeating: "=", count: 4 - remainder))
        }
        self.init(base64Encoded: base64)
    }

    /// Encodes bytes as an unpadded base64url string for the WebAuthn wire format.
    func base64urlEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
