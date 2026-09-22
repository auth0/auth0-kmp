package com.auth0.kmp.myaccount.model

/**
 * A TOTP (time-based one-time password) enrollment challenge, combining the
 * authentication method ID from the response headers with the challenge details
 * from the response body.
 *
 * @param authenticationMethodId the identifier of the pending authentication
 *   method, passed back when verifying the enrollment.
 * @param authSession the opaque session token passed back when verifying the enrollment.
 * @param barcodeUri the `otpauth://` URI to render as a QR code for an authenticator app.
 * @param manualInputCode the shared secret to enter manually when a QR code cannot be scanned.
 */
public data class TotpEnrollmentChallenge(
    val authenticationMethodId: String,
    val authSession: String,
    val barcodeUri: String,
    val manualInputCode: String,
)

/**
 * A push-notification enrollment challenge, combining the authentication method
 * ID from the response headers with the challenge details from the response body.
 *
 * @param authenticationMethodId the identifier of the pending authentication
 *   method, passed back when verifying the enrollment.
 * @param authSession the opaque session token passed back when verifying the enrollment.
 * @param barcodeUri the URI to render as a QR code for the Auth0 Guardian app to scan.
 */
public data class PushEnrollmentChallenge(
    val authenticationMethodId: String,
    val authSession: String,
    val barcodeUri: String,
)

/**
 * An email enrollment challenge, combining the authentication method ID from the
 * response headers with the challenge details from the response body.
 *
 * @param authenticationMethodId the identifier of the pending authentication
 *   method, passed back when verifying the enrollment.
 * @param authSession the opaque session token passed back when verifying the enrollment.
 */
public data class EmailEnrollmentChallenge(
    val authenticationMethodId: String,
    val authSession: String,
)

/**
 * A phone enrollment challenge, combining the authentication method ID from the
 * response headers with the challenge details from the response body.
 *
 * @param authenticationMethodId the identifier of the pending authentication
 *   method, passed back when verifying the enrollment.
 * @param authSession the opaque session token passed back when verifying the enrollment.
 */
public data class PhoneEnrollmentChallenge(
    val authenticationMethodId: String,
    val authSession: String,
)

/**
 * A recovery-code enrollment challenge, combining the authentication method ID
 * from the response headers with the challenge details from the response body.
 *
 * @param authenticationMethodId the identifier of the pending authentication
 *   method, passed back when verifying the enrollment.
 * @param authSession the opaque session token passed back when verifying the enrollment.
 * @param recoveryCode the recovery code to present to the user to store securely.
 */
public data class RecoveryCodeEnrollmentChallenge(
    val authenticationMethodId: String,
    val authSession: String,
    val recoveryCode: String,
)

/**
 * A password enrollment challenge, combining the authentication method ID from
 * the response headers with the challenge details from the response body.
 *
 * @param authenticationMethodId the identifier of the pending authentication
 *   method, passed back when verifying the enrollment.
 * @param authSession the opaque session token passed back when verifying the enrollment.
 * @param passwordPolicy the password policy the new password must satisfy.
 */
public data class PasswordEnrollmentChallenge(
    val authenticationMethodId: String,
    val authSession: String,
    val passwordPolicy: PasswordPolicy,
)

/**
 * The preferred channel used to deliver a phone one-time password.
 */
public enum class PhoneAuthenticationMethodType {
    /** Deliver the one-time password by SMS. */
    SMS,

    /** Deliver the one-time password by voice call. */
    VOICE,
}
