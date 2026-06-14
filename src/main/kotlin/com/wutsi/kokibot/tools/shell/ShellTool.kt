package com.wutsi.kokibot.tools.shell

import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import com.wutsi.kokibot.util.ShellUtil
import org.slf4j.LoggerFactory
import java.io.File

class ShellTool : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ShellTool::class.java)
        const val NAME = "shell"
        const val MAX_TIMEOUT_SECONDS = 3600L // 1 hour
    }

    // Forbidden executables (matched as command tokens, not substrings)
    private val forbiddenExecutables = setOf(
        "mkfs", "mke2fs", "chmod", "chown", "sudo"
    )

    // Forbidden (command, arg-pattern) combinations
    private val forbiddenCommandArgs = listOf(
        // rm with recursive+force flags (any order, combined or separate)
        "rm" to Regex("""(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r|--recursive)"""),
        // mv with absolute path as source
        "mv" to Regex("""^/"""),
        // dd writing to a physical disk device
        "dd" to Regex("""of=/dev/(sd|hd|nvme|disk|xvd)"""),
    )

    // Forbidden redirection target prefixes
    private val forbiddenRedirectTargets = listOf("/etc/", "/dev/", "/boot/", "/sys/", "/proc/")

    // Shell separators: ; && || | & and newlines
    private val commandSeparator = Regex("""\s*(?:;|&&|\|\||\||&|\n)\s*""")

    // Command substitution: $(...) or `...`
    private val commandSubstitution = Regex("""\$\([^)]*\)|`[^`]*`""")

    // Redirection target: matches '>' or '>>' followed by a path
    private val redirection = Regex(""">>?\s*([^\s;|&]+)""")

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Run a shell command and return the output",
        parameters = listOf(
            ToolParameter(
                name = "command",
                description = "Command to run",
                type = ToolParameterType.STRING,
                required = true
            ),
            ToolParameter(
                name = "directory",
                description = "Working directory (optional)",
                type = ToolParameterType.STRING,
                required = false
            ),
        )
    )

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        return "Bash:" +
            if (toolCalls.size == 1) {
                val cmd = toolCalls[0].arguments["command"].toString()
                if (cmd.length > 200) " ${cmd.take(200)}..." else " $cmd"
            } else {
                " ${toolCalls.size} commands"
            }
    }

    override fun exec(arguments: Map<*, *>): String {
        val command = arguments["command"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: command")
        val directory = arguments["directory"]?.toString()?.ifEmpty { null }
        val timeout = MAX_TIMEOUT_SECONDS

        val result = try {
            exec(command, directory, timeout)
        } catch (ex: Throwable) {
            LOGGER.warn("Command failed: $command", ex)
            "FAILURE. ${ex.message}"
        }
        return result
    }

    private fun exec(command: String, directory: String?, timeoutSeconds: Long): String {
        if (isForbidden(command)) {
            return "FORBIDDEN! You are not allowed to run this command for security reasons."
        }

        val result = ShellUtil.exec(command, directory?.let { File(directory) }, timeoutSeconds)
        if (result.status == 0) {
            return result.output ?: "Success"
        }
        // ShellUtil returns status=-1 specifically when the process was killed for exceeding the timeout
        if (result.status == -1) {
            LOGGER.warn("Command timed out after ${timeoutSeconds}s: $command")
            val partial = listOfNotNull(result.output, result.error).joinToString("\n").ifBlank { "" }
            return "Error: command timed out after $timeoutSeconds seconds and was terminated.\n$partial".trimEnd()
        }
        LOGGER.error("Command failed: $command\n${result.error}")
        return result.error ?: "Error. exit code=${result.status}"
    }

    /**
     * Determines whether the given shell [command] is forbidden for security reasons.
     *
     * This function performs a multi-stage analysis of the command string in order to
     * defend against common bypass techniques that a naive substring check would miss.
     *
     * Protections implemented:
     *
     * 1. **Command substitution detection** — Any expression wrapped in `$(...)` or
     *    backticks `` `...` `` is extracted and recursively re-analyzed, then stripped
     *    from the command before further parsing. This blocks payloads such as
     *    `echo $(rm -rf /)` or `` echo `chmod 777 /etc/passwd` ``.
     *
     * 2. **Sub-command splitting** — The command is split on shell separators
     *    (`;`, `&&`, `||`, `|`, `&`, and newlines) and each sub-command is checked
     *    independently. This blocks chained payloads such as `echo hello; rm -rf /data`,
     *    `ls | rm -rf /tmp`, `true && rm -rf /tmp`, or `false || sudo reboot`.
     *
     * 3. **Redirection target inspection** — Any `>` or `>>` redirection is parsed
     *    and the target path is checked against a list of forbidden prefixes
     *    (`/etc/`, `/dev/`, `/boot/`, `/sys/`, `/proc/`). `/dev/null` is exempted
     *    because it is commonly and safely used to suppress output (e.g. `2>/dev/null`).
     *
     * 4. **Token-based executable matching** — Each sub-command is tokenized on
     *    whitespace (so `rm    -rf` is normalized to `rm -rf`), leading environment
     *    variable assignments like `FOO=bar` are skipped, and absolute paths
     *    (e.g. `/bin/rm`) are reduced to their basename. The resulting executable is
     *    matched against:
     *      - [forbiddenExecutables]: an exact-match deny-list of dangerous binaries
     *        (`mkfs`, `mke2fs`, `chmod`, `chown`, `sudo`).
     *      - [forbiddenCommandArgs]: command + argument-pattern combinations such as
     *        `rm` with `-rf` / `--recursive`, `mv` with an absolute source path, and
     *        `dd` writing to a physical disk device (`of=/dev/sd*` etc.).
     *
     * @param command the raw shell command to evaluate
     * @return `true` if the command (or any of its sub-commands / substitutions) is
     *         considered unsafe and must not be executed; `false` otherwise.
     */
    private fun isForbidden(command: String): Boolean {
        // 1. Inspect anything inside $(...) or `...` first, then strip it out
        val substitutions = commandSubstitution.findAll(command).map { it.value }.toList()
        for (sub in substitutions) {
            val inner = sub
                .removePrefix("$(").removeSuffix(")")
                .removePrefix("`").removeSuffix("`")
            if (isForbidden(inner)) return true
        }
        val sanitized = commandSubstitution.replace(command, " ")

        // 2. Split into sub-commands on shell separators (; && || | &)
        val subCommands = sanitized.split(commandSeparator).map { it.trim() }.filter { it.isNotEmpty() }

        for (sub in subCommands) {
            // 3. Check redirection targets
            for (match in redirection.findAll(sub)) {
                val target = match.groupValues[1]
                if (target == "/dev/null") continue // harmless: suppressing output
                if (forbiddenRedirectTargets.any { target.startsWith(it) }) return true
            }

            // 4. Tokenize and inspect the executable + arguments
            val tokens = sub.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue

            // Strip any leading env-var assignments (e.g., FOO=bar cmd ...)
            val cmdIndex = tokens.indexOfFirst { !it.contains('=') || it.startsWith("-") }
            if (cmdIndex < 0) continue
            val executable = tokens[cmdIndex].substringAfterLast('/') // handle /bin/rm
            val args = tokens.drop(cmdIndex + 1)

            if (executable in forbiddenExecutables) return true

            for ((forbiddenCmd, argPattern) in forbiddenCommandArgs) {
                if (executable == forbiddenCmd && args.any { argPattern.containsMatchIn(it) }) {
                    return true
                }
            }
        }
        return false
    }
}
