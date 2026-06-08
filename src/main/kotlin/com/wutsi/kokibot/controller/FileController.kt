package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import java.nio.file.Files

@Controller
@RequestMapping("/files")
class FileController(private val multi: MultiBootstrap) {
    @GetMapping("{path}")
    fun files(
        @PathVariable path: String
    ): ResponseEntity<ByteArray> {
        val i = path.indexOf("|")
        val name = path.substring(0 until i)
        val filePath = path.substring(i + 1).replace("|", "/")

        val bootstrap = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
            ?: return ResponseEntity.notFound().build()

        val file = bootstrap.getContext().fileService.urlPathFile(filePath)
        if (!file.exists()) {
            return ResponseEntity.notFound().build()
        }

        val contentType = Files.probeContentType(file.toPath())
        val headers = org.springframework.http.HttpHeaders()
        headers.contentDisposition = ContentDisposition.builder("attachment")
            .filename(file.name)
            .build()
        headers.contentType = contentType?.let { MediaType.valueOf(contentType) }
            ?: MediaType.APPLICATION_OCTET_STREAM
        headers.contentLength = file.length()

        return ResponseEntity
            .ok()
            .headers(headers)
            .body(file.inputStream().readAllBytes())
    }
}
