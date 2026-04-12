package com.wutsi.kokibot.file

import com.wutsi.kokibot.exception.UnsupportedMimeTypeException
import com.wutsi.kokibot.file.extractor.DOCTextExtractor
import com.wutsi.kokibot.file.extractor.DOCXTextExtractor
import com.wutsi.kokibot.file.extractor.PDFTextExtractor
import com.wutsi.kokibot.file.extractor.XLSTextExtractor
import com.wutsi.kokibot.file.extractor.XLSXTextExtractor

class TextExtractorFactory {
    private val extractors = mapOf(
        "application/pdf" to PDFTextExtractor(),
        "application/msword" to DOCTextExtractor(),
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to DOCXTextExtractor(),
        "application/vnd.ms-excel" to XLSTextExtractor(),
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to XLSXTextExtractor(),
    )

    fun create(mimeType: String): TextExtractor {
        return extractors[mimeType]
            ?: throw UnsupportedMimeTypeException(mimeType)
    }
}
