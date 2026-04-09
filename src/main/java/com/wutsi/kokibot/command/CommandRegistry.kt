package com.wutsi.kokibot.command

import com.wutsi.kokibot.exception.CommandNotFoundException
import org.springframework.stereotype.Service

@Service
class CommandRegistry {
    private val commands = mutableMapOf<String, Command>()

    fun register(command: Command) {
        val name = command.name().lowercase()
        val xname = if (!name.startsWith("/")) "/$name" else name

        commands[xname] = command
    }

    fun get(name: String): Command? {
        return commands[name]
            ?: throw CommandNotFoundException("Command not found: $name")
    }
}
