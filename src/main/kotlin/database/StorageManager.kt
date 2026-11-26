package database

import database.sql.StopBonusDatabase
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.sql.Connection
import java.time.Instant
import java.time.ZoneId
import java.util.HexFormat
import java.util.UUID

internal const val LEGACY_FILE_NAME = "bonus.d.mv.db"

internal sealed interface StorageStatus {
    data object Empty : StorageStatus
    data class Legacy(val sources: List<Path>) : StorageStatus
    data class Pending(val preview: MigrationPreview) : StorageStatus
    data class Active(val changedLegacy: Path?) : StorageStatus
}

internal data class MigrationPreview(
    val source: Path,
    val backup: Path,
    val zone: ZoneId,
    val accounts: Long,
    val weapons: Long,
    val records: Long,
    val earliest: Instant?,
    val latest: Instant?,
)

internal enum class MigrationStage { BACKUP, IMPORT, VERIFY }

private data class StorageMetadata(
    val instanceId: String,
    val activated: Boolean,
    val source: Path?,
    val digest: String?,
    val zone: ZoneId?,
)

internal class StorageManager(
    directory: Path,
    private val legacyPaths: List<Path>,
) : AutoCloseable {
    val directory: Path = directory.toAbsolutePath().normalize()
    val backups: Path = this.directory.resolve("backups")
    private val activeFile = this.directory.resolve("bonus.sqlite")
    private val pendingFile = this.directory.resolve("bonus.sqlite.pending")
    private val receipt = this.directory.resolve("bonus.sqlite.active")
    private var lockChannel: FileChannel? = null
    private var lock: FileLock? = null

    @Synchronized
    fun inspect(): StorageStatus {
        acquireLock()
        if (exists(activeFile)) {
            val metadata = readMetadata(activeFile, mode = "rw")
            check(metadata.activated) { "活动库缺少启用标记，请从备份恢复" }
            rememberActive(metadata.instanceId)
            val changed = metadata.source?.takeIf { source ->
                Files.isRegularFile(source) && runCatching { sha256(source) != metadata.digest }.getOrDefault(true)
            }
            return StorageStatus.Active(changed)
        }
        if (exists(pendingFile)) return StorageStatus.Pending(preview(pendingFile))
        check(!exists(receipt)) { "已启用的数据库文件缺失，请从备份恢复" }
        val detected = (listOf(directory.resolve(LEGACY_FILE_NAME)) + legacyPaths)
            .map { it.toAbsolutePath().normalize() }.distinct().filter(::exists)
            .map { it.toRealPath() }.distinct()
        return if (detected.isEmpty()) StorageStatus.Empty else StorageStatus.Legacy(detected)
    }

    @Synchronized
    fun open(): DatabaseOperator {
        acquireLock()
        check(readMetadata(activeFile).activated) { "请先启用数据库" }
        return DatabaseOperator(activeFile)
    }

    @Synchronized
    fun createEmpty() {
        check(inspect() == StorageStatus.Empty) { "发现已有数据库，请先处理已有数据" }
        val id = UUID.randomUUID().toString()
        withTemporaryFile { temp ->
            createSqliteSchema(temp)
            writeMetadata(temp, StorageMetadata(id, true, null, null, null))
            validate(temp)
            publish(temp, activeFile)
            rememberActive(id)
        }
    }

    @Synchronized
    fun stage(
        path: Path,
        zone: ZoneId,
        overlap: OverlapResolution,
        onStage: (MigrationStage) -> Unit = {},
        checkCancelled: () -> Unit = {},
    ): MigrationPreview {
        acquireLock()
        check(!exists(activeFile) && !exists(receipt) && !exists(pendingFile)) { "请先处理已有的 SQLite 数据库" }
        val source = path.toRealPath()
        Files.createDirectories(backups)
        val id = UUID.randomUUID().toString()
        val backup = backups.resolve("h2-$id.mv.db")
        onStage(MigrationStage.BACKUP)
        val digest = snapshotLegacy(source, backup)
        forceDirectory(backups)
        checkCancelled()
        onStage(MigrationStage.IMPORT)
        withTemporaryFile { temp ->
            createSqliteSchema(temp)
            withLegacyDatabase(backup) { legacy ->
                connectSqlite(temp, "rw").use { target ->
                    importLegacy(legacy, target, zone, overlap, checkCancelled)
                }
            }
            onStage(MigrationStage.VERIFY)
            writeMetadata(temp, StorageMetadata(id, false, source, digest, zone))
            validate(temp)
            checkCancelled()
            publish(temp, pendingFile)
        }
        return preview(pendingFile)
    }

    @Synchronized
    fun activate() {
        acquireLock()
        check(!exists(activeFile)) { "已经存在活动数据库，不能覆盖" }
        val metadata = readMetadata(pendingFile)
        val source = checkNotNull(metadata.source)
        val backup = backups.resolve("h2-${metadata.instanceId}.mv.db")
        check(Files.isRegularFile(backup) && sha256(backup) == metadata.digest) {
            "迁移备份缺失或已变化，请重新导入后确认"
        }
        validate(pendingFile)
        withLegacyDatabase(source) {
            check(sha256(source) == metadata.digest) { "旧库已有变化，请重新导入后确认" }
            rememberActive(metadata.instanceId)
            connectSqlite(pendingFile, "rw").use { connection ->
                connection.createStatement().use { it.executeUpdate("UPDATE storage_metadata SET activated = 1 WHERE id = 1") }
            }
            publish(pendingFile, activeFile)
        }
    }

    @Synchronized
    fun discardPreview() {
        acquireLock()
        check(!exists(activeFile))
        val metadata = readMetadata(pendingFile)
        val interruptedActivation = exists(receipt)
        check(!interruptedActivation || Files.readString(receipt) == metadata.instanceId) {
            "活动库启用记录与预览不符，请保留文件"
        }
        Files.createDirectories(backups)
        publish(pendingFile, backups.resolve("preview-${metadata.instanceId}.sqlite"))
        if (interruptedActivation) {
            Files.delete(receipt)
            forceDirectory(directory)
        }
    }

    @Synchronized
    fun createBackup(destination: Path): Path {
        acquireLock()
        check(readMetadata(activeFile).activated) { "没有可备份的活动库" }
        Files.createDirectories(destination)
        val path = destination.resolve("stopbonus-${UUID.randomUUID()}.sqlite")
        withTemporaryFile(destination) { temp ->
            copyDatabase(activeFile, temp)
            validate(temp)
            publish(temp, path)
        }
        return path
    }

    @Synchronized
    fun restore(source: Path) {
        acquireLock()
        check(!exists(pendingFile)) { "请先启用或移走当前迁移预览" }
        val metadata = readMetadata(source)
        check(metadata.activated) { "请选择已启用数据库的完整 SQLite 备份" }
        validate(source)
        if (exists(activeFile) && Files.isSameFile(activeFile, source)) return
        withTemporaryFile { temp ->
            copyDatabase(source, temp)
            val restored = readMetadata(temp)
            validate(temp)
            if (exists(activeFile)) {
                check(listOf("-wal", "-shm", "-journal").none { exists(activeFile.resolveSibling(activeFile.fileName.toString() + it)) }) {
                    "活动库存在未合并的事务文件，请保留原目录并重试"
                }
                Files.createDirectories(backups)
                val preserved = backups.resolve("before-restore-${UUID.randomUUID()}.sqlite")
                Files.copy(activeFile, preserved)
                forceFile(preserved)
                forceDirectory(backups)
            }
            rememberActive(restored.instanceId)
            publish(temp, activeFile, replace = true)
        }
    }

    private fun acquireLock() {
        if (lock != null) return
        Files.createDirectories(directory)
        val channel = FileChannel.open(directory.resolve(".stopbonus.lock"), CREATE, WRITE)
        try {
            lock = channel.tryLock() ?: error("另一实例正在使用这个数据目录，请先退出后重试")
            lockChannel = channel
        } catch (error: Exception) {
            channel.close()
            if (error is java.nio.channels.OverlappingFileLockException) {
                error("另一实例正在使用这个数据目录，请先退出后重试")
            }
            throw error
        }
    }

    private fun preview(path: Path): MigrationPreview {
        val metadata = readMetadata(path, mode = "rw")
        return connectSqlite(path, "ro").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """SELECT (SELECT count(*) FROM accounts), (SELECT count(*) FROM weapons),
                        count(*), min(start_time), max(start_time) FROM bonus_records""",
                ).use { rows ->
                    check(rows.next())
                    MigrationPreview(
                        checkNotNull(metadata.source), backups.resolve("h2-${metadata.instanceId}.mv.db"),
                        checkNotNull(metadata.zone),
                        rows.getLong(1), rows.getLong(2), rows.getLong(3),
                        rows.getString(4)?.let(::decodeInstant), rows.getString(5)?.let(::decodeInstant),
                    )
                }
            }
        }
    }

    private fun rememberActive(id: String) {
        if (exists(receipt) && Files.readString(receipt) == id) return
        withTemporaryFile { temp ->
            Files.writeString(temp, id)
            publish(temp, receipt, replace = true)
        }
    }

    private fun <T> withTemporaryFile(parent: Path = directory, block: (Path) -> T): T {
        val temp = Files.createTempFile(parent, ".stopbonus-", ".tmp")
        return try {
            block(temp)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    @Synchronized
    override fun close() {
        try {
            lock?.release()
        } finally {
            lockChannel?.close()
            lockChannel = null
            lock = null
        }
    }
}

private fun exists(path: Path): Boolean {
    if (Files.exists(path, NOFOLLOW_LINKS)) return true
    if (Files.notExists(path, NOFOLLOW_LINKS)) return false
    throw IOException("无法确认文件状态 $path")
}

private fun readMetadata(path: Path, mode: String = "ro"): StorageMetadata = connectSqlite(path, mode).use { connection ->
    check(connection.pragma("application_id") == SQLITE_APPLICATION_ID.toLong()) { "文件不是 StopBonus 数据库" }
    val version = connection.pragma("user_version")
    check(version == StopBonusDatabase.Schema.version) { "当前应用不支持该数据库版本 $version" }
    connection.createStatement().use { statement ->
        statement.executeQuery(
            "SELECT instance_id, activated, source_path, source_sha256, source_zone FROM storage_metadata WHERE id = 1",
        ).use { rows ->
            check(rows.next()) { "数据库缺少启用信息" }
            StorageMetadata(
                rows.getString(1), rows.getInt(2) == 1,
                rows.getString(3)?.let(Path::of), rows.getString(4), rows.getString(5)?.let(ZoneId::of),
            )
        }
    }
}

private fun writeMetadata(path: Path, metadata: StorageMetadata) {
    connectSqlite(path, "rw").use { connection ->
        connection.prepareStatement("INSERT INTO storage_metadata VALUES (1, ?, ?, ?, ?, ?)").use { query ->
            query.setString(1, metadata.instanceId)
            query.setInt(2, if (metadata.activated) 1 else 0)
            query.setString(3, metadata.source?.toString())
            query.setString(4, metadata.digest)
            query.setString(5, metadata.zone?.id)
            query.executeUpdate()
        }
    }
}

private fun Connection.pragma(name: String): Long = createStatement().use { statement ->
    statement.executeQuery("PRAGMA $name").use { rows ->
        check(rows.next())
        rows.getLong(1)
    }
}

private fun validate(path: Path) {
    connectSqlite(path, "ro").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA integrity_check").use {
                check(it.next() && it.getString(1) == "ok" && !it.next()) { "SQLite 完整性校验失败" }
            }
            statement.executeQuery("PRAGMA foreign_key_check").use {
                check(!it.next()) { "SQLite 关联校验失败" }
            }
        }
    }
}

private fun copyDatabase(source: Path, target: Path) {
    connectSqlite(source, "ro").use { connection ->
        connection.prepareStatement("VACUUM INTO ?").use {
            it.setString(1, target.toAbsolutePath().toString())
            it.execute()
        }
    }
}

private fun publish(source: Path, target: Path, replace: Boolean = false) {
    check(replace || !exists(target)) { "目标文件已存在 $target" }
    forceFile(source)
    if (replace) Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
    else Files.move(source, target, ATOMIC_MOVE)
    forceDirectory(target.parent)
}

internal fun forceFile(path: Path) = FileChannel.open(path, WRITE).use { it.force(true) }

private fun forceDirectory(path: Path) {
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
    FileChannel.open(path).use { it.force(true) }
}

internal fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
    }
    return HexFormat.of().formatHex(digest.digest())
}
