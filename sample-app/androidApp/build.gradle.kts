import java.util.Properties

plugins {
    // AGP 9.0+ provides built-in Kotlin support; no kotlin.android plugin needed.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Type-safe Navigation-Compose routes are @Serializable types, which require
    // the kotlinx-serialization compiler plugin on this module.
    alias(libs.plugins.kotlinSerialization)
}

// Auth0 tenant config is read from the git-ignored root local.properties so no
// secrets land in source control. Missing values fall back to empty strings.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.auth0.kmp.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.auth0.kmp.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"

        buildConfigField("String", "AUTH0_DOMAIN", "\"${localProps.getProperty("auth0.domain", "")}\"")
        buildConfigField("String", "AUTH0_CLIENT_ID", "\"${localProps.getProperty("auth0.clientId", "")}\"")

        manifestPlaceholders["auth0Scheme"] = applicationId as String
        manifestPlaceholders["auth0Domain"] = localProps.getProperty("auth0.domain", "")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

dependencies {
    implementation(project(":auth0-kmp"))

    implementation(libs.kotlinx.coroutines.core)

    // Sample-app-only UI dependencies. Declared as plain coordinates here (not in
    // the shared root version catalog) so the SDK's dependency manifest stays free
    // of app-only concerns like Compose and Navigation.
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    // Provides the @Serializable annotation used by the type-safe nav routes.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
