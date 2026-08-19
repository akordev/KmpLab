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
    }
}

rootProject.name = "KmpLab"

// The shared Kotlin Multiplatform network layer. Internal to the SDK — never
// exposed to SDK consumers.
include(":shared:network")

// The Android SDK and the app that integrates it.
include(":android:sdk")
include(":android:sample")

// The iOS SDK and sample live under ios/ and are built with SwiftPM / Xcode,
// consuming the XCFramework produced by :shared:network.
