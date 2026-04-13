package com.wutsi.kokibot.service.file.extractor

import com.wutsi.kokibot.service.file.extractor.PDFTextExtractor
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class PDFTextExtractorTest {
    val extractor = PDFTextExtractor()

    @Test
    fun extract() {
        val file = this::class.java.getResource("/file/sample.pdf")!!.file
        val text = extractor.extract(file = File(file))

        assertTrue(
            text.contains(
                "This sample consists of a simple form containing four distinct fields"
            )
        )
    }
}
