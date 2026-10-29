pluginManagement {
    repositories {
        // Local sandbox mirror (offline builds). Real repos kept for CI / future.
        maven { url = uri("/home/hatch/localrepo") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("/home/hatch/localrepo") }
        google()
        mavenCentral()
    }
}
rootProject.name = "MoonStream"
include(":app")
