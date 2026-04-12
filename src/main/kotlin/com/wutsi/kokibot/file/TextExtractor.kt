package com.wutsi.kokibot.file

import java.io.File

interface TextExtractor {
    fun extract(file: File): String
}
