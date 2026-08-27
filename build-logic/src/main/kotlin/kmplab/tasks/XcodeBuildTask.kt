package kmplab.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * One `xcodebuild` invocation.
 *
 * Never up to date: xcodebuild has its own incremental build, and duplicating
 * that judgement in Gradle is how you end up serving a stale app.
 */
abstract class XcodeBuildTask : MacOnlyTask() {

    @get:Input
    abstract val scheme: Property<String>

    @get:Input
    abstract val configuration: Property<String>

    @get:Input
    abstract val destination: Property<String>

    @get:Internal
    abstract val workspace: DirectoryProperty

    @get:Internal
    abstract val derivedData: DirectoryProperty

    @get:Inject
    abstract val execOps: ExecOperations

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun build() {
        requireMacOs("Building ${scheme.orNull ?: "an iOS target"}")

        val workspaceDir = workspace.get().asFile
        if (!workspaceDir.exists()) {
            throw org.gradle.api.GradleException(
                "No workspace at ${workspaceDir.path}.\n" +
                    "It is generated, not committed — this task depends on generateXcodeWorkspace, " +
                    "so reaching this means generation was skipped or failed.",
            )
        }

        val target = destination.get()
        val arguments = buildList {
            add("xcodebuild")
            add("-workspace"); add(workspaceDir.path)
            add("-scheme"); add(scheme.get())
            add("-configuration"); add(configuration.get())
            add("-destination"); add(target)
            add("-derivedDataPath"); add(derivedData.get().asFile.path)
            // Simulator builds are signed ad-hoc, and a missing signing identity
            // should not stop a developer building. Device builds keep signing.
            if (target.contains("Simulator")) add("CODE_SIGNING_ALLOWED=NO")
            add("build")
        }

        val output = ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine(arguments)
            val stream = captureStream(output)
            standardOutput = stream
            errorOutput = stream
            isIgnoreExitValue = true
            // The XCFramework is already built: this task depends on the Gradle
            // task that produces it. Without this the foreign build phase would
            // launch a second Gradle from inside this one, which contends for the
            // same project lock.
            environment("KMPLAB_XCFRAMEWORK_READY", "1")
        }

        if (result.exitValue != 0) {
            failWithOutput("xcodebuild ${scheme.get()} (${configuration.get()})", output)
        }
        logger.lifecycle("Built ${scheme.get()} (${configuration.get()}) for $target")
    }
}
