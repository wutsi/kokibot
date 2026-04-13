package com.wutsi.kokibot.service.file.extractor

import com.wutsi.kokibot.service.file.TextExtractor
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File

class DOCXTextExtractor : TextExtractor {
    override fun extract(file: File): String {
        val fis = file.inputStream()
        fis.use {
            val doc = XWPFDocument(fis)
            doc.use {
                return XWPFWordExtractor(doc).use { extractor ->
                    extractor.text
                }
            }
        }
    }
}
