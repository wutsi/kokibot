package com.wutsi.kokibot

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

abstract class Registry<T> : Resource {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Registry::class.java)
    }

    protected val items = ConcurrentHashMap<String, T>()

    protected abstract fun keyOf(item: T): String
    protected abstract fun notFound(name: String): RuntimeException

    final override fun init(config: Map<*, *>, context: Context) = init(context)

    open fun init(context: Context) {}

    protected open fun destroyItem(item: T) {}

    override fun destroy() {
        items.values.forEach { item ->
            try {
                destroyItem(item)
            } catch (e: Exception) {
                LOGGER.warn("Failed to destroy ${keyOf(item)}: ${e.message}")
            }
        }
        items.clear()
    }

    override fun health(): Health = Health(id = id(), up = true)

    fun all(): List<T> = items.entries.sortedBy { it.key }.map { it.value }

    open fun register(item: T) {
        items[keyOf(item).lowercase()] = item
    }

    fun get(name: String): T =
        items[name.lowercase()] ?: throw notFound(name)

    open fun unregister(item: T) {
        items.remove(keyOf(item).lowercase())
    }
}
