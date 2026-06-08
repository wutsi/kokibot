package com.wutsi.kokibot.tools.user

class AskQuestionException(val question: String) : RuntimeException() {
    override val message: String
        get() = question
}
