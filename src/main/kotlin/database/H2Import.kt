package database

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.sql.Types

internal enum class OverlapResolution { REQUIRE_CONFIRMATION, EARLIER, LATER }

internal class AmbiguousLegacyTime : IllegalStateException("旧库中有夏令时回拨期间的时间，请选择按较早或较晚的偏移解释。")

internal fun <T> withLegacyDatabase(path: Path, block: (Connection) -> T): T {
    val actual = path.toRealPath()
    val fileName = actual.fileName.toString()
    require(fileName.endsWith(".mv.db")) { "请选择 H2 的 .mv.db 文件" }
    val base = actual.resolveSibling(fileName.removeSuffix(".mv.db")).toString()
    require(';' !in base) { "旧库路径包含分号，请先退出旧应用并将文件复制到其他目录" }
    return DriverManager.getConnection("jdbc:h2:file:$base;IFEXISTS=TRUE;ACCESS_MODE_DATA=r").use(block)
}

internal fun snapshotLegacy(source: Path, backup: Path): String = withLegacyDatabase(source) {
    val digest = sha256(source)
    Files.copy(source, backup)
    forceFile(backup)
    check(digest == sha256(backup) && digest == sha256(source)) { "旧库在备份期间发生变化，请退出旧应用后重试" }
    digest
}

private enum class FieldKind { NUMBER, TEXT, TIME, DURATION }

private data class Field(val name: String, val kind: FieldKind)

private data class LegacyTable(
    val source: String,
    val target: String,
    val fields: List<Field>,
    val keys: List<String>,
)

private fun number(name: String) = Field(name, FieldKind.NUMBER)
private fun text(name: String) = Field(name, FieldKind.TEXT)
private fun time(name: String) = Field(name, FieldKind.TIME)

private val timestamps = listOf(time("create_time"), time("last_updated_time"))
private val legacyTables = listOf(
    LegacyTable("accounts", "accounts", listOf(number("id"), text("name")) + timestamps, listOf("id")),
    LegacyTable("weapons", "weapons", listOf(number("id"), number("account_id"), text("name")) + timestamps, listOf("id")),
    LegacyTable(
        "bonusrecords", "bonus_records",
        listOf(number("id"), number("account_id"), time("start_time"), time("end_time"),
            Field("duration", FieldKind.DURATION), number("score"), text("remark")) + timestamps,
        listOf("id"),
    ),
    LegacyTable(
        "bonusrecordweapons", "bonus_record_weapons",
        listOf(number("record_id"), number("weapon_id")), listOf("record_id", "weapon_id"),
    ),
)

internal fun importLegacy(
    source: Connection,
    target: Connection,
    zone: ZoneId,
    overlap: OverlapResolution,
    checkCancelled: () -> Unit,
) {
    val tables = source.metaData.getTables(null, "BONUS", "%", arrayOf("BASE TABLE", "TABLE")).use { rows ->
        buildMap {
            while (rows.next()) put(rows.getString("TABLE_NAME").lowercase(), rows.getString("TABLE_NAME"))
        }
    }
    check(tables.keys == legacyTables.map { it.source }.toSet()) { "旧库包含无法识别的表结构，请保留备份" }

    target.autoCommit = false
    try {
        for (table in legacyTables) {
            val sourceName = checkNotNull(tables[table.source])
            transferTable(source, target, sourceName, table, zone, overlap, checkCancelled)
            if (table.keys == listOf("id")) {
                carryIdentity(source, target, sourceName, table.target)
            }
        }
        target.createStatement().use { statement ->
            statement.executeQuery(
                """SELECT 1 FROM bonus_record_weapons l
                    JOIN bonus_records r ON l.record_id = r.id
                    JOIN weapons w ON l.weapon_id = w.id
                    WHERE w.account_id <> r.account_id LIMIT 1""",
            ).use { check(!it.next()) { "发现跨账号的武器关联，请保留备份" } }
        }
        target.commit()
    } catch (error: Exception) {
        target.rollback()
        throw error
    } finally {
        target.autoCommit = true
    }
}

