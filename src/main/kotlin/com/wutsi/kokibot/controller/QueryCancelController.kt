package com.wutsi.kokibot.controller

import com.wutsi.kokibot.MultiBootstrap
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/assistants")
class QueryCancelController(private val multi: MultiBootstrap) {

    @PostMapping("/{name}/queries/{id}/cancel")
    fun cancel(
        @PathVariable name: String,
        @PathVariable id: String,
    ): ResponseEntity<Any> {
        val context = multi.get(name)?.getContext() ?: return ResponseEntity.notFound().build()

        context.inbox.cancel(id)
        return ResponseEntity.ok().build()
    }
}
