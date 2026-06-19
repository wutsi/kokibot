package com.wutsi.kokibot.servlet

import com.wutsi.kokibot.MultiBootstrap
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.file.Files

class FilesServlet(private val multi: MultiBootstrap) : HttpServlet() {

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val raw = req.pathInfo?.trimStart('/') ?: run {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }

        val inline = raw.startsWith("preview/")
        val path = if (inline) raw.removePrefix("preview/") else raw

        val i = path.indexOf("/")
        if (i < 0) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }

        val name = path.substring(0 until i)
        val filePath = path.substring(i + 1)

        val bootstrap = multi.bootstraps.firstOrNull { it.getContext().assistant.name == name }
            ?: run {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }

        val file = bootstrap.getContext().fileService.urlPathFile(filePath)
        if (!file.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }

        val contentType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
        val disposition = if (inline) "inline" else "attachment"

        resp.contentType = contentType
        resp.setHeader("Content-Disposition", "$disposition; filename=\"${file.name}\"")
        resp.setContentLengthLong(file.length())

        file.inputStream().use { input ->
            resp.outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }
}
