package com.wutsi.kokibot.skill

import com.wutsi.kokibot.exception.ConfigurationException
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.regex.Pattern

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
        val bins = (requires?.get("bins") as? List<*>)?.map { it.toString() } ?: emptyList()
        val env = (requires?.get("env") as? List<*>)?.map { it.toString() } ?: emptyList()

        val meta = metadataMap?.get("metadata") as? Map<*, *>
        val keywords = (meta?.get("keywords") as? List<*>)?.map { it.toString() } ?: emptyList()
        val categories = (meta?.get("categories") as? List<*>)?.map { it.toString() } ?: emptyList()

        // 2. Locate the Tools Section
        val body = parts[2]
        val toolsSection = body.substringAfter("## Tools").substringBefore("##")

        // 3. Extract Tools and their nested Parameters
        // Regex for tool name: - `tool_name`:
        // Regex for params:   - `param_name`: (type) desc
        val toolLines = toolsSection.trim().split("\n")
        val tools = mutableListOf<ToolMetadata>()
        var currentTool: ToolMetadata? = null
        var currentParams = mutableListOf<ToolParameter>()

        val toolHeaderPattern = Pattern.compile("- `(?<tool>\\w+)`:(?<desc>.*)")
        val paramPattern = Pattern.compile("\\s+- `(?<name>\\w+)`: \\((?<type>\\w+)\\) (?<desc>.*)")

        toolLines.forEach { line ->
            val toolMatcher = toolHeaderPattern.matcher(line)
            val paramMatcher = paramPattern.matcher(line)

            when {
                paramMatcher.find() -> {
                    val pDesc = paramMatcher.group("desc")
                    currentParams.add(
                        ToolParameter(
                            name = paramMatcher.group("name").trim(),
                            type = try {
                                ToolParameterType.valueOf(paramMatcher.group("type").toString())
                            } catch (_: Exception) {
                                ToolParameterType.STRING
                            },
                            description = pDesc.trim(),
                            required = !pDesc.contains("optional", ignoreCase = true)
                        )
                    )
                }

                toolMatcher.find() -> {
                    // If we were already building a tool, save it
                    currentTool?.let { tools.add(it.copy(parameters = currentParams.toList())) }

                    // Start new tool
                    currentTool = ToolMetadata(
                        name = toolMatcher.group("tool").trim(),
                        description = toolMatcher.group("desc").trim()
                    )
                    currentParams = mutableListOf()
                }
            }
        }

        // Add the final tool in the loop
        currentTool?.let { tools.add(it.copy(parameters = currentParams)) }

        return Pair(
            SkillMetadata(
                name = (metadataMap["name"]?.toString() ?: file.name).lowercase(),
                description = metadataMap["description"]?.toString() ?: "",
                keywords = keywords,
                categories = categories,
                requiredBins = bins,
                requiredEnv = env,
                tools = tools
            ),
            body.trim(),
        )
    }
}
