package database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import config.ClockProvider
import database.entity.AccountView
import database.entity.BonusRecordView
import database.entity.WeaponView
import database.sql.StopBonusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.Properties

internal const val SQLITE_APPLICATION_ID = 0x53424F4E

class DatabaseOperator internal constructor(path: Path) : AutoCloseable {
    private val driver = JdbcSqliteDriver(sqliteUrl(path, "rw"), sqliteProperties())
    private val database = StopBonusDatabase(driver)
    private val queries = database.storageQueries
    private val mutex = Mutex()
    private var closed = false

    suspend fun accounts(): List<AccountView> = access {
        queries.listAccounts(::AccountView).executeAsList()
    }

    suspend fun createAccount(name: String): AccountView = access {
        require(name.length <= 50) { "账号名最多 50 个字符" }
        database.transactionWithResult {
            val now = encodeInstant(ClockProvider.now())
            queries.insertAccount(name, now, now)
            AccountView(queries.lastInsertedId().executeAsOne(), name)
        }
    }

    suspend fun deleteAccount(id: Long): Unit = access { queries.deleteAccount(id) }

    suspend fun weapons(accountId: Long): List<WeaponView> = access {
        queries.listWeapons(accountId, ::WeaponView).executeAsList()
    }

    suspend fun createWeapon(accountId: Long, name: String): WeaponView = access {
        require(name.length <= 1000) { "武器名最多 1000 个字符" }
        database.transactionWithResult {
            val now = encodeInstant(ClockProvider.now())
            queries.insertWeapon(accountId, name, now, now)
            WeaponView(queries.lastInsertedId().executeAsOne(), name)
        }
    }

    suspend fun deleteWeapon(accountId: Long, id: Long): Unit = access {
        queries.deleteWeapon(accountId, id)
    }

    suspend fun records(accountId: Long): List<BonusRecordView> = access {
        database.transactionWithResult {
            val weaponsByRecord = queries.listRecordWeapons(accountId) { recordId, weaponId, name ->
                recordId to WeaponView(weaponId, name)
            }.executeAsList().groupBy({ it.first }, { it.second })
            queries.listRecords(accountId) { id, start, end, duration, score, remark ->
                BonusRecordView(
                    id, decodeInstant(start), decodeInstant(end), Duration.ofNanos(duration),
                    score.toUInt(), remark, weaponsByRecord[id].orEmpty(),
                )
            }.executeAsList()
        }
    }

    suspend fun createRecord(
        accountId: Long,
        startTime: Instant,
        endTime: Instant,
        duration: Duration,
        score: UInt,
        remark: String,
        weaponId: Long?,
    ) = access {
        database.transaction {
            val now = encodeInstant(ClockProvider.now())
            queries.insertRecord(
                accountId, encodeInstant(startTime), encodeInstant(endTime),
                duration.toNanos(), score.toLong(), remark, now, now,
            )
            if (weaponId != null) {
                check(queries.findWeapon(accountId, weaponId).executeAsOneOrNull() != null) { "所选武器已不可用" }
                queries.linkWeapon(queries.lastInsertedId().executeAsOne(), weaponId)
            }
        }
    }

    suspend fun deleteRecord(accountId: Long, id: Long): Unit = access {
        queries.deleteRecord(accountId, id)
    }

    suspend fun recordsInRange(accountId: Long, start: Instant, end: Instant): List<RecordTime> = access {
        queries.recordsInRange(accountId, encodeInstant(start), encodeInstant(end)) { time, nanos ->
            RecordTime(decodeInstant(time), Duration.ofNanos(nanos))
        }.executeAsList()
    }

    private suspend fun <T> access(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed)
            block()
        }
    }

    override fun close() {
        runBlocking {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    driver.close()
                }
            }
        }
    }
}

data class RecordTime(val startTime: Instant, val duration: Duration)

internal fun encodeInstant(value: Instant): String =
    (value.epochSecond - Instant.MIN.epochSecond).toString().padStart(17, '0') +
        value.nano.toString().padStart(9, '0')

internal fun decodeInstant(value: String): Instant {
    require(value.length == 26 && value.all { it in '0'..'9' })
    return Instant.ofEpochSecond(
        value.substring(0, 17).toLong() + Instant.MIN.epochSecond,
        value.substring(17).toLong(),
    )
}

internal fun sqliteUrl(path: Path, mode: String): String =
    "jdbc:sqlite:${path.toAbsolutePath().normalize().toUri().toASCIIString()}?mode=$mode"

internal fun sqliteProperties() = Properties().apply {
    setProperty("foreign_keys", "true")
    setProperty("busy_timeout", "5000")
    setProperty("synchronous", "FULL")
}

internal fun connectSqlite(path: Path, mode: String): Connection =
    DriverManager.getConnection(sqliteUrl(path, mode), sqliteProperties())

internal fun createSqliteSchema(path: Path) {
    JdbcSqliteDriver(sqliteUrl(path, "rw"), sqliteProperties()).use { driver ->
        StopBonusDatabase(driver).transaction {
            StopBonusDatabase.Schema.create(driver).value
            driver.execute(null, "PRAGMA application_id=$SQLITE_APPLICATION_ID", 0).value
            driver.execute(null, "PRAGMA user_version=${StopBonusDatabase.Schema.version}", 0).value
        }
    }
}
