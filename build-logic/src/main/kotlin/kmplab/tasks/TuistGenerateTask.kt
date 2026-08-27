package kmplab.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Runs `tuist generate`, and only when a manifest actually changed.
 *
 * Declaring the manifests as inputs and the workspace as the output is what lets
 * Gradle skip this most of the time. Generation is only a couple of seconds, but
 * it rewrites the pbxproj, and rewriting it under a running Xcode makes Xcode
 * reload the project.
 */
abstract class TuistGenerateTask : MacOnlyTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifests: ConfigurableFileCollection

    /**
     * The generated files, named individually rather than as the workspace
     * directory. Xcode writes xcuserdata inside that directory every session, and
     * a directory output would treat each of those writes as this task's own
     * result going stale.
     */
    @get:OutputFiles
    abstract val generatedFiles: ConfigurableFileCollection

    @get:org.gradle.api.tasks.Internal
    abstract val workspace: DirectoryProperty

    @get:org.gradle.api.tasks.Internal
    abstract val iosDirectory: DirectoryProperty

    @get:org.gradle.api.tasks.Internal
    abstract val tuistExecutable: Property<String>

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun generate() {
        requireMacOs("Generating the Xcode workspace")

        val output = ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine(tuistExecutable.get(), "generate", "--no-open")
            workingDir = iosDirectory.get().asFile
            val stream = captureStream(output)
            standardOutput = stream
            errorOutput = stream
            isIgnoreExitValue = true
        }

        if (result.exitValue != 0) {
            failWithOutput(
                command = "tuist generate",
                output = output,
                hint = "The XCFramework must exist before generation: a manifest names it as a " +
                    "dependency and Tuist refuses to build a graph whose xcframework is missing.",
            )
        }
    }
}
