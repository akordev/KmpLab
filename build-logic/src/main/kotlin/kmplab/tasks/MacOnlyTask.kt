package kmplab.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Shared behaviour for tasks that shell out to Xcode's toolchain.
 *
 * Nothing here touches the Project object at execution time, so these tasks stay
 * compatible with the configuration cache the build has enabled.
 */
abstract class MacOnlyTask : DefaultTask() {

    protected fun requireMacOs(what: String) {
        val os = System.getProperty("os.name") ?: ""
        if (!os.startsWith("Mac")) {
            throw GradleException(
                "$what needs macOS and the Xcode toolchain, but this is $os.\n" +
                    "The Android tasks build everywhere; the iOS ones do not.",
            )
        }
    }

    /**
     * Captures output for the failure message, and with --info also lets it
     * through live. A build that prints nothing for half a minute reads as hung.
     */
    protected fun captureStream(buffer: ByteArrayOutputStream): OutputStream =
        if (logger.isInfoEnabled) {
            object : OutputStream() {
                override fun write(b: Int) {
                    buffer.write(b)
                    System.out.write(b)
                }

                override fun flush() {
                    buffer.flush()
                    System.out.flush()
                }
            }
        } else {
            buffer
        }

    /**
     * Fails with the command's own output rather than a Gradle stack trace.
     * A wall of Kotlin frames above an xcodebuild error helps nobody.
     */
    protected fun failWithOutput(command: String, output: ByteArrayOutputStream, hint: String? = null): Nothing {
        val text = output.toString().lines()
        val tail = text.takeLast(40).joinToString("\n")
        throw GradleException(
            buildString {
                appendLine("$command failed.")
                hint?.let { appendLine(it) }
                appendLine()
                appendLine("Last lines of output:")
                appendLine(tail)
            },
        )
    }
}
