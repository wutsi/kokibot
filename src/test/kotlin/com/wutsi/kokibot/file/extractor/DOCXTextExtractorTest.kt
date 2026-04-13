package com.wutsi.kokibot.file.extractor

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class DOCXTextExtractorTest {
    val extractor = DOCXTextExtractor()

    @Test
    fun extract() {
        val file = this::class.java.getResource("/file/sample.docx")!!.file
        val text = extractor.extract(file = File(file))

        assertTrue(
            text.contains(
                "J’appuyais tendrement mes joues contre les belles joues de l’oreiller qui, pleines et fraîches, sont comme les joues de notre enfance."
            )
        )
    }
}
