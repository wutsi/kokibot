package com.wutsi.kokibot.skill

import com.wutsi.kokibot.ConfigurationException
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.Yaml
import java.io.File

@Service
class SkillParser {
    private val yaml = Yaml()

    /**
     * Parses a SKILL.md file and extracts both the SkillMetadata and the raw body content.
     */
    fun parse(file: File): Pair<SkillMetadata, String> {
        val content = file.readText()
        val parts = content.split("---")
        if (parts.size < 3) throw ConfigurationException("skill: ${file.name} - Invalid SKILL.md format")

        // 1. Parse Metadata
        val metadataMap = yaml.load<Map<String, Any>>(parts[1])
        val requires = metadataMap?.get("requires") as? Map<*, *>
        val binaries = ((requires?.get("bins") ?: requires?.get("binaries")) as? List<*>)
            ?.map { it.toString() } ?: emptyList()
        val env = (requires?.get("env") as? List<*>)?.map { it.toString() } ?: emptyList()
        val setup = (requires?.get("setup") as? List<*>)?.map { it.toString() } ?: emptyList()
        val os = (requires?.get("os") as? List<*>)?.map { it.toString() } ?: emptyList()

        val meta = metadataMap?.get("metadata") as? Map<*, *>
        val keywords = (meta?.get("keywords") as? List<*>)?.map { it.toString() } ?: emptyList()
        val categories = (meta?.get("categories") as? List<*>)?.map { it.toString() } ?: emptyList()

        // 2. Locate the Tools Section
        val body = parts.subList(2, parts.size).joinToString("---").trim()

        return Pair(
            SkillMetadata(
                home = file.parentFile,
                name = (metadataMap?.get("name")?.toString() ?: file.parentFile.name).lowercase(),
                description = metadataMap?.get("description")?.toString() ?: "",
                keywords = keywords,
                categories = categories,
                requiredBinaries = binaries,
                requiredEnv = env,
                requiredSetup = setup,
                requiredOS = os,
            ),
            body.trim(),
        )
    }

    fun extractBody(file: File): String {
        return parse(file).second
    }
}
