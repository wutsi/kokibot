package com.wutsi.kokibot.tools.mail

import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class MailUnsubscribeTool(private var http: HttpClient = HttpClient.newHttpClient()) : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MailUnsubscribeTool::class.java)

        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"
        const val NAME = "mail_unsubscribe"
    }

    override fun metadata(): ToolMetadata = ToolMetadata(
        name = NAME,
        description = "Unsubscribe from a mailing list",
        parameters = listOf(
            ToolParameter(
                name = "url",
                description = "Unsubscription URL",
                type = ToolParameterType.STRING,
                required = true
            ),
        )
    )

    override fun exec(arguments: Map<*, *>): String {
        val url = arguments["url"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: url")

        try {
            unsubscribe(url)
            return "Unsubscribed from $url"
        } catch (ex: Exception) {
            LOGGER.error("Failed to unsubscribe from $url", ex)
            return "Failed to unsubscribe from $url. Error: ${ex.message}"
        }
    }

    private fun unsubscribe(url: String) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build()

        http.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
