package com.wutsi.kokibot.mcp

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class McpOkHttpTransport(private val httpClient: OkHttpClient = OkHttpClient()) : McpHttpTransport {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    override fun post(url: String, headers: Map<String, String>, body: String): McpHttpResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }

        return httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            val responseHeaders = response.headers.toMap()
            McpHttpResponse(statusCode = response.code, headers = responseHeaders, body = responseBody)
        }
    }
}
