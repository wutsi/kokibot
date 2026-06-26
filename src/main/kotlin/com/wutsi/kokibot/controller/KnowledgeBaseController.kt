package com.wutsi.kokibot.controller

import com.wutsi.kokibot.ConfigurationException
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.service.kb.FileAlreadyIngestedException
import com.wutsi.kokibot.service.kb.KBEntryType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.net.URL

@RequestMapping("/assistants")
@RestController
class KnowledgeBaseController(private val multi: MultiBootstrap) {
    @GetMapping("/{name}/knowledge-base")
    fun get(@PathVariable name: String): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val kb = bootstrap.getContext().knowledgeBase
        return ResponseEntity.ok(
            buildMap {
                put("enabled", kb.isEnabled())
                put("exclusive", kb.isExclusive())
                put("webSearch", kb.isWebSearch())
            }
        )
    }

    @GetMapping("/{name}/knowledge-base/entries")
    fun entries(
        @PathVariable name: String,
        @RequestParam(required = false) status: String? = null,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val context = bootstrap.getContext()
        val kb = context.knowledgeBase
        val fileService = context.fileService
        val entries = kb.entries()
            .filter { entry -> status == null || entry.status.name.equals(status, ignoreCase = true) }
            .map { entry ->
                mapOf(
                    "filename" to entry.name,
                    "displayName" to if (entry.type == KBEntryType.LINK && entry.url != null) {
                        URL(entry.url).file
                            .removeSuffix("/")
                            .ifEmpty { null }
                            ?.let { File(it).name }
                            ?: entry.name
                    } else {
                        entry.name
                    },
                    "scope" to entry.scope,
                    "keywords" to entry.keywords,
                    "url" to if (entry.type == KBEntryType.FILE && entry.source != null) {
                        fileService.urlPath(File(context.home, entry.source).absolutePath)
                    } else {
                        entry.url
                    },
                    "status" to entry.status,
                    "type" to entry.type,
                    "error" to entry.error,
                )
            }
        return ResponseEntity.ok(entries)
    }

    @PostMapping("/{name}/knowledge-base/upload")
    fun upload(
        @PathVariable name: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val kb = bootstrap.getContext().knowledgeBase
        val fileService = bootstrap.getContext().fileService
        val originalFilename = file.originalFilename ?: "upload"
        val tmp = fileService.createTempFile(originalFilename)
        return try {
            file.transferTo(tmp)
            kb.ingest(tmp)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: FileAlreadyIngestedException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (e.message ?: "File already ingested")))
        } finally {
            tmp.delete()
        }
    }

    @PostMapping("/{name}/knowledge-base/link")
    fun addLink(
        @PathVariable name: String,
        @RequestBody request: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any>> {
        val url = request["url"] as? String ?: return ResponseEntity.badRequest().build()
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val kb = bootstrap.getContext().knowledgeBase
        return try {
            kb.ingest(URL(url))
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: FileAlreadyIngestedException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (e.message ?: "File already ingested")))
        }
    }

    @PostMapping("/{name}/knowledge-base/settings")
    fun set(
        @PathVariable name: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = getBootstrap(name) ?: return ResponseEntity.notFound().build()
        val key = body["key"] as? String ?: return ResponseEntity.badRequest().build()
        val value = body["value"] ?: return ResponseEntity.badRequest().build()
        return try {
            bootstrap.set("knowledge-base.$key", value)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: ConfigurationException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid setting")))
        }
    }

    @GetMapping("/{name}/knowledge-base/entries/delete")
    fun delete(
        @PathVariable name: String,
        @RequestParam filename: String,
    ) {
        val bootstrap = getBootstrap(name) ?: return
        val kb = bootstrap.getContext().knowledgeBase
        kb.delete(filename)
    }

    private fun getBootstrap(name: String) =
        multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
}
