package com.auth0.kmp.myaccount.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.myaccount.model.EmailEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PasswordComplexity
import com.auth0.kmp.myaccount.model.PasswordDictionary
import com.auth0.kmp.myaccount.model.PasswordEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PasswordHistory
import com.auth0.kmp.myaccount.model.PasswordPolicy
import com.auth0.kmp.myaccount.model.PasswordProfileData
import com.auth0.kmp.myaccount.model.PhoneEnrollmentChallenge
import com.auth0.kmp.myaccount.model.PushEnrollmentChallenge
import com.auth0.kmp.myaccount.model.RecoveryCodeEnrollmentChallenge
import com.auth0.kmp.myaccount.model.TotpEnrollmentChallenge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@InternalAuth0Api
public data class TotpEnrollmentChallengeResponse(
    @SerialName("auth_session") val authSession: String,
    @SerialName("barcode_uri") val barcodeUri: String,
    @SerialName("manual_input_code") val manualInputCode: String,
)

@Serializable
@InternalAuth0Api
public data class PushEnrollmentChallengeResponse(
    @SerialName("auth_session") val authSession: String,
    @SerialName("barcode_uri") val barcodeUri: String,
)

@Serializable
@InternalAuth0Api
public data class EmailEnrollmentChallengeResponse(
    @SerialName("auth_session") val authSession: String,
)

@Serializable
@InternalAuth0Api
public data class PhoneEnrollmentChallengeResponse(
    @SerialName("auth_session") val authSession: String,
)

@Serializable
@InternalAuth0Api
public data class RecoveryCodeEnrollmentChallengeResponse(
    @SerialName("auth_session") val authSession: String,
    @SerialName("recovery_code") val recoveryCode: String,
)

@Serializable
@InternalAuth0Api
public data class PasswordEnrollmentChallengeResponse(
    @SerialName("auth_session") val authSession: String,
    @SerialName("policy") val policy: PasswordPolicyResponse,
)

@Serializable
@InternalAuth0Api
public data class PasswordPolicyResponse(
    @SerialName("complexity") val complexity: PasswordComplexityResponse,
    @SerialName("profile_data") val profileData: PasswordProfileDataResponse,
    @SerialName("history") val history: PasswordHistoryResponse,
    @SerialName("dictionary") val dictionary: PasswordDictionaryResponse,
)

@Serializable
@InternalAuth0Api
public data class PasswordComplexityResponse(
    @SerialName("min_length") val minLength: Int,
    @SerialName("character_types") val characterTypes: List<String> = emptyList(),
    @SerialName("character_type_rule") val characterTypeRule: String,
    @SerialName("identical_characters") val identicalCharacters: String,
    @SerialName("sequential_characters") val sequentialCharacters: String,
    @SerialName("max_length_exceeded") val maxLengthExceeded: String,
)

@Serializable
@InternalAuth0Api
public data class PasswordProfileDataResponse(
    @SerialName("active") val active: Boolean,
    @SerialName("blocked_fields") val blockedFields: List<String> = emptyList(),
)

@Serializable
@InternalAuth0Api
public data class PasswordHistoryResponse(
    @SerialName("active") val active: Boolean,
    @SerialName("size") val size: Int,
)

@Serializable
@InternalAuth0Api
public data class PasswordDictionaryResponse(
    @SerialName("active") val active: Boolean,
    @SerialName("default") val default: String,
)

@InternalAuth0Api
public fun TotpEnrollmentChallengeResponse.toTotpEnrollmentChallenge(
    authenticationMethodId: String,
): TotpEnrollmentChallenge =
    TotpEnrollmentChallenge(
        authenticationMethodId = authenticationMethodId,
        authSession = authSession,
        barcodeUri = barcodeUri,
        manualInputCode = manualInputCode,
    )

@InternalAuth0Api
public fun PushEnrollmentChallengeResponse.toPushEnrollmentChallenge(
    authenticationMethodId: String,
): PushEnrollmentChallenge =
    PushEnrollmentChallenge(
        authenticationMethodId = authenticationMethodId,
        authSession = authSession,
        barcodeUri = barcodeUri,
    )

@InternalAuth0Api
public fun EmailEnrollmentChallengeResponse.toEmailEnrollmentChallenge(
    authenticationMethodId: String,
): EmailEnrollmentChallenge =
    EmailEnrollmentChallenge(
        authenticationMethodId = authenticationMethodId,
        authSession = authSession,
    )

@InternalAuth0Api
public fun PhoneEnrollmentChallengeResponse.toPhoneEnrollmentChallenge(
    authenticationMethodId: String,
): PhoneEnrollmentChallenge =
    PhoneEnrollmentChallenge(
        authenticationMethodId = authenticationMethodId,
        authSession = authSession,
    )

@InternalAuth0Api
public fun RecoveryCodeEnrollmentChallengeResponse.toRecoveryCodeEnrollmentChallenge(
    authenticationMethodId: String,
): RecoveryCodeEnrollmentChallenge =
    RecoveryCodeEnrollmentChallenge(
        authenticationMethodId = authenticationMethodId,
        authSession = authSession,
        recoveryCode = recoveryCode,
    )

@InternalAuth0Api
public fun PasswordEnrollmentChallengeResponse.toPasswordEnrollmentChallenge(
    authenticationMethodId: String,
): PasswordEnrollmentChallenge =
    PasswordEnrollmentChallenge(
        authenticationMethodId = authenticationMethodId,
        authSession = authSession,
        passwordPolicy = policy.toPasswordPolicy(),
    )

@InternalAuth0Api
public fun PasswordPolicyResponse.toPasswordPolicy(): PasswordPolicy =
    PasswordPolicy(
        complexity = complexity.toPasswordComplexity(),
        profileData = profileData.toPasswordProfileData(),
        history = history.toPasswordHistory(),
        dictionary = dictionary.toPasswordDictionary(),
    )

@InternalAuth0Api
public fun PasswordComplexityResponse.toPasswordComplexity(): PasswordComplexity =
    PasswordComplexity(
        minLength = minLength,
        characterTypes = characterTypes,
        characterTypeRule = characterTypeRule,
        identicalCharacters = identicalCharacters,
        sequentialCharacters = sequentialCharacters,
        maxLengthExceeded = maxLengthExceeded,
    )

@InternalAuth0Api
public fun PasswordProfileDataResponse.toPasswordProfileData(): PasswordProfileData =
    PasswordProfileData(
        active = active,
        blockedFields = blockedFields,
    )

@InternalAuth0Api
public fun PasswordHistoryResponse.toPasswordHistory(): PasswordHistory =
    PasswordHistory(
        active = active,
        size = size,
    )

@InternalAuth0Api
public fun PasswordDictionaryResponse.toPasswordDictionary(): PasswordDictionary =
    PasswordDictionary(
        active = active,
        default = default,
    )
