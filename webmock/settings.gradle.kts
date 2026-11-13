pluginManagement {
    // NOTE: no google() repo — this is a wasmJs-only project with no Android
    // artifacts, and Gradle gets "Connection reset" for dl.google.com
    // through the sandbox egress proxy (curl works, Java doesn't).
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
rootProject.name = "moonstream-webmock"
include(":composeApp")
