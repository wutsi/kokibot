//        val file = download(url)
package com.wutsi.kokibot.service.kb

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.service.file.MarkdownConverter
import com.wutsi.kokibot.util.URLUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class KnowledgeBase : Resource {
    private lateinit var context: Context
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var exclusive: Boolean = true

    @Volatile
    private var webSearch: Boolean = true

    companion object {
        const val ID = "service:knowledge-base"
        private val LOGGER = LoggerFactory.getLogger(KnowledgeBase::class.java)
    }

    override fun id(): String {
        return ID
    }

    override fun init(config: Map<*, *>, context: Context) {
        this.context = context
        this.enabled = config["enabled"] as? Boolean ?: false
        this.exclusive = config["exclusive"] as? Boolean ?: true
    }

    override fun destroy() {
        super.destroy()

        executor.shutdown()
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                LOGGER.warn("Scheduler didn't terminate gracefully, forcing shutdown")
                executor.shutdownNow()
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.error("Scheduler failed to terminate")
                }
            }
        } catch (_: InterruptedException) {
            LOGGER.warn("Interrupted while waiting for scheduler shutdown")
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    fun isEnabled(): Boolean = enabled

    fun isExclusive(): Boolean = exclusive

    fun isWebSearch(): Boolean = webSearch

    fun apply(key: String, value: Any) {
        when (key) {
            "enabled" -> enabled = value.toString().toBoolean()
            "exclusive" -> exclusive = value.toString().toBoolean()
            "webSearch" -> webSearch = value.toString().toBoolean()
            else -> throw ConfigurationException("Unknown knowledge-base setting: $key")
        }
    }

    fun ingest(file: File) {
        var source: File
        val entry = lock.write {
            // Make sure the file is not already ingested
            val index = entries()
            if (index.any { it.name == file.name }) {
                throw FileAlreadyIngestedException("${file.name} is already ingest")
            }

            // Store the file into the source directory
            source = addToSource(file)

            // Update the index
            LOGGER.info("Adding ${file.name} to the knowledge base index")
            val prefix = context.home.absolutePath.length + 1
            val entry = KBEntry(
                name = file.name,
                source = source.absolutePath.substring(prefix),
                type = KBEntryType.FILE,
                status = KBEntryStatus.PROCESSING,
            )
            writeIndex(index + entry)

            entry
        }

        // Process the file asynchronously
        executor.submit {
            try {
                processAsync(source.name, source)
            } catch (ex: FileNotFoundException) {
                LOGGER.error("Failed to process ${file.name} asynchronously", ex)
                delete(entry.name) // File not found, remove from index
            } catch (ex: Exception) {
                LOGGER.error("Failed to process ${source.name} asynchronously", ex)
                val xentry = entry.copy(
                    error = ex.message ?: "Unexpected error",
                    status = KBEntryStatus.ERROR,
                )
                update(xentry) // Update the entry with the error status
            }
        }
    }

    fun ingest(url: URL) {
        val name = url.toString()
        val entry = lock.write {
            // Make sure the file is not already ingested
            val index = entries()
            if (index.any { it.name == name }) {
                throw FileAlreadyIngestedException("$name is already ingest")
            }

            // Update the index
            LOGGER.info("Adding $name to the knowledge base index")
            val entry = KBEntry(
                name = name,
                url = url.toString(),
                type = KBEntryType.LINK,
                status = KBEntryStatus.PROCESSING,
            )
            writeIndex(index + entry)
            entry
        }

        // Process the LINK asynchronously
        executor.submit {
            try {
                processAsync(name, url)
            } catch (ex: Exception) {
                LOGGER.error("Failed to process $name asynchronously", ex)
                val xentry = entry.copy(
                    error = ex.message ?: "Unexpected error",
                    status = KBEntryStatus.ERROR,
                )
                try {
                    update(xentry)
                } catch (e: Exception) {
                    LOGGER.error("Failed to update entry $name with error message", e)
                }
            }
        }
    }

    private fun processAsync(name: String, url: URL) {
        LOGGER.info("Downloading $url")
        val file = URLUtil.fetch(url)
        val source = addToSource(file)

        processAsync(name, source)
    }

    private fun processAsync(name: String, source: File) {
        // Convert to markdown
        val md = convertToMarkdown(source)

        // Summarize
        val result = summarize(md)
        val summary = File(getDataDir(), "${source.name}.summary.md")
        summary.writeText(result.summary)

        // Update the index
        lock.write {
            val entry = entries().find { it.name == name }
            if (entry != null) {
                LOGGER.info("Adding knowledge base entry $source.name")
                val xentry = entry.copy(
                    scope = result.scope,
                    keywords = result.keywords,
                    source = source.absolutePath.substring(context.home.absolutePath.length + 1),
                    summary = summary.absolutePath.substring(context.home.absolutePath.length + 1),
                    raw = md.absolutePath.substring(context.home.absolutePath.length + 1),
                    status = KBEntryStatus.READY,
                )
                update(xentry)
            } else {
                LOGGER.warn("Knowledge base entry ${source.name} not found")
            }
        }
    }

    fun delete(name: String): KBEntry? {
        lock.write {
            val index = entries()
            val entry = index.find { it.name == name }
            if (entry == null) {
                LOGGER.warn("Knowledge base entry $name not found")
                return null
            }

            // Remove from index
            LOGGER.info("Removing $name from index")
            val xindex = index.filter { it.name != name }
            writeIndex(xindex)

            // Delete local files
            LOGGER.info("Deleting files")
            entry.source?.let { deleteFile(entry.source) }
            entry.raw?.let { deleteFile(entry.raw) }
            entry.summary?.let { deleteFile(entry.summary) }
            return entry
        }
    }

    fun entries(): List<KBEntry> {
        lock.read {
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
    }

    private fun update(entry: KBEntry) {
        lock.write {
            val items = entries().filter { it.name != entry.name }
            writeIndex(items + entry)
        }
    }

    private fun deleteFile(path: String) {
        val file = File(context.home, path)
        if (file.exists()) {
            LOGGER.info("Deleting $path")
            file.delete()
        } else {
            LOGGER.warn("File $path does not exist")
        }
    }

    private fun writeIndex(entries: List<KBEntry>) {
        val file = getIndexFile()
        file.parentFile.mkdirs()
        val json = context.jsonMapper.writeValueAsString(entries)
        file.writeText(json)
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

        val content = response.choices.firstOrNull()?.content?.trim()
            ?: throw IllegalStateException("No content returned from LLM")

        val json = if (content.startsWith("```json")) {
            val start = content.indexOf("```json") + 7
            val end = content.lastIndexOf("```")
            content.substring(start, end).trim()
        } else {
            content
        }
        return context.jsonMapper.readValue(json, KBSumaryResult::class.java)
    }

    private fun addToSource(file: File): File {
        val source = File(getSourceDir(), file.name)
        source.parentFile.mkdirs()

        LOGGER.info("Storing ${file.absolutePath} into ${source.absolutePath}")
        file.copyTo(source, overwrite = true)
        return source
    }

    private fun convertToMarkdown(source: File): File {
        LOGGER.info("Converting ${source.absolutePath} to markdown")

        // Convert to markdown using pandoc or markitdown
        val converter = MarkdownConverter(fileService = context.fileService)
        val content = converter.convert(source)

        // Store
        val md = File(getDataDir(), "${source.name}.md")
        md.parentFile.mkdirs()
        md.writeText(content)
        return md
    }

    private fun getSourceDir(): File {
        val dir = File(getRootDir(), "/source")
        return dir
    }

    private fun getDataDir(): File {
        val dir = File(getRootDir(), "/data")
        return dir
    }

    private fun getIndexFile(): File {
        return File(getRootDir(), "index.json")
    }

    private fun getRootDir(): File {
        val dir = File(context.home, "kb")
        return dir
    }
}
