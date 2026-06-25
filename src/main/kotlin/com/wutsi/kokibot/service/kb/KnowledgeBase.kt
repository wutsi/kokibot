package com.wutsi.kokibot.service.kb

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.service.file.MarkdownConverter
import org.slf4j.LoggerFactory
import java.io.File

class KnowledgeBase : Resource {
    private var enabled: Boolean = true
    private var exclusive: Boolean = true
    private lateinit var context: Context

    companion object {
        const val ID = "service:knowledge-base"
        private val LOGGER = LoggerFactory.getLogger(KnowledgeBase::class.java)
    }

    override fun id(): String {
        return ID
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        this.enabled = config["enabled"] as? Boolean ?: true
        this.exclusive = config["exclusive"] as? Boolean ?: true
    }

    fun isEnabled(): Boolean = enabled

    fun isExclusive(): Boolean = exclusive

    fun apply(key: String, value: Any) {
        when (key) {
            "enabled" -> enabled = value.toString().toBoolean()
            "exclusive" -> exclusive = value.toString().toBoolean()
            else -> throw ConfigurationException("Unknown knowledge-base setting: $key")
        }
    }

    fun ingest(file: File) {
        if (checkIfAlreadyIngested(file)) {
            throw FileAlreadyIngestedException("${file.name} is already ingest")
        }

        // Store the file into the source directory
        val source = addToSource(file)
        val md = convertToMarkdown(source)

        // Summarize
        val result = summarize(md)
        val summary = File(getRawDir(), "${source.name}.summary.md")
        summary.writeText(result.summary)

        // Update the index
        LOGGER.info("Adding ${file.name} to the knowledge base index")
        val entry = KBEntry(
            name = file.name,
            scope = result.scope,
            keywords = result.keywords,
            summary = summary.absolutePath,
            raw = md.absolutePath,
            source = source.absolutePath,
        )
        val index = readIndex().toMutableList()
        index.add(entry)
        writeIndex(index)
    }

    fun delete(name: String) {
        val index = readIndex()
        val entry = index.find { it.name == name }
        if (entry == null) {
            LOGGER.warn("Knowledge base entry $name not found")
            return
        }

        // Remove from index
        val xindex = index.filter { it.name != name }
        writeIndex(xindex)

        // Delete local files
        deleteFile(entry.source)
        deleteFile(entry.raw)
        deleteFile(entry.summary)
    }

    fun readIndex(): List<KBEntry> {
        val file = getIndexFile()
        if (!file.exists()) return emptyList()
        return try {
            context.jsonMapper.readValue(
                file.readText(),
                context.jsonMapper.typeFactory.constructCollectionType(List::class.java, KBEntry::class.java),
            )
        } catch (ex: Exception) {
            LOGGER.warn("Failed to read index file", ex)
            emptyList()
        }
    }

    fun checkIfAlreadyIngested(file: File): Boolean {
        val index = readIndex()
        return index.any { it.name == file.name }
    }

    private fun deleteFile(path: String) {
        val file = File(path)
        if (file.exists()) {
            LOGGER.info("Deleting $path")
            file.delete()
        }
    }

    private fun writeIndex(entries: List<KBEntry>) {
        val file = getIndexFile()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(context.jsonMapper.writeValueAsString(entries))
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IllegalStateException("Failed update the knowledge base")
        }
    }

    private fun summarize(md: File): KBSumaryResult {
        LOGGER.info("Extracting summary from  ${md.absolutePath}")

        val request = LLMRequest(
            prompt = """
                You are a highly meticulous Technical Data Compiler.
                Your task is to read the provided content and extract the following information:
                1. Scope: Provide a concise description of the scope of the content, limited to 30 to 50 words.
                2. Summary: High-fidelity summary of the content in Markdown format. Your primary directive is ZERO SEMANTIC DATA LOSS. Do not summarize away technical details, specific configurations, metrics, architecture patterns, or domain-specific logic.
                3. Keywords: Identify and list relevant keywords that encapsulate the core topics, technologies, and concepts discussed in the content. Up to 10 keywords

                Return the result in JSON format with the following structure:
                ```
                {
                    "scope": "Scope of the content - 50 to 100 words",
                    "summary": "Summary of the content",
                    "keywords": ["keyword1", "keyword2", ...]
                }
                ```

                **IMPORTANT:** Return only the JSON object, without any additional text or formatting.

                Content:
                ${md.readText()}
            """.trimIndent(),
        )
        val response = context.llm.completion(request, emptyList())

        val json = response.choices[0].content
        return context.jsonMapper.readValue(json, KBSumaryResult::class.java)
    }

    private fun addToSource(file: File): File {
        val source = File(getSourceDir(), file.name)

        LOGGER.info("Storing ${file.absolutePath} into ${source.absolutePath}")
        file.copyTo(source, overwrite = true)
        return source
    }

    private fun convertToMarkdown(source: File): File {
        LOGGER.info("Converting ${source.absolutePath} to Markdown")

        // Convert to markdown using pandoc or markitdown
        val converter = MarkdownConverter(fileService = context.fileService)
        val content = converter.convert(source)

        // Store
        val md = File(getRawDir(), "${source.name}.md")
        md.writeText(content)
        return md
    }

    private fun getSourceDir(): File {
        val dir = File(getRootDir(), "/source")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getRawDir(): File {
        val dir = File(getRootDir(), "/raw")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getIndexFile(): File {
        return File(getRootDir(), "index.json")
    }

    private fun getRootDir(): File {
        val dir = File(context.home, "kb")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