private fun transferTable(
    source: Connection,
    target: Connection,
    tableName: String,
    table: LegacyTable,
    zone: ZoneId,
    overlap: OverlapResolution,
    checkCancelled: () -> Unit,
) {
    val columns = source.metaData.getColumns(null, "BONUS", tableName, "%").use { rows ->
        buildMap {
            while (rows.next()) put(rows.getString("COLUMN_NAME").lowercase(), rows.getString("COLUMN_NAME"))
        }
    }
    val expected = table.fields.map { it.name }.toSet()
    val missingRemark = table.source == "bonusrecords" && columns.keys == expected - "remark"
    check(columns.keys == expected || missingRemark) { "旧库的 $tableName 字段无法识别" }

    val projection = table.fields.joinToString { field ->
        columns[field.name]?.let(::quoteIdentifier) ?: "CAST('' AS VARCHAR)"
    }
    val sourceQuery = "SELECT $projection FROM BONUS.${quoteIdentifier(tableName)} " +
        "ORDER BY " + table.keys.joinToString { quoteIdentifier(columns.getValue(it)) }
    val names = table.fields.joinToString { it.name }
    val parameters = table.fields.joinToString { "?" }
    val targetQuery = "SELECT $names FROM ${table.target} ORDER BY ${table.keys.joinToString()}"
    target.prepareStatement("INSERT INTO ${table.target} ($names) VALUES ($parameters)").use { insert ->
        source.createStatement().use { statement ->
            statement.executeQuery(sourceQuery).use { rows ->
                while (rows.next()) {
                    checkCancelled()
                    val values = table.fields.mapIndexed { index, field -> readField(rows, index + 1, field, zone, overlap) }
                    values.forEachIndexed { index, value -> insert.setObject(index + 1, value) }
                    insert.executeUpdate()
                }
            }
            statement.executeQuery(sourceQuery).use { sourceRows ->
                target.createStatement().use { verify ->
                    verify.executeQuery(targetQuery).use { targetRows ->
                        while (sourceRows.next()) {
                            checkCancelled()
                            check(targetRows.next()) { "校验失败，${table.target} 缺少记录" }
                            table.fields.forEachIndexed { index, field ->
                                val position = index + 1
                                val expectedValue = readField(sourceRows, position, field, zone, overlap)
                                val actualValue = when (expectedValue) {
                                    is Long -> targetRows.getLong(position)
                                    else -> targetRows.getString(position)
                                }
                                check(!targetRows.wasNull() && expectedValue == actualValue) {
                                    "校验失败，${table.target} 的 ${field.name} 不一致"
                                }
                            }
                        }
                        check(!targetRows.next()) { "校验失败，${table.target} 存在多余记录" }
                    }
                }
            }
        }
    }
}

private fun readField(
    rows: ResultSet,
    position: Int,
    field: Field,
    zone: ZoneId,
    overlap: OverlapResolution,
): Any = when (field.kind) {
    FieldKind.TEXT -> checkNotNull(rows.getString(position))
    FieldKind.NUMBER -> {
        val value = rows.getBigDecimal(position)?.longValueExact() ?: error("旧库存在空数值")
        if (field.name == "score") check(value in 0..UInt.MAX_VALUE.toLong())
        value
    }
    FieldKind.DURATION -> when (rows.metaData.getColumnType(position)) {
        Types.BIGINT, Types.INTEGER, Types.NUMERIC, Types.DECIMAL ->
            checkNotNull(rows.getBigDecimal(position)).longValueExact()
        else -> checkNotNull(rows.getObject(position, Duration::class.java)).toNanos()
    }
    FieldKind.TIME -> {
        val instant = when (rows.metaData.getColumnType(position)) {
            Types.TIMESTAMP_WITH_TIMEZONE -> rows.getObject(position, OffsetDateTime::class.java).toInstant()
            Types.TIMESTAMP -> {
                val time = rows.getObject(position, LocalDateTime::class.java)
                val offsets = zone.rules.getValidOffsets(time)
                val offset = when (offsets.size) {
                    0 -> error("旧库时间位于夏令时跳跃区间，请重新确认旧电脑的存储时区")
                    1 -> offsets.single()
                    else -> when (overlap) {
                        OverlapResolution.REQUIRE_CONFIRMATION -> throw AmbiguousLegacyTime()
                        OverlapResolution.EARLIER -> offsets.first()
                        OverlapResolution.LATER -> offsets.last()
                    }
                }
                time.toInstant(offset)
            }
            else -> error("旧库存在无法识别的时间列")
        }
        encodeInstant(instant)
    }
}

private fun carryIdentity(source: Connection, target: Connection, sourceName: String, table: String) {
    val base = source.prepareStatement(
        """SELECT IDENTITY_BASE, IDENTITY_INCREMENT FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'BONUS' AND TABLE_NAME = ? AND UPPER(COLUMN_NAME) = 'ID'""",
    ).use { query ->
        query.setString(1, sourceName)
        query.executeQuery().use { rows ->
            check(rows.next())
            val next = rows.getLong(1)
            check(!rows.wasNull() && rows.getLong(2) == 1L) { "旧库使用了无法保留的主键序列" }
            next
        }
    }
    val highWater = Math.subtractExact(base, 1)
    target.prepareStatement("UPDATE sqlite_sequence SET seq = max(seq, ?) WHERE name = ?").use { query ->
        query.setLong(1, highWater)
        query.setString(2, table)
        if (query.executeUpdate() == 0) {
            target.prepareStatement("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)").use { insert ->
                insert.setString(1, table)
                insert.setLong(2, highWater.coerceAtLeast(0))
                insert.executeUpdate()
            }
        }
    }
}

private fun quoteIdentifier(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
