dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // Reuse the root version catalog so the vanniktech plugin version is declared
    // exactly once. build-logic and the root build MUST load the same version to
    // keep one MavenCentralBuildService on one classloader (see auth0.publish).
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
