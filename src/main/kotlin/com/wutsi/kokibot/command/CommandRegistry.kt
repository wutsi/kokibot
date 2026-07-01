package com.wutsi.kokibot.command

import com.wutsi.kokibot.Registry
import org.slf4j.LoggerFactory

class CommandRegistry : Registry<Command>() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CommandRegistry::class.java)
    }

    override fun id() = "command-registry"

    override fun keyOf(command: Command): String {
        val name = command.metadata().name.lowercase()
        return if (name.startsWith("/")) name else "/$name"
    }

    override fun notFound(name: String) = CommandNotFoundException("Command not found: $name")

    override fun register(command: Command) {
        LOGGER.info("Command: ${keyOf(command)}")
        super.register(command)
    }
}
