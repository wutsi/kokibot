package com.wutsi.kokibot.file.extractor

import com.wutsi.kokibot.file.TextExtractor
import org.apache.poi.hwpf.HWPFDocument
import java.io.File

class DOCTextExtractor : TextExtractor {
    override fun extract(file: File): String {
        val fis = file.inputStream()
        fis.use {
            val doc = HWPFDocument(fis)
            doc.use {
                return doc.text.toString()
            }
        }
    }
}
