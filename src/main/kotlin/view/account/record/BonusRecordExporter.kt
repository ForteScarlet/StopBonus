package view.account.record

import database.entity.BonusRecordView
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class BonusRecordExportFormat(
    val extension: String,
) {
    CSV("csv"),
    EXCEL("xlsx"),
}

internal object BonusRecordExporter {
    private const val CSV_FIELD_SEPARATOR = ','
    private const val CSV_LINE_SEPARATOR = "\r\n"
    private const val UTF8_BOM_BYTE_1 = 0xEF
    private const val UTF8_BOM_BYTE_2 = 0xBB
    private const val UTF8_BOM_BYTE_3 = 0xBF
    private const val EXCEL_SHEET_NAME = "奖励记录"
    private const val EXCEL_DATE_FORMAT = "yyyy-mm-dd hh:mm:ss"
    private const val EXCEL_MAX_COLUMN_WIDTH = 60 * 256
    private const val EXCEL_DATE_COLUMN_WIDTH = 24 * 256
    private const val DURATION_SECONDS_PER_MINUTE = 60L
    private const val DURATION_MINUTES_PER_HOUR = 60L
    private const val DURATION_HOURS_PER_DAY = 24L

    private val exportDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val headers = BonusRecordExportColumn.entries.map { it.header }

    fun bytes(
        format: BonusRecordExportFormat,
        records: List<BonusRecordView>,
        zoneId: ZoneId,
    ): ByteArray {
        val sortedRecords = records.sortedWith(
            compareBy<BonusRecordView> { it.startTime }
                .thenBy { it.entityID.value },
        )
        return when (format) {
            BonusRecordExportFormat.CSV -> csvBytes(sortedRecords, zoneId)
            BonusRecordExportFormat.EXCEL -> excelBytes(sortedRecords, zoneId)
        }
    }

    private fun csvBytes(
        records: List<BonusRecordView>,
        zoneId: ZoneId,
    ): ByteArray {
        val csv = buildString {
            appendCsvRow(headers)
            records.forEach { record ->
                appendCsvRow(record.toExportFields(zoneId))
            }
        }
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(UTF8_BOM_BYTE_1.toByte(), UTF8_BOM_BYTE_2.toByte(), UTF8_BOM_BYTE_3.toByte()))
            write(csv.toByteArray(Charsets.UTF_8))
        }.toByteArray()
    }

    private fun StringBuilder.appendCsvRow(fields: List<String>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) append(CSV_FIELD_SEPARATOR)
            append(escapeCsvField(field))
        }
        append(CSV_LINE_SEPARATOR)
    }

    private fun escapeCsvField(field: String): String {
        if (field.none { it == CSV_FIELD_SEPARATOR || it == '"' || it == '\r' || it == '\n' }) {
            return field
        }
        return buildString {
            append('"')
            field.forEach { character ->
                if (character == '"') append('"')
                append(character)
            }
            append('"')
        }
    }

    private fun excelBytes(
        records: List<BonusRecordView>,
        zoneId: ZoneId,
    ): ByteArray = ByteArrayOutputStream().use { output ->
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(EXCEL_SHEET_NAME)
            val headerStyle = workbook.createHeaderStyle()
            val dateStyle = workbook.createDateStyle()

            sheet.createRow(0).also { headerRow ->
                headers.forEachIndexed { index, header ->
                    headerRow.createCell(index).apply {
                        setCellValue(header)
                        cellStyle = headerStyle
                    }
                }
            }

            records.forEachIndexed { rowIndex, record ->
                val row = sheet.createRow(rowIndex + 1)
                val localStart = record.startTime.atZone(zoneId).toLocalDateTime()
                val localEnd = record.endTime.atZone(zoneId).toLocalDateTime()
                row.createCell(BonusRecordExportColumn.START_TIME.index).apply {
                    setCellValue(localStart)
                    cellStyle = dateStyle
                }
                row.createCell(BonusRecordExportColumn.END_TIME.index).apply {
                    setCellValue(localEnd)
                    cellStyle = dateStyle
                }
                row.createCell(BonusRecordExportColumn.DURATION.index).setCellValue(formatDuration(record.duration))
                row.createCell(BonusRecordExportColumn.DURATION_SECONDS.index).setCellValue(record.duration.seconds.toDouble())
                row.createCell(BonusRecordExportColumn.SCORE.index).setCellValue(record.score.toDouble())
                row.createCell(BonusRecordExportColumn.WEAPONS.index).setCellValue(record.weapons.joinToString("、") { it.name })
                row.createCell(BonusRecordExportColumn.REMARK.index).setCellValue(record.remark)
            }

            sheet.createFreezePane(0, 1)
            BonusRecordExportColumn.entries.forEach { column ->
                val index = column.index
                sheet.autoSizeColumn(index)
                sheet.setColumnWidth(index, columnWidth(sheet.getColumnWidth(index), column))
            }
            workbook.write(output)
        }
        output.toByteArray()
    }

    private fun XSSFWorkbook.createHeaderStyle(): XSSFCellStyle {
        val headerFont: Font = createFont().apply { bold = true }
        return createCellStyle().apply { setFont(headerFont) }
    }

    private fun XSSFWorkbook.createDateStyle(): XSSFCellStyle = createCellStyle().apply {
        dataFormat = creationHelper.createDataFormat().getFormat(EXCEL_DATE_FORMAT)
    }

    private fun BonusRecordView.toExportFields(zoneId: ZoneId): List<String> = listOf(
        startTime.formatForExport(zoneId),
        endTime.formatForExport(zoneId),
        formatDuration(duration),
        duration.seconds.toString(),
        score.toString(),
        weapons.joinToString("、") { it.name },
        remark,
    )

    private fun columnWidth(currentWidth: Int, column: BonusRecordExportColumn): Int = when (column) {
        BonusRecordExportColumn.START_TIME,
        BonusRecordExportColumn.END_TIME -> maxOf(currentWidth, EXCEL_DATE_COLUMN_WIDTH)
        else -> currentWidth
    }.coerceAtMost(EXCEL_MAX_COLUMN_WIDTH)

    private fun Instant.formatForExport(zoneId: ZoneId): String =
        atZone(zoneId).format(exportDateTimeFormatter)

    private fun formatDuration(duration: Duration): String {
        val negative = duration.isNegative
        val positiveDuration = duration.abs()
        val totalSeconds = positiveDuration.seconds
        val days = totalSeconds / (DURATION_HOURS_PER_DAY * DURATION_MINUTES_PER_HOUR * DURATION_SECONDS_PER_MINUTE)
        val hours = totalSeconds / (DURATION_MINUTES_PER_HOUR * DURATION_SECONDS_PER_MINUTE) % DURATION_HOURS_PER_DAY
        val minutes = totalSeconds / DURATION_SECONDS_PER_MINUTE % DURATION_MINUTES_PER_HOUR
        val seconds = totalSeconds % DURATION_SECONDS_PER_MINUTE

        return buildString {
            if (negative) append('-')
            if (days > 0) append(days).append('天')
            if (hours > 0) append(hours).append('时')
            if (minutes > 0 || days > 0 || hours > 0) append(minutes).append('分')
            append(seconds).append('秒')
        }
    }
}

private enum class BonusRecordExportColumn(
    val header: String,
) {
    START_TIME("开始时间"),
    END_TIME("结束时间"),
    DURATION("持续时长"),
    DURATION_SECONDS("持续时长（秒）"),
    SCORE("评分"),
    WEAPONS("使用道具"),
    REMARK("备注");

    val index: Int
        get() = ordinal
}
