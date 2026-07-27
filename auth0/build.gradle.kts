import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.skie)
    id("auth0.publish")
}

skie {
    analytics {
        enabled.set(false)
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Auth0"
            isStatic = true
            export(project(":auth0-core"))
            export(project(":auth0-authentication"))
            export(project(":auth0-webauth"))
            export(project(":auth0-credentials"))
        }
    }

    android {
        namespace = "com.auth0.kmp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }

        withHostTest { }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":auth0-core"))
            api(project(":auth0-authentication"))
            api(project(":auth0-webauth"))
            api(project(":auth0-credentials"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
