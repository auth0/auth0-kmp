package com.auth0.kmp.myaccount.error

/**
 * A single validation failure reported in a My Account API error response.
 *
 * @param detail a human-readable explanation of what was invalid.
 * @param pointer a JSON pointer to the offending field in the request body.
 * @param source the part of the request the failure originated from.
 * @param field the name of the offending field.
 */
public data class ValidationError(
    val detail: String,
    val pointer: String? = null,
    val source: String? = null,
    val field: String? = null,
)
