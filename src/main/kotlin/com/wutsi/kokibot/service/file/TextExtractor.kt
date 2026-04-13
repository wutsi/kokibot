package com.wutsi.kokibot.service.file

import java.io.File

interface TextExtractor {
    fun extract(file: File): String
}
