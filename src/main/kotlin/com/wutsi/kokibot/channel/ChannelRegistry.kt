package com.wutsi.kokibot.channel

import com.wutsi.kokibot.ChannelNotFoundException
import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.util.MapUtil
import org.slf4j.LoggerFactory

class ChannelRegistry(
    private val factory: ChannelFactory = ChannelFactory()
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ChannelRegistry::class.java)
    }

    private val channels = mutableMapOf<String, Channel>()

    fun init(config: Map<*, *>, context: Context) {
        val root = MapUtil.toList("channels", config)
        root?.forEach { node ->
            if (node is Map<*, *>) {
                try {
                    initChannel(node, context)
                } catch (ex: Exception) {
                    LOGGER.warn("Failed to initialize the channel - ${ex.message}")
                }
            }
        }
    }

    private fun initChannel(config: Map<*, *>, context: Context) {
        val type = config["type"]?.toString()
            ?: throw ConfigurationException("channel type is required")

        val channel = factory.create(type)
        channel.init(config, context)
        register(channel)
    }

    fun all(): List<Channel> {
        return channels.values.toList()
    }

    fun get(id: String): Channel {
        return channels[id.lowercase()]
            ?: throw ChannelNotFoundException("Channel not found: $id")
    }

    fun register(channel: Channel) {
        channels[channel.id().lowercase()] = channel
    }
}
