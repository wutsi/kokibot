package com.wutsi.kokibot.service.heartbeat

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata

class HeartbeatCommand : Command {
    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/heartbeat",
            description = "Trigger the heartbeat manually",
        )
    }

    override fun exec(input: String, context: Context): String {
        context.heartbeat.tick()
        return "Heartbeat triggered"
    }
}
