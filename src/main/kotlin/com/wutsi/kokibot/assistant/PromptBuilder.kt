package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import org.apache.commons.io.IOUtils
import java.io.File

class PromptBuilder(
    private val assistantName: String
) {
    fun buildPrompt(
        query: Message,
        iterationMemory: List<String>,
        context: Context
    ): String {
        val sb = StringBuilder()
        sb.append("Query: ${query.text}\n")

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

        return sb.toString()
    }

    fun buildSystemInstructions(
        query: Message,
        coordinator: Boolean,
        context: Context
    ): String {
        val entries = listOfNotNull(
            loadIdentity(context),
            if (coordinator) coordinatorInstructions(context.home) else null,
            dailyLogInstructions(context.home),
            chatHistoryInstructions(query, context.home),
            skillsInstructions(context),
            securityInstructions(context.home),
        )
        return entries.joinToString("\n\n---\n\n")
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
            file.readText().replace("{{ASSISTANT_NAME}}", assistantName)
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

    private fun securityInstructions(home: File): String {
        return IOUtils.toString(
            javaClass.getResource("/instructions/SECURITY.md"),
            "utf-8"
        ).replace("{{HOME}}", home.absolutePath)
    }

    private fun coordinatorInstructions(home: File): String {
        return IOUtils.toString(
            javaClass.getResource("/instructions/COORDINATOR.md"),
            "utf-8"
        ).replace("{{HOME}}", home.absolutePath)
    }

    private fun dailyLogInstructions(home: File): String {
        return IOUtils.toString(
            javaClass.getResourceAsStream("/instructions/DAILY_LOG.md"),
            "utf-8"
        ).replace("{{HOME}}", home.absolutePath)
    }

    private fun chatHistoryInstructions(query: Message, home: File): String? {
        val userId = query.userId
        val channelId = query.channelId
        if (userId == null || channelId == null) {
            return null
        }
        return IOUtils.toString(
            javaClass.getResourceAsStream("/instructions/CHAT_HISTORY.md"),
            "utf-8"
        )
            .replace("{{HOME}}", home.absolutePath)
            .replace("{{USER_ID}}", userId)
            .replace("{{CHANNEL_ID}}", channelId.removePrefix("channel:"))
    }
}
