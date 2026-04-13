package com.wutsi.kokibot.service.file.extractor

import com.wutsi.kokibot.service.file.extractor.XLSXTextExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class XLSXTextExtractorTest {
    val extractor = XLSXTextExtractor()

    @Test
    fun extract() {
        val file = this::class.java.getResource("/file/sample.xlsx")!!.file
        val text = extractor.extract(file = File(file))

        assertEquals(
            """
                --- START OF SHEET: Main (Index: 0) ---
                Name,Age,Description,Percentage
                Ray Sponsible,30,"This is a ""stuff",30%
                Roger Milla,69,Simply the best,99%
                --- END OF SHEET: Main ---
                --- START OF SHEET: Rules (Index: 1) ---
                Rules
                Rule A
                Rule B
                Rule C
                --- END OF SHEET: Rules ---

            """.trimIndent(),
            text
        )
    }
}
