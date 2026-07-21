package com.auth0.kmp.sample

import kotlinx.serialization.Serializable

// Type-safe Navigation-Compose destinations. Each screen is its own @Serializable
// type, referenced directly in composable<T> { } and navController.navigate(T).
// All routes are argument-free objects: credentials flow through the shared
// AuthViewModel, not through navigation arguments.

@Serializable
object Splash

@Serializable
object Chooser

// Sub-chooser reached from "Embedded Login": password login, sign up, passkey
// sign up, and passkey login.
@Serializable
object EmbeddedMethods

@Serializable
object EmbeddedLogin

@Serializable
object Signup

// Confirmation shown after a successful createUser; renders the DatabaseUser and
// offers to log the new user in.
@Serializable
object SignupResult

@Serializable
object PasskeySignup

@Serializable
object PasskeyLogin

@Serializable
object Welcome
