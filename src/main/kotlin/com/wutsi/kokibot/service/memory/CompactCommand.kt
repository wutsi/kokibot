package com.wutsi.kokibot.service.memory

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.command.Command
import com.wutsi.kokibot.command.CommandMetadata

class CompactCommand : Command {
    override fun metadata(): CommandMetadata {
        return CommandMetadata(
            name = "/compact",
            description = "Compact the assistant memory",
        )
    }

    override fun exec(input: String, context: Context): String {
        if (!input.trim().equals("confirm", ignoreCase = true)) {
            return """
                To compact the memory, please use the command with the "confirm" parameter:
                  /compact confirm

                This is to avoid accidentally compacting the memory, which cannot be undone.
            """.trimIndent()
        } else {
            return try {
                val start = System.currentTimeMillis()
                context.memory.compact()
                val duration = (System.currentTimeMillis() - start) / 1000
                "✓ Memory compacted successfully in ${duration}s"
            } catch (ex: IllegalStateException) {
                "✗ Memory compaction failed: ${ex.message}\n\n" +
                    "Your existing memory was not modified. Please check the logs for details."
            } catch (ex: java.net.SocketTimeoutException) {
                "✗ Memory compaction timed out. The LLM service may be slow or unavailable.\n\n" +
                    "Your existing memory was not modified. Please try again later."
            } catch (ex: Exception) {
                "✗ Memory compaction failed: ${ex.javaClass.simpleName}: ${ex.message}\n\n" +
                    "Your existing memory was not modified. Please check the logs for details."
            }
        }
    }
}
