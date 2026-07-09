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

@Serializable
object EmbeddedLogin

@Serializable
object Welcome
