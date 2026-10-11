pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "speedbreaker"

include(":app")

// Opt-in emulator regression fixture; never part of the product/release build.
if (providers.gradleProperty("callFixture").orNull == "true") {
    include(":call-fixture")
    project(":call-fixture").projectDir = file("tools/call-fixture")
}
