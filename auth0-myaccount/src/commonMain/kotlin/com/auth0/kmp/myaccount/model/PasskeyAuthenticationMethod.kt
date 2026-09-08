package com.auth0.kmp.myaccount.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A passkey authentication method.
 *
 * @param id unique identifier of the authentication method.
 * @param type type of the authentication method. Equals to `passkey`.
 * @param userIdentityId unique identifier of the user identity linked with the authentication method.
 * @param userAgent the user agent of the browser or device used to enroll the passkey.
 * @param credential details of the passkey credential.
 * @param createdAt creation date of the authentication method.
 * @param aaguid Authenticator Attestation GUID for the passkey provider.
 * @param relyingPartyId relying party Id for the domain.
 * @param usage the ways this authentication method may be used.
 */
@OptIn(ExperimentalTime::class)
public data class PasskeyAuthenticationMethod(
    val id: String,
    val type: String,
    val userIdentityId: String?,
    val userAgent: String?,
    val credential: PasskeyCredential,
    val createdAt: Instant,
    val aaguid: String,
    val relyingPartyId: String,
    val usage: List<String>,
)

/**
 * A passkey credential.
 *
 * @param keyId unique identifier of the passkey credential.
 * @param publicKey public key of the passkey credential.
 * @param userHandle user handle associated with the passkey credential.
 * @param deviceType kind of device the passkey credential is stored on as defined by backup eligibility.
 * @param isBackedUp whether the passkey credential was backed up.
 * @param transports the transports the authenticator supports.
 */
public data class PasskeyCredential(
    val keyId: String,
    val publicKey: String,
    val userHandle: String,
    val deviceType: PasskeyDeviceType,
    val isBackedUp: Boolean,
    val transports: List<String>? = null,
)

/**
 * Kind of device the passkey is stored on as defined by backup eligibility.
 */
public enum class PasskeyDeviceType {
    /** Passkey that cannot be backed up and synced to another device. */
    SINGLE_DEVICE,

    /** Passkey that can be backed up and synced to another device, when enabled by the user. */
    MULTI_DEVICE,
}
