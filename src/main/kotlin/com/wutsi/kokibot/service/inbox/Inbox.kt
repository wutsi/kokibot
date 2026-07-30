package com.wutsi.kokibot.service.inbox

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Inbox : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Inbox::class.java)
        private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        const val PENDING = "pending"
        const val PROCESSING = "processing"
        const val DONE = "done"
        const val FAILED = "failed"
        const val ORPHANED = "orphaned"
        const val CANCEL = "cancel"
    }

    private lateinit var context: Context
    private lateinit var inboxDir: File
    private var onSubmit: (() -> Unit)? = null

    override fun id() = "service:inbox"

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        inboxDir = File(context.home, "inbox")
        listOf(PENDING, PROCESSING, DONE, FAILED, ORPHANED, CANCEL).forEach { state ->
            File(inboxDir, state).mkdirs()
        }
        orphanProcessing()
        LOGGER.info("Inbox initialized at ${inboxDir.absolutePath}")
    }

    fun onSubmit(callback: () -> Unit) {
        this.onSubmit = callback
    }

    fun submit(message: Message): InboxMessage {
        val inbox = InboxMessage(
            id = message.id,
            channelId = message.channelId,
            userId = message.userId,
            text = message.text,
            filePaths = message.filePaths,
            subject = message.subject,
            conversationId = message.conversationId,
            submittedAt = LocalDateTime.now(),
        )
        val file = stateFile(PENDING, inbox)
        file.writeText(context.jsonMapper.writeValueAsString(inbox))
        LOGGER.info("Submitted ${inbox.id} to inbox")
        onSubmit?.invoke()
        return inbox
    }

    @Synchronized
    fun poll(): InboxMessage? {
        val file = File(inboxDir, PENDING)
            .listFiles { f -> f.isFile && f.extension == "json" }
            ?.minByOrNull { it.name }
            ?: return null

        val message = context.jsonMapper.readValue(file.readText(), InboxMessage::class.java)
        val updated = message.copy(processedAt = LocalDateTime.now())
        move(file, updated, PROCESSING)
        LOGGER.info("Polled ${updated.id} from inbox")
        return updated
    }

    fun complete(id: String, response: String) {
        val file = findInState(PROCESSING, id) ?: run {
            LOGGER.warn("Message $id not found in processing — skipping complete")
            return
        }
        val message = context.jsonMapper.readValue(file.readText(), InboxMessage::class.java)
        move(file, message.copy(response = response, completedAt = LocalDateTime.now()), DONE)
        clearCancel(id)
        LOGGER.info("Completed $id")
    }

    fun processingCount(): Int =
        File(inboxDir, PROCESSING)
            .listFiles { f -> f.isFile && f.extension == "json" }
            ?.size ?: 0

    fun fail(id: String, error: String) {
        val file = findInState(PROCESSING, id) ?: run {
            LOGGER.warn("Message $id not found in processing — skipping fail")
            return
        }
        val message = context.jsonMapper.readValue(file.readText(), InboxMessage::class.java)
        move(file, message.copy(error = error, completedAt = LocalDateTime.now()), FAILED)
        clearCancel(id)
        LOGGER.info("Failed $id: $error")
    }

    fun cancel(id: String) {
        File(File(inboxDir, CANCEL), "$id.cancel").createNewFile()
        LOGGER.info("Cancel requested for $id")
    }

    fun isCancelled(id: String): Boolean =
        File(File(inboxDir, CANCEL), "$id.cancel").exists()

    private fun clearCancel(id: String) {
        File(File(inboxDir, CANCEL), "$id.cancel").delete()
    }

    private fun orphanProcessing() {
        val files = File(inboxDir, PROCESSING)
            .listFiles { f -> f.isFile && f.extension == "json" }
            ?: return
        files.forEach { file ->
            val message = context.jsonMapper.readValue(file.readText(), InboxMessage::class.java)
            move(file, message, ORPHANED)
            LOGGER.warn("Orphaned ${message.id} from previous run")
        }
    }

    private fun move(from: File, message: InboxMessage, targetState: String) {
        stateFile(targetState, message).writeText(context.jsonMapper.writeValueAsString(message))
        from.delete()
    }

    private fun findInState(state: String, id: String): File? =
        File(inboxDir, state)
            .listFiles { f -> f.isFile && f.extension == "json" && f.nameWithoutExtension.endsWith("-$id") }
            ?.firstOrNull()

    private fun stateFile(state: String, message: InboxMessage): File {
        val ts = message.submittedAt.format(TIMESTAMP_FORMAT)
        return File(File(inboxDir, state), "$ts-${message.id}.json")
    }
}
