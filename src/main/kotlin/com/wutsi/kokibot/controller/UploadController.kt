package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class UploadController(private val multi: MultiBootstrap) {
    @PostMapping("/upload")
    fun upload(
        @RequestParam name: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Map<String, Any>> {
        val bootstrap = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
            ?: return ResponseEntity.notFound().build()

        val fileService = bootstrap.getContext().fileService
        val originalFilename = file.originalFilename ?: "upload"
        val tmp = fileService.createTempFile(originalFilename)
        file.inputStream.use { input ->
            tmp.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return ResponseEntity.ok(
            mapOf(
                "name" to originalFilename,
                "path" to tmp.absolutePath,
                "size" to tmp.length()
            )
        )
    }
}
