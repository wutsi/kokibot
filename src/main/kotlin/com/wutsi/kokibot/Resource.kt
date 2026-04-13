package com.wutsi.kokibot

interface Resource {
    fun id(): String
    fun init(config: Map<*, *>, context: Context)
    fun destroy() {}
    fun health(): Health
}
