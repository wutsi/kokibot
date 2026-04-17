package com.wutsi.kokibot.util

import com.wutsi.kokibot.tools.shell.ShellTool.Companion.ERROR_TIMEOUT
import java.io.File
import java.util.concurrent.TimeUnit

object ShellUtil {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    /**
     * Checks if a command exists in the system PATH.
     */
    fun exists(command: String): Boolean {
        val checkCommand = if (isWindows) listOf("where", command) else listOf("which", command)

        return try {
            val process = ProcessBuilder(checkCommand)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()

            // Wait briefly for the check to complete
            val finished = process.waitFor(5, TimeUnit.SECONDS)

            // On Unix, 'which' returns 0 if found, non-zero otherwise
            finished && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun exec(command: String, directory: File?, timeoutSeconds: Long = 60): String {
        val builder = ProcessBuilder("sh", "-c", command)
        if (directory != null) {
            builder.directory(directory)
        }
        val process = builder.start()

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ERROR_TIMEOUT
        }

        return if (process.exitValue() == 0) {
            process.inputStream.bufferedReader().readText()
        } else {
            process.errorStream.bufferedReader().readText()
        }
    }
}
