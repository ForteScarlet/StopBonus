package view.account.record

import database.entity.BonusRecordView
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class BonusRecordExporterTest {
    @Test
    fun excelExportPreservesLocalDatesAndRecordValues() {
        val start = Instant.parse("2026-11-25T16:30:00Z")
        val record = BonusRecordView(
            id = 1,
            startTime = start,
            endTime = start.plusSeconds(90),
            duration = Duration.ofSeconds(90),
            score = 8u,
            remark = "测试备注",
            weapons = emptyList(),
        )

        val bytes = BonusRecordExporter.bytes(BonusRecordExportFormat.EXCEL, listOf(record), ZoneId.of("Asia/Shanghai"))

        XSSFWorkbook(bytes.inputStream()).use { workbook ->
            val row = workbook.getSheet("奖励记录").getRow(1)
            assertEquals(LocalDateTime.of(2026, 11, 26, 0, 30), row.getCell(0).localDateTimeCellValue)
            assertEquals(LocalDateTime.of(2026, 11, 26, 0, 31, 30), row.getCell(1).localDateTimeCellValue)
            assertEquals("1分30秒", row.getCell(2).stringCellValue)
            assertEquals(90.0, row.getCell(3).numericCellValue)
            assertEquals(8.0, row.getCell(4).numericCellValue)
            assertEquals("测试备注", row.getCell(6).stringCellValue)
        }
    }
}
