package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import org.apache.commons.io.IOUtils
import java.io.File
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PromptBuilder(
    private val assistantName: String,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    companion object {
        private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "EEEE, MMMM d, yyyy 'at' HH:mm:ss zzz",
            Locale.ENGLISH
        )
    }

    fun buildPrompt(query: Message, iterationMemory: List<String>, context: Context): String {
        val sb = StringBuilder()
        val text = buildText(query, context)
        sb.append("Query: $text\n")

        sb.append("\n---\n")
        sb.append("# Current Date and Time\n")
        val now = ZonedDateTime.now(clock)
        sb.append("The current date and time is: ${now.format(DATE_TIME_FORMATTER)} (${now.toOffsetDateTime()})\n")

        val longTermMemory = context.memory.get()
        if (longTermMemory != null) {
            sb.append("\n---\n")
            sb.append("# Long-Term Memory\n")
            sb.append("Here are information that you have stored in your long-term memory in Markdown format:\n")
            sb.append("```markdown\n$longTermMemory\n```\n")
        }

        val shortTermMemory = context.dailyLog.get()
        if (shortTermMemory != null) {
            sb.append("\n---\n\n")
            sb.append("# Short-Term Memory\n")
            sb.append("Here are information that you have stored in your short-term memory in Markdown format:\n")
            sb.append("```markdown\n$shortTermMemory\n```\n")
        }

        if (iterationMemory.isNotEmpty()) {
            sb.append("\n---\n\n")
            sb.append("# Previous reasoning steps and observations\n")
            iterationMemory.forEach { line -> sb.append("$line\n\n") }
        }

        return applyVariables(sb.toString(), query, context)
    }

    fun buildSystemInstructions(
        query: Message,
        coordinator: Boolean,
        context: Context
    ): String {
        val entries = listOfNotNull(
            loadIdentity(context),
            if (coordinator) coordinatorInstructions() else null,
            dailyLogInstructions(),
            chatHistoryInstructions(query),
            skillsInstructions(context),
            securityInstructions(),
        )
        return applyVariables(entries.joinToString("\n\n---\n\n"), query, context)
    }

    internal fun buildText(query: Message, context: Context): String {
        query.channelId ?: return query.text

        val channelId = query.channelId.removePrefix("channel:")

        val input = javaClass.getResourceAsStream("/instructions/channel/$channelId.md") ?: return query.text
        return query.text +
            "\n\n" +
            IOUtils.toString(input, "utf-8")
    }

    internal fun loadIdentity(context: Context): String? {
        return loadIdentity(context.home)
    }

    internal fun saveIdentity(content: String, context: Context) {
        val file = File(context.home, "ASSISTANT.md")
        file.writeText(content)
    }

    private fun loadIdentity(home: File): String? {
        val file = File(home, "ASSISTANT.md")
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }

    private fun skillsInstructions(context: Context): String? {
        val skills = context.skillRegistry
            .all()
            .filter { skill -> skill.health().up }
            .joinToString("\n") { skill ->
                listOfNotNull(
                    "## Skill: ${skill.metadata.name}\n\n" +
                        "**Home Directory:** ${skill.metadata.home}\n\n" +
                        "**Description:** ${skill.metadata.description}"
                ).joinToString("\n\n")
            }
            .ifEmpty { null }

        return skills?.let { "# Available skills\n\nHere are the skills available:\n\n$skills" }
    }

    private fun securityInstructions(): String {
        return IOUtils.toString(
            javaClass.getResource("/instructions/SECURITY.md"),
            "utf-8"
        )
    }

    private fun coordinatorInstructions(): String {
        return IOUtils.toString(
            javaClass.getResource("/instructions/COORDINATOR.md"),
            "utf-8"
        )
    }

    private fun dailyLogInstructions(): String {
        return IOUtils.toString(
            javaClass.getResourceAsStream("/instructions/DAILY_LOG.md"),
            "utf-8"
        )
    }

    private fun chatHistoryInstructions(query: Message): String? {
        val userId = query.userId
        val channelId = query.channelId
        if (userId == null || channelId == null) {
            return null
        }
        return IOUtils.toString(
            javaClass.getResourceAsStream("/instructions/CHAT_HISTORY.md"),
            "utf-8"
        )
    }

    private fun applyVariables(text: String, query: Message, context: Context): String {
        val userId = query.userId ?: "-"
        val channelId = query.channelId?.removePrefix("channel:") ?: "-"

        return text.replace("{{ASSISTANT_NAME}}", assistantName)
            .replace("{{HOME}}", context.home.absolutePath)
            .replace("{{USER_ID}}", userId)
            .replace("{{CHANNEL_ID}}", channelId.removePrefix("channel:"))
    }
}
