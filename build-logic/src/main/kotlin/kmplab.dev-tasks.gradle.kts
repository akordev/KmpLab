import kmplab.tasks.TuistGenerateTask
import kmplab.tasks.XcodeBuildTask

// Developer entry points: build either artifact, on either platform, in either
// configuration. Eight concrete tasks rather than fewer tasks with --options, so
// that `./gradlew tasks` lists every combination and shells can complete them.
//
// Each one is thin. The Android tasks are lifecycle tasks over what AGP already
// registers; the iOS tasks drive xcodebuild, which Gradle otherwise knows nothing
// about.

// None of these script-level vals may share a name with a property of the tasks
// they configure. Inside a configuration block the task's own property wins on
// the right-hand side, which reads as a null group or a circular dependency.
val taskGroup = "KmpLab"

// The simulator, not a device: this is the build a developer runs. Override with
// -Pkmplab.destination for a device or a specific simulator.
val defaultDestination = providers.gradleProperty("kmplab.destination")
    .getOrElse("generic/platform=iOS Simulator")

val iosDir = layout.projectDirectory.dir("ios")
val workspaceDir = iosDir.dir("KmpLab.xcworkspace")

val tuistGenerate = tasks.register<TuistGenerateTask>("generateXcodeWorkspace") {
    group = taskGroup
    description = "Generate the Xcode workspace from the Tuist manifests"

    // Only the manifests matter. Source files do not: the targets use buildable
    // folders, so adding a file needs no regeneration.
    manifests.from(
        iosDir.file("Tuist.swift"),
        iosDir.dir("Tuist"),
        iosDir.file("sdk/Project.swift"),
        iosDir.file("sample/Project.swift"),
    )
    workspace.set(workspaceDir)
    generatedFiles.setFrom(
        workspaceDir.file("contents.xcworkspacedata"),
        iosDir.file("sdk/KmpLabSDK.xcodeproj/project.pbxproj"),
        iosDir.file("sample/KmpLabSample.xcodeproj/project.pbxproj"),
    )
    iosDirectory.set(iosDir)
    tuistExecutable.set(providers.gradleProperty("kmplab.tuist").getOrElse("tuist"))

    // Generation reads the XCFramework the manifest names, so it has to exist
    // first. Debug is enough — generation only checks that the path resolves.
    dependsOn(":shared:network:linkKmpLabNetworkDebugXCFramework")
}

fun registerIosTask(name: String, scheme: String, configuration: String, what: String) =
    tasks.register<XcodeBuildTask>(name) {
        group = taskGroup
        description = "Build the iOS $what in $configuration"

        this.scheme.set(scheme)
        this.configuration.set(configuration)
        destination.set(defaultDestination)
        workspace.set(workspaceDir)
        derivedData.set(layout.buildDirectory.dir("xcode-derived-data"))

        dependsOn(tuistGenerate)
        // Build the Kotlin framework here rather than letting Xcode's foreign
        // build phase do it, which would start a nested Gradle. XcodeBuildTask
        // sets KMPLAB_XCFRAMEWORK_READY so the phase steps aside.
        dependsOn(":shared:network:linkKmpLabNetwork${configuration}XCFramework")
    }

fun registerAndroidTask(name: String, path: String, configuration: String, what: String) =
    tasks.register(name) {
        group = taskGroup
        description = "Build the Android $what in $configuration"
        dependsOn("$path:assemble$configuration")
    }

listOf("Debug", "Release").forEach { configuration ->
    registerIosTask("buildIosSdk$configuration", "KmpLabSDK", configuration, "SDK framework")
    registerIosTask("buildIosSample$configuration", "KmpLabSample", configuration, "sample app")

    registerAndroidTask("buildAndroidSdk$configuration", ":android:sdk", configuration, "SDK (AAR)")
    registerAndroidTask("buildAndroidSample$configuration", ":android:sample", configuration, "sample app (APK)")
}
