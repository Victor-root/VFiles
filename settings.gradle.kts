pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // Bundled offline Maven repository (see local-maven/README.md). dav4jvm is served from here
        // instead of JitPack, which no longer provides an artifact for the pinned commit.
        maven {
            url = uri("local-maven")
            content { includeGroup("com.github.bitfireAT") }
        }
        maven {
            url = uri("https://jitpack.io")
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

rootProject.name = "VFiles"
include(":app")
include(":baselineprofile")
