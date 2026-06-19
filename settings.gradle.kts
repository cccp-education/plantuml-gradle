pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
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
