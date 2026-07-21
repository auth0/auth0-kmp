package com.auth0.kmp.authentication.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The authenticated user's profile, built from the OIDC standard claims returned
 * by the `/userinfo` endpoint.
 *
 * Standard OIDC claims are exposed as typed properties. Any non-standard or
 * namespaced claim is collected into [customClaims], keyed by its claim name.
 *
 * @param sub the subject identifier — the unique, stable id of the user.
 * @param name the user's full name.
 * @param givenName the user's given (first) name.
 * @param familyName the user's family (last) name.
 * @param middleName the user's middle name.
 * @param nickname the user's casual name.
 * @param preferredUsername the name the user wishes to be referred to by.
 * @param profile a URL of the user's profile page.
 * @param picture a URL of the user's profile picture.
 * @param website a URL of the user's website.
 * @param email the user's preferred email address.
 * @param emailVerified whether the user's email address has been verified.
 * @param gender the user's gender.
 * @param birthdate the user's birthday.
 * @param zoneinfo the user's time zone.
 * @param locale the user's locale.
 * @param phoneNumber the user's preferred telephone number.
 * @param phoneNumberVerified whether the user's phone number has been verified.
 * @param address the user's postal address, keyed by address component.
 * @param updatedAt the time the user's information was last updated.
 * @param customClaims non-standard or namespaced claims not covered by a typed
 *   property, keyed by claim name.
 */
@Serializable(with = UserProfileSerializer::class)
public data class UserProfile(
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
    val address: Map<String, String>? = null,
    val updatedAt: String? = null,
    val customClaims: Map<String, JsonElement> = emptyMap(),
)

internal object UserProfileSerializer : KSerializer<UserProfile> {

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
        buildClassSerialDescriptor("com.auth0.kmp.authentication.model.UserProfile")

    override fun deserialize(decoder: Decoder): UserProfile {
        val input = decoder as? JsonDecoder
            ?: error("UserProfile can only be deserialized from JSON")
        val obj = input.decodeJsonElement().jsonObject

        fun string(key: String): String? = obj[key]?.jsonPrimitive?.content
        fun boolean(key: String): Boolean? = obj[key]?.jsonPrimitive?.booleanOrNull

        val address = obj[ADDRESS]?.jsonObject?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.content?.let { k to it }
        }?.toMap()
        val customClaims = obj.filterKeys { it !in knownKeys }

        return UserProfile(
            sub = string(SUB) ?: error("UserProfile requires a '$SUB' claim"),
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

    override fun serialize(encoder: Encoder, value: UserProfile) {
        val output = encoder as? JsonEncoder
            ?: error("UserProfile can only be serialized to JSON")
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
            value.address?.let { address ->
                put(ADDRESS, buildJsonObject { address.forEach { (k, v) -> put(k, v) } })
            }
            value.updatedAt?.let { put(UPDATED_AT, it) }
            value.customClaims.forEach { (k, v) -> put(k, v) }
        }
        output.encodeJsonElement(obj)
    }
}
