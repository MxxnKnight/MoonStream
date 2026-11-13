plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
            }
        }
    }
}

// Pin the generated Res class package: the default is derived from the
// Gradle module name (composeapp.generated.resources), not the Kotlin
// package, so imports of com.moon.webmock.generated.resources.Res would
// fail without this.
compose {
    resources {
        packageOfResClass = "com.moon.webmock.generated.resources"
    }
}
