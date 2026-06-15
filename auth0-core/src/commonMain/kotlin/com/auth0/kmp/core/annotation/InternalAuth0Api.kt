package com.auth0.kmp.core.annotation

/**
 * Marks declarations that are internal to the Auth0 SDK implementation.
 *
 * APIs annotated with this are visible across SDK modules but are not part of
 * the public, supported surface. They may change or be removed without notice,
 * and must not be used by SDK consumers.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal Auth0 API and must not be used outside the SDK."
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR
)
annotation class InternalAuth0Api
