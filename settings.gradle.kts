pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Chronosphere"

include(
    ":app",
    ":core:simulation",
    ":core:worldgen",
    ":core:civilization",
    ":core:people",
    ":core:economy",
    ":core:society",
    ":core:history",
    ":core:textgen",
    ":core:storage",
    ":core:adult-contracts",
    ":feature:map",
)

// The adult implementation is optional. Core and the base app remain compilable without it.
if (file("feature/adult").exists()) {
    include(":feature:adult")
}
