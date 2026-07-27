import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.maven.tasks.GenerateMavenPom

plugins {
    id("com.vanniktech.maven.publish")
}

group = providers.gradleProperty("GROUP").get()
// Single source of truth: the root `.version` file (same file auth0-core's
// generateVersionFile reads into SDK_VERSION), so the published coordinate and
// the SDK's reported version can never drift.
version = rootDir.resolve(".version").readLines()
    .firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    ?: error("Version file is empty or missing: ${rootDir.resolve(".version").absolutePath}")

mavenPublishing {

    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set(project.name)
    }
}

// Preflight gate: verify every generated POM before it can be uploaded. Runs
// automatically before any publish task (generatePom -> preflight -> publish),
// so a malformed POM fails the build locally/in CI instead of half-uploading.
val preflightPublish = tasks.register("preflightPublish") {
    group = "verification"
    description = "Verifies generated Maven POMs before publishing."

    // Capture config-time values so the execution action never touches `project`
    // (keeps the task configuration-cache compatible).
    val expectedGroup = providers.gradleProperty("GROUP").get()
    val expectedVersion = version.toString()
    val expectedArtifactBase = project.name
    val publicationsDir = layout.buildDirectory.dir("publications")

    dependsOn(tasks.withType(GenerateMavenPom::class.java))
    outputs.upToDateWhen { false } // a safety gate must always run, never be skipped

    doLast {
        val dir = publicationsDir.get().asFile
        val poms = dir.walkTopDown().filter { it.name == "pom-default.xml" }.toList()
        if (poms.isEmpty()) error("preflightPublish: no POMs under $dir — did POM generation run?")

        val problems = mutableListOf<String>()
        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()

        fun childText(parent: org.w3c.dom.Element, tag: String): String? {
            val kids = parent.childNodes
            for (i in 0 until kids.length) {
                val n = kids.item(i)
                if (n is org.w3c.dom.Element && n.tagName == tag) return n.textContent?.trim()
            }
            return null
        }
        fun directChildren(parent: org.w3c.dom.Element, tag: String): List<org.w3c.dom.Element> {
            val out = mutableListOf<org.w3c.dom.Element>()
            val kids = parent.childNodes
            for (i in 0 until kids.length) {
                val n = kids.item(i)
                if (n is org.w3c.dom.Element && n.tagName == tag) out += n
            }
            return out
        }

        poms.forEach { pom ->
            val root = dbf.newDocumentBuilder().parse(pom).documentElement
            val target = pom.parentFile.name // e.g. kotlinMultiplatform, android, iosArm64
            fun bad(msg: String) = problems.add("[$expectedArtifactBase/$target] $msg")

            val gid = childText(root, "groupId")
            if (gid != expectedGroup) bad("groupId=$gid, expected $expectedGroup")

            val ver = childText(root, "version")
            if (ver != expectedVersion) bad("version=$ver, expected $expectedVersion")

            val artifact = childText(root, "artifactId")
            if (artifact == null || !artifact.startsWith(expectedArtifactBase)) {
                bad("artifactId=$artifact, expected to start with $expectedArtifactBase")
            }

            if (childText(root, "name").isNullOrEmpty()) bad("missing <name>")
            if (childText(root, "description").isNullOrEmpty()) bad("missing <description>")
            if (childText(root, "url").isNullOrEmpty()) bad("missing <url>")

            val licenses = directChildren(root, "licenses").flatMap { directChildren(it, "license") }
            when (licenses.size) {
                1 -> licenses.single().let {
                    if (childText(it, "name").isNullOrEmpty()) bad("license missing <name>")
                    if (childText(it, "url").isNullOrEmpty()) bad("license missing <url>")
                }
                else -> bad("expected exactly 1 <license>, found ${licenses.size}")
            }

            val developers = directChildren(root, "developers").flatMap { directChildren(it, "developer") }
            if (developers.size != 1) bad("expected exactly 1 <developer>, found ${developers.size}")

            val scms = directChildren(root, "scm")
            when (scms.size) {
                1 -> scms.single().let {
                    if (childText(it, "connection").isNullOrEmpty()) bad("scm missing <connection>")
                    if (childText(it, "developerConnection").isNullOrEmpty()) bad("scm missing <developerConnection>")
                    if (childText(it, "url").isNullOrEmpty()) bad("scm missing <url>")
                }
                else -> bad("expected exactly 1 <scm>, found ${scms.size}")
            }

            if (problems.none { it.startsWith("[$expectedArtifactBase/$target]") }) {
                logger.lifecycle("preflightPublish ✓ $gid:$artifact:$ver ($target)")
            }
        }

        if (problems.isNotEmpty()) {
            error("preflightPublish failed:\n" + problems.joinToString("\n") { "  - $it" })
        }
    }
}

tasks.withType(AbstractPublishToMaven::class.java).configureEach {
    dependsOn(preflightPublish)
}
