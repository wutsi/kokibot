package com.wutsi.kokibot.command

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health

class HealthCommand : Command {
    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/health",
            description = """
                Return the health of the system or a specific resource.
                Usages:
                 - `/health`: Return the overall health
                 - `/health [resource]`: Return a resource health
            """.trimIndent()
        )
    }

    override fun exec(input: String, context: Context): String {
        val id = input.trim().lowercase()
        return if (id.isEmpty()) {
            overall(context)
        } else {
            resource(id, context)
        }
    }

    private fun resource(id: String, context: Context): String {
        val resource = context.resources()
            .sortedBy { resource -> resource.id() }
            .find { resource -> resource.id() == id }
            ?: return "Resource not found: `$id`"

        val health = resource.health()
        return status(health) + " " + "`$id`" +
            if (!health.up) "\n\n" + (health.details ?: "") else ""
    }

    private fun overall(context: Context): String {
        val overall = context.health()
        return "Overall Health: " + status(overall) + "\n\n" +
            overall.children
                .sortedBy { health -> health.id }
                .joinToString("\n") { health -> status(health) + " `${health.id}`" }
    }

    private fun status(health: Health): String {
        return if (health.up) "✅" else "❌"
    }
}
