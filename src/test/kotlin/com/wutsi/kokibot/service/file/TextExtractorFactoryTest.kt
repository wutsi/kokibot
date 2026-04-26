package com.wutsi.kokibot.service.file

import com.wutsi.kokibot.service.UnsupportedMimeTypeException
import com.wutsi.kokibot.service.file.extractor.DOCTextExtractor
import com.wutsi.kokibot.service.file.extractor.DOCXTextExtractor
import com.wutsi.kokibot.service.file.extractor.PDFTextExtractor
import com.wutsi.kokibot.service.file.extractor.XLSTextExtractor
import com.wutsi.kokibot.service.file.extractor.XLSXTextExtractor
import org.junit.jupiter.api.Test

class TextExtractorFactoryTest {
    val factory = TextExtractorFactory()

    @Test
    fun pdf() {
        val extractor = factory.create("application/pdf")
        assert(extractor is PDFTextExtractor)
    }

    @Test
    fun doc() {
        val extractor = factory.create("application/msword")
        assert(extractor is DOCTextExtractor)
    }

    @Test
    fun docx() {
        val extractor = factory.create("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        assert(extractor is DOCXTextExtractor)
    }

    @Test
    fun xls() {
        val extractor = factory.create("application/vnd.ms-excel")
        assert(extractor is XLSTextExtractor)
    }

    @Test
    fun xlsx() {
        val extractor = factory.create("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        assert(extractor is XLSXTextExtractor)
    }

    @Test
    fun unsupported() {
        try {
            factory.create("application/zip")
        } catch (ex: UnsupportedMimeTypeException) {
            assert(ex.message == "application/zip")
        }
    }
}
