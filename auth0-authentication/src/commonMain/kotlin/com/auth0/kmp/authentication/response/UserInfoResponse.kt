package com.auth0.kmp.authentication.response

import com.auth0.kmp.core.annotation.InternalAuth0Api
import com.auth0.kmp.core.model.Address
import com.auth0.kmp.core.model.UserInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable(with = UserInfoResponseSerializer::class)
@InternalAuth0Api
public data class UserInfoResponse(
    val sub: String,
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val nickname: String? = null,
    val preferredUsername: String? = null,
    val profile: String? = null,
    val picture: String? = null,
    val website: String? = null,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val gender: String? = null,
    val birthdate: String? = null,
    val zoneinfo: String? = null,
    val locale: String? = null,
    val phoneNumber: String? = null,
    val phoneNumberVerified: Boolean? = null,
    val address: AddressResponse? = null,
    val updatedAt: String? = null,
    val customClaims: Map<String, JsonElement> = emptyMap(),
)

@Serializable
@InternalAuth0Api
public data class AddressResponse(
    @SerialName("formatted") val formatted: String? = null,
    @SerialName("street_address") val streetAddress: String? = null,
    @SerialName("locality") val locality: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("country") val country: String? = null,
)

@InternalAuth0Api
public fun AddressResponse.toAddress(): Address =
    Address(
        formatted = formatted,
        streetAddress = streetAddress,
        locality = locality,
        region = region,
        postalCode = postalCode,
        country = country,
    )

@InternalAuth0Api
public fun UserInfoResponse.toUserInfo(): UserInfo =
    UserInfo(
        sub = sub,
        name = name,
        givenName = givenName,
        familyName = familyName,
        middleName = middleName,
        nickname = nickname,
        preferredUsername = preferredUsername,
        profile = profile,
        picture = picture,
        website = website,
        email = email,
        emailVerified = emailVerified,
        gender = gender,
        birthdate = birthdate,
        zoneinfo = zoneinfo,
        locale = locale,
        phoneNumber = phoneNumber,
        phoneNumberVerified = phoneNumberVerified,
        address = address?.toAddress(),
        updatedAt = updatedAt?.let(::parseUpdatedAt),
        customClaims = customClaims,
    )

/**
 * Parses an OIDC `updated_at` wire value into an [Instant].
 *
 * The value may be a numeric epoch (seconds) or an ISO-8601 timestamp. An
 * unparseable value yields `null` rather than throwing.
 */
private fun parseUpdatedAt(raw: String): Instant? {
    raw.toDoubleOrNull()?.let { return Instant.fromEpochSeconds(it.toLong(), 0) }
    return runCatching { Instant.parse(raw) }.getOrNull()
}

@OptIn(InternalAuth0Api::class)
internal object UserInfoResponseSerializer : KSerializer<UserInfoResponse> {

    private const val SUB = "sub"
    private const val NAME = "name"
    private const val GIVEN_NAME = "given_name"
    private const val FAMILY_NAME = "family_name"
    private const val MIDDLE_NAME = "middle_name"
    private const val NICKNAME = "nickname"
    private const val PREFERRED_USERNAME = "preferred_username"
    private const val PROFILE = "profile"
    private const val PICTURE = "picture"
    private const val WEBSITE = "website"
    private const val EMAIL = "email"
    private const val EMAIL_VERIFIED = "email_verified"
    private const val GENDER = "gender"
    private const val BIRTHDATE = "birthdate"
    private const val ZONEINFO = "zoneinfo"
    private const val LOCALE = "locale"
    private const val PHONE_NUMBER = "phone_number"
    private const val PHONE_NUMBER_VERIFIED = "phone_number_verified"
    private const val ADDRESS = "address"
    private const val UPDATED_AT = "updated_at"

    private val knownKeys = setOf(
        SUB, NAME, GIVEN_NAME, FAMILY_NAME, MIDDLE_NAME, NICKNAME, PREFERRED_USERNAME,
        PROFILE, PICTURE, WEBSITE, EMAIL, EMAIL_VERIFIED, GENDER, BIRTHDATE, ZONEINFO,
        LOCALE, PHONE_NUMBER, PHONE_NUMBER_VERIFIED, ADDRESS, UPDATED_AT,
    )

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.auth0.kmp.authentication.response.UserInfoResponse")

    override fun deserialize(decoder: Decoder): UserInfoResponse {
        val input = decoder as? JsonDecoder
            ?: error("UserInfoResponse can only be deserialized from JSON")
        val obj = input.decodeJsonElement().jsonObject

        fun string(key: String): String? =
            (obj[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        fun boolean(key: String): Boolean? = obj[key]?.jsonPrimitive?.booleanOrNull

        val address = obj[ADDRESS]?.let {
            input.json.decodeFromJsonElement(AddressResponse.serializer(), it)
        }
        val customClaims = obj.filterKeys { it !in knownKeys }

        return UserInfoResponse(
            sub = string(SUB) ?: error("UserInfoResponse requires a '$SUB' claim"),
            name = string(NAME),
            givenName = string(GIVEN_NAME),
            familyName = string(FAMILY_NAME),
            middleName = string(MIDDLE_NAME),
            nickname = string(NICKNAME),
            preferredUsername = string(PREFERRED_USERNAME),
            profile = string(PROFILE),
            picture = string(PICTURE),
            website = string(WEBSITE),
            email = string(EMAIL),
            emailVerified = boolean(EMAIL_VERIFIED),
            gender = string(GENDER),
            birthdate = string(BIRTHDATE),
            zoneinfo = string(ZONEINFO),
            locale = string(LOCALE),
            phoneNumber = string(PHONE_NUMBER),
            phoneNumberVerified = boolean(PHONE_NUMBER_VERIFIED),
            address = address,
            updatedAt = string(UPDATED_AT),
            customClaims = customClaims,
        )
    }

    override fun serialize(encoder: Encoder, value: UserInfoResponse) {
        val output = encoder as? JsonEncoder
            ?: error("UserInfoResponse can only be serialized to JSON")
        val obj = buildJsonObject {
            put(SUB, value.sub)
            value.name?.let { put(NAME, it) }
            value.givenName?.let { put(GIVEN_NAME, it) }
            value.familyName?.let { put(FAMILY_NAME, it) }
            value.middleName?.let { put(MIDDLE_NAME, it) }
            value.nickname?.let { put(NICKNAME, it) }
            value.preferredUsername?.let { put(PREFERRED_USERNAME, it) }
            value.profile?.let { put(PROFILE, it) }
            value.picture?.let { put(PICTURE, it) }
            value.website?.let { put(WEBSITE, it) }
            value.email?.let { put(EMAIL, it) }
            value.emailVerified?.let { put(EMAIL_VERIFIED, it) }
            value.gender?.let { put(GENDER, it) }
            value.birthdate?.let { put(BIRTHDATE, it) }
            value.zoneinfo?.let { put(ZONEINFO, it) }
            value.locale?.let { put(LOCALE, it) }
            value.phoneNumber?.let { put(PHONE_NUMBER, it) }
            value.phoneNumberVerified?.let { put(PHONE_NUMBER_VERIFIED, it) }
            value.address?.let {
                put(ADDRESS, output.json.encodeToJsonElement(AddressResponse.serializer(), it))
            }
            value.updatedAt?.let { put(UPDATED_AT, it) }
            value.customClaims.forEach { (k, v) -> put(k, v) }
        }
        output.encodeJsonElement(obj)
    }
}
