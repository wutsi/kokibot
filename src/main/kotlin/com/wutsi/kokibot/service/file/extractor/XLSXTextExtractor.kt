package com.wutsi.kokibot.service.file.extractor

import com.wutsi.kokibot.service.file.TextExtractor
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class XLSXTextExtractor : TextExtractor {
    override fun extract(file: File): String {
        val fis = file.inputStream()
        val buffer = StringWriter()
        fis.use {
            WorkbookFactory.create(fis).use { workbook ->
                PrintWriter(buffer).use { writer ->

                    for (i in 0 until workbook.numberOfSheets) {
                        val sheet = workbook.getSheetAt(i)

                        writer.println("--- START OF SHEET: ${sheet.sheetName} (Index: $i) ---")
                        writer.print(convert(sheet))
                        writer.println("--- END OF SHEET: ${sheet.sheetName} ---")
                    }
                }
            }
        }
        return buffer.toString()
    }

    private fun convert(sheet: Sheet): String {
        val formatter = DataFormatter()
        val buffer = StringWriter()
        PrintWriter(buffer).use { writer ->
            for (row in sheet) {
                val line = mutableListOf<String>()
                for (cn in 0 until row.lastCellNum) {
                    val cell = row.getCell(
                        cn,
                        Row.MissingCellPolicy.CREATE_NULL_AS_BLANK
                    )
                    val text = formatter.formatCellValue(cell)

                    // Standard CSV escaping
                    if (text.contains("\"")) {
                        val escapedText = text.replace("\"", "\"\"")
                        line.add("\"$escapedText\"")
                    } else {
                        line.add(text)
                    }
                }
                writer.println(line.joinToString(","))
            }
        }
        return buffer.toString()
    }
}
