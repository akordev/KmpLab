// build-logic is an included build, not buildSrc: a change here re-runs only its
// own compilation, where a change in buildSrc invalidates the configuration of
// every project in the main build.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"
