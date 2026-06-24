package com.wutsi.kokibot.service

import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.dto.AssistantSummaryDto
import org.springframework.stereotype.Service

@Service
class AssistantService(private val multi: MultiBootstrap) {
    fun list(): List<AssistantSummaryDto> {
        return multi.bootstraps.map { bootstrap ->
            val assistant = bootstrap.getContext().assistant
            AssistantSummaryDto(
                name = assistant.name,
                description = assistant.description,
            )
        }
    }
}
