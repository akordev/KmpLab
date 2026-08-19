import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.skie)
}

// One XCFramework bundling every iOS slice. This is what ios/sdk consumes as a
// binary target; the Swift SDK wraps it so consumers never see Kotlin types.
val xcframework = XCFramework("KmpLabNetwork")

kotlin {
    android {
        namespace = "dev.akordev.kmplab.network"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // Host-side unit tests. On a Linux machine this is the only place the
        // shared code can actually be executed — the iOS test targets need a Mac.
        withHostTestBuilder {}
    }

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "KmpLabNetwork"
            isStatic = true
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// SKIE rewrites the generated Swift API: sealed classes become exhaustive Swift
// enums with their generic type argument intact, Kotlin default arguments become
// Swift overloads, and Flows become AsyncSequences. Without it the Swift side has
// to cast its way through Objective-C, which erases all of that.
//
// SKIE is a *linker* plugin: it only runs when a framework is actually linked,
// which cannot happen on Linux. See ios/README.md.
skie {
    build {
        // The XCFramework is handed to consumers as a binary, so it may be linked
        // by a different Xcode than built it. This turns on Swift library
        // evolution — which XCFrameworks require — along with
        // noClangModuleBreadcrumbsInStaticFrameworks, which exists for exactly
        // the static framework this produces.
        produceDistributableFramework()
    }
}

// Write the XCFramework straight into the iOS tree instead of build/, so
// ios/sdk/Project.swift can reference it by a fixed relative path. The task
// appends the build type, giving Artifacts/debug and Artifacts/release.
//
// Relative to this project rather than rootProject.layout, which the
// configuration cache does not allow a subproject to reach for.
tasks.withType<XCFrameworkTask>().configureEach {
    outputDir = layout.projectDirectory.dir("../../ios/sdk/Artifacts").asFile
}
