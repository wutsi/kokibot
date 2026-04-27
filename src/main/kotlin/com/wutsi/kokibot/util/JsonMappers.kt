package com.wutsi.kokibot.util

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper

/**
 * Factory for building a resilient [JsonMapper] (Jackson 3.x).
 *
 * Mirrors the desired Spring `spring.jackson` configuration:
 * ```
 * spring:
 *   jackson:
 *     default-property-inclusion: non_null
 *     use-jackson2-defaults: true
 *     deserialization:
 *       FAIL_ON_UNKNOWN_PROPERTIES: false
 *       FAIL_ON_NULL_FOR_PRIMITIVES: false
 *       ACCEPT_EMPTY_STRING_AS_NULL_OBJECT: true
 *     mapper:
 *       ACCEPT_CASE_INSENSITIVE_ENUMS: true
 *       ACCEPT_CASE_INSENSITIVE_PROPERTIES: true
 * ```
 *
 * Additionally enables lenient JSON reading features so the mapper does not crash
 * on payloads coming from LLMs (which often contain unescaped control characters,
 * single quotes, comments, or trailing commas).
 *
 * Note: in Jackson 3, `ACCEPT_CASE_INSENSITIVE_ENUMS` and
 * `ACCEPT_CASE_INSENSITIVE_PROPERTIES` were unified into the single feature
 * `MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES`.
 */
object JsonMappers {
    fun create(): JsonMapper {
        return JsonMapper.builderWithJackson2Defaults()
            // default-property-inclusion: non_null
            .changeDefaultPropertyInclusion { _ ->
                JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS)
            }
            // deserialization
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            // mapper (case-insensitive enums + properties)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
            // resilience: tolerate malformed JSON often produced by LLMs
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build()
    }
}
