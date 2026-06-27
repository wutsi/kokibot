package com.wutsi.kokibot.command

import com.wutsi.kokibot.AssistantAlreadyRegisteredException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.MultiBootstrap

class RenameCommand(private val multiBootstrap: MultiBootstrap) : Command {
    override fun metadata() = CommandMetadata(
        name = "/rename",
        description = "Rename the assistant and move its home directory.\nUsage: `/rename <new-name>`",
    )

    override fun exec(input: Message, context: Context): String {
        val oldName = context.assistant.name
        val newName = input.text
        try {
            multiBootstrap.rename(oldName, newName)
        } catch (e: AssistantAlreadyRegisteredException) {
            return "Cannot rename: assistant `$newName` already exists"
        } catch (e: Exception) {
            return "Rename failed: ${e.message}"
        }

        return "✓ Assistant renamed from `$oldName` to `$newName`"
    }
}
