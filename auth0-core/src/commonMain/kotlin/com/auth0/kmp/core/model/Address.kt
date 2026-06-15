package com.auth0.kmp.core.model

/**
 * The OpenID Connect `address` claim — a structured postal address.
 *
 * Defined by OpenID Connect Core 1.0, section 5.1.1 (Address Claim). Every
 * member is optional. [formatted] is a single human-readable string; the
 * remaining members are its individual components.
 *
 * @param formatted the full mailing address, newline-separated.
 * @param streetAddress street component (house number, street name, etc.).
 * @param locality city or locality.
 * @param region state, province, prefecture, or region.
 * @param postalCode zip or postal code.
 * @param country country name.
 */
data class Address(
    val formatted: String? = null,
    val streetAddress: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
)
