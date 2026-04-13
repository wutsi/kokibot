package com.wutsi.kokibot.service.file.extractor

import com.wutsi.kokibot.service.file.TextExtractor
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.File

class XLSTextExtractor : TextExtractor {
    override fun extract(file: File): String {
        val fis = file.inputStream()
        val buffer = StringBuilder()
        fis.use {
            val workbook = HSSFWorkbook(fis)
            workbook.use {
                for (i in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(i)
                    buffer.append("--- START OF SHEET: ${sheet.sheetName} (Index: $i) ---\n")
                    buffer.append(convert(sheet))
                    buffer.append("--- END OF SHEET: ${sheet.sheetName} ---\n")
                }
            }
        }
        return buffer.toString()
    }

    private fun convert(sheet: HSSFSheet): String {
        val formatter = DataFormatter()
        val buffer = StringBuilder()
        for (row in sheet) {
            val line = mutableListOf<String>()
            for (cell in row) {
                val text = formatter.formatCellValue(cell)
                if (text.contains("\"")) {
                    val escapedText = text.replace("\"", "\"\"")
                    line.add("\"$escapedText\"")
                } else {
                    line.add(text)
                }
            }
            buffer.append(line.joinToString(",") + "\n")
        }
        return buffer.toString()
    }
}
