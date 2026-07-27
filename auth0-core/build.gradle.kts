import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("auth0.publish")
}

val generateVersionFile by tasks.registering {
    val versionFile = rootProject.file(".version")
    val outputDir = layout.buildDirectory.dir("generated/version/commonMain/kotlin")
    inputs.file(versionFile)
    outputs.dir(outputDir)
    doLast {
        val version = versionFile.readLines().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("Version file is empty or missing: ${versionFile.absolutePath}")
        val pkgDir = outputDir.get().dir("com/auth0/kmp/core").asFile
        pkgDir.mkdirs()
        pkgDir.resolve("Version.kt").writeText(
            """
            package com.auth0.kmp.core

            internal const val SDK_VERSION: String = "$version"
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    compilerOptions {
        optIn.add("com.auth0.kmp.core.annotation.InternalAuth0Api")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Auth0Core"
            isStatic = true
        }
    }

    android {
        namespace = "com.auth0.kmp.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        withHostTest { }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateVersionFile)
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.startup.runtime)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
