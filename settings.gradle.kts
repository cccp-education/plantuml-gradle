pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
// CNV-16 — JitPack mirror for the education.cccp.build.* conventions
// (bypasses Sonatype / Gradle Plugin Portal). The JitPack tag is pinned while
// the canonical plugin declarations may stay on the released version.
pluginManagement.repositories.maven("https://jitpack.io")
pluginManagement.resolutionStrategy.eachPlugin {
    if (requested.id.id.startsWith("education.cccp.build.")) {
        useModule("com.github.cccp-education.conventions-gradle:conventions-plugin:0.0.15")
    }
}


dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "plantuml-gradle"

// Import the workspace BOM
includeBuild("../workspace-bom")

include("plantuml-plugin")
