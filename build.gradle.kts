plugins {
    // declared here (apply false) so the plugin classes load once and are
    // shared across subprojects' classloaders
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}