plugins {
    `kotlin-dsl`
}

dependencies {
    // compileOnly (not implementation): the convention plugin compiles against the
    // vanniktech DSL, but build-logic must NOT re-export the plugin onto each
    // consuming module — that second copy is what put MavenCentralBuildService on a
    // different classloader per module. The single shared copy comes from the root
    // build's `apply false` declaration instead.
    //
    // Version comes from the shared root catalog (see build-logic/settings.gradle.kts)
    // so it is declared once and can never drift from the root's `apply false` copy.
    compileOnly("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:${libs.versions.vanniktech.publish.get()}")
}
