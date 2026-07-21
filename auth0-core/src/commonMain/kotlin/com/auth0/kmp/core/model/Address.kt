package com.auth0.kmp.core.model

/**
 * A user's postal address, as defined by the OIDC `address` standard claim.
 *
 * A provider may return any subset of these components, so every field is
 * optional.
 *
 * @param formatted the full mailing address, formatted for display or a label.
 * @param streetAddress the street address, which may span multiple lines.
 * @param locality the city or locality.
 * @param region the state, province, prefecture, or region.
 * @param postalCode the zip or postal code.
 * @param country the country name.
 */
public data class Address(
    val formatted: String? = null,
    val streetAddress: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
)
