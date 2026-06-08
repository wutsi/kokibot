package com.wutsi.kokibot.service

/**
 * Thread-local storage for current session execution context.
 * Used by tools to access session information without tight coupling to Assistant.
 */
object ExecutionContext {
    private val sessionId = ThreadLocal<String>()
    private val assistant = ThreadLocal<String>()
    private val userId = ThreadLocal<String?>()
    private val channelId = ThreadLocal<String?>()

    fun set(sessionId: String, assistant: String, userId: String?, channelId: String?) {
        this.sessionId.set(sessionId)
        this.assistant.set(assistant)
        this.userId.set(userId)
        this.channelId.set(channelId)
    }

    fun getSessionId(): String? = sessionId.get()

    fun getAssistant(): String? = assistant.get()

    fun getUserId(): String? = userId.get()

    fun getChannelId(): String? = channelId.get()

    fun clear() {
        sessionId.remove()
        assistant.remove()
        userId.remove()
        channelId.remove()
    }
}
