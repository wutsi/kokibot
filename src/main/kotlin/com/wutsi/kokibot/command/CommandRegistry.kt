package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.exception.CommandNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CommandRegistry {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CommandRegistry::class.java)
    }

    private val commands = mutableMapOf<String, Command>()

    fun init(context: Context) {
    }

    fun destroy() {
    }

    fun all(): List<Command> {
        return commands.values.toList()
    }

    fun register(command: Command) {
        val name = command.metadata().name.lowercase()
        val xname = if (!name.startsWith("/")) "/$name" else name

        LOGGER.info("Command: $xname")
        commands[xname] = command
    }

    fun get(name: String): Command {
        return commands[name]
            ?: throw CommandNotFoundException("Command not found: $name")
    }
}
