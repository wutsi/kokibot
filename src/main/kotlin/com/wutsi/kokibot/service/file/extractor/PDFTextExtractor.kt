package com.wutsi.kokibot.service.file.extractor

import com.wutsi.kokibot.service.file.TextExtractor
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

class PDFTextExtractor : TextExtractor {
    override fun extract(file: File): String {
        val doc = Loader.loadPDF(file)
        val stripper = PDFTextStripper()
        stripper.startPage = 1
        stripper.endPage = doc.numberOfPages
        return stripper.getText(doc)
    }
}
