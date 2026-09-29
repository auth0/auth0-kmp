package com.auth0.kmp.authentication.model

/**
 * Delivery method for a phone-number OTP challenge in the DB-connection passwordless flow.
 *
 * Sent as the `delivery_method` parameter to `POST /otp/challenge`.
 *
 * @property value the wire value sent to the server.
 */
public enum class DeliveryMethod(public val value: String) {

    /** Deliver the one-time code via SMS (default). */
    TEXT("text"),

    /** Deliver the one-time code via a voice call. */
    VOICE("voice"),
}
