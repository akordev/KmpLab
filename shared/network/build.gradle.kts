import java.nio.file.Files
import java.nio.file.Paths
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

    // No iosX64: that slice exists only for the simulator on an Intel Mac, and
    // it costs a third of every Kotlin rebuild in the local dev loop. Add it back
    // here if someone needs to develop on Intel hardware.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
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

    // SKIE captures build analytics and uploads them to Touchlab on every
    // framework link — the skieUploadAnalytics* tasks in the build log. Nothing
    // in the build should need the network to succeed, so both halves are off.
    //
    // If you ever want to see what it would have sent, swap this for
    // disableUpload.set(true): capture still happens, and the JSON lands in
    // build/skie/<framework>/<arch>/analytics.
    analytics {
        enabled.set(false)
    }
}

// The XCFramework is an intermediate, not a deliverable: ios/sdk is its only
// consumer and KmpLabSDK absorbs it. So it stays under build/, where `gradlew
// clean` reaches it and .gitignore already covers it.
//
// A pbxproj cannot vary an xcframework reference by configuration, so Xcode needs
// one fixed path. These tasks maintain it as a symlink pointing at whichever
// build type was asked for.
//
// The symlink has to follow the *request*, not the build: assembling is often
// UP-TO-DATE, and a doLast on the assemble task would then be skipped, leaving
// Debug builds silently linking the release framework. Hence a separate task
// that never reports itself up to date.
val xcframeworksDir = layout.buildDirectory.dir("XCFrameworks").get().asFile

listOf("Debug", "Release").forEach { buildType ->
    tasks.register("linkKmpLabNetwork${buildType}XCFramework") {
        description = "Assemble the $buildType XCFramework and point KmpLabNetwork.xcframework at it"
        dependsOn("assembleKmpLabNetwork${buildType}XCFramework")
        outputs.upToDateWhen { false }

        val target = Paths.get(buildType.lowercase(), "KmpLabNetwork.xcframework")
        val dir = xcframeworksDir
        doLast {
            val link = dir.resolve("KmpLabNetwork.xcframework")
            when {
                Files.isSymbolicLink(link.toPath()) -> Files.delete(link.toPath())
                link.exists() -> link.deleteRecursively()
            }
            Files.createSymbolicLink(link.toPath(), target)
        }
    }
}
