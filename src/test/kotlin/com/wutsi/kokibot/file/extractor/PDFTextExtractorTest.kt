package com.wutsi.kokibot.file.extractor

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class PDFTextExtractorTest {
    val extractor = PDFTextExtractor()

    @Test
    fun extract() {
        val file = this::class.java.getResource("/file/sample.pdf")!!.file
        val text = extractor.extract(file = java.io.File(file))

        assertTrue(
            text.contains(
                "This sample consists of a simple form containing four distinct fields"
            )
        )
    }
}
