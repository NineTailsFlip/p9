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

rootProject.name = "p9"

// The Nine suite's shared modules come from the nine-core submodule (git clone --recursive, or
// git submodule update --init), unchanged from upstream ~timofurrer/nine.
include(":core")
project(":core").projectDir = file("nine-core/core")
include(":contacts")
project(":contacts").projectDir = file("nine-core/contacts")

include(":app")
