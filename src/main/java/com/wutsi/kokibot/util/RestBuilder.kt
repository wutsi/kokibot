package com.wutsi.kokibot.util

import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestTemplate
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.temporal.ChronoUnit

class RestBuilder {
    private val jsonMapper = JsonMapper.builderWithJackson2Defaults()
        .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun build(
        readTimeoutMillis: Long?,
        connectTimeoutMillis: Long?,
    ): RestTemplate {
        val builder = RestTemplateBuilder()
            .additionalMessageConverters(JacksonJsonHttpMessageConverter(jsonMapper))

        if (readTimeoutMillis != null) {
            builder.readTimeout(Duration.of(readTimeoutMillis, ChronoUnit.MILLIS))
        }
        if (connectTimeoutMillis != null) {
            builder.connectTimeout(Duration.of(connectTimeoutMillis, ChronoUnit.MILLIS))
        }
        return builder.build()
    }
}
