plugins {
    // declared here (apply false) so the plugin classes load once and are
    // shared across subprojects' classloaders
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
    // vanniktech's MavenCentralBuildService is a shared build service; Gradle keys
    // build services by classloader scope. Loading it once here (apply false) keeps
    // every module's `id("auth0.publish")` on ONE classloader, so the sibling
    // modules share one service instance instead of colliding.
    alias(libs.plugins.vanniktechPublish) apply false
    // Loaded once here (apply false) for the same classloader reason as above:
    // `auth0.publish` applies Dokka in every publishable module, and vanniktech
    // detects it to swap the empty -javadoc.jar for generated Dokka HTML.
    alias(libs.plugins.dokka) apply false
}