pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.5.2"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
        id("com.google.devtools.ksp") version "2.2.10-2.0.2"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WakeGuard"
include(":app")
