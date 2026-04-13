package com.wutsi.kokibot.file.extractor

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class DOCTextExtractorTest {
    val extractor = DOCTextExtractor()

    @Test
    fun extract() {
        val file = this::class.java.getResource("/file/sample.doc")!!.file
        val text = extractor.extract(file = File(file))

        assertTrue(
            text.contains(
                "Longtemps, je me suis couché de bonne heure."
            )
        )
    }
}
