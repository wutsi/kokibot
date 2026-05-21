package com.wutsi.kokibot.service

/**
 * Thread-local storage for current session execution context.
 * Used by tools to access session information without tight coupling to Assistant.
 */
object SessionContext {
    private val sessionId = ThreadLocal<String>()
    private val assistantName = ThreadLocal<String>()

    fun set(id: String, assistant: String) {
        sessionId.set(id)
        assistantName.set(assistant)
    }

    fun getSessionId(): String? = sessionId.get()

    fun getAssistant(): String? = assistantName.get()

    fun clear() {
        sessionId.remove()
        assistantName.remove()
    }
}
