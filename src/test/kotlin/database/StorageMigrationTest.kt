package database

import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StorageMigrationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun legacyDataSurvivesPreviewActivationAndReopen() = runBlocking {
        val directory = temporary.newFolder("current").toPath()
        val source = createLegacy(temporary.newFolder("old").toPath(), withRemark = true)
        val originalHash = sha256(source)
        val zone = ZoneId.of("Asia/Shanghai")

        StorageManager(directory, listOf(source)).use { storage ->
            assertEquals(listOf(source.toRealPath()), assertIs<StorageStatus.Legacy>(storage.inspect()).sources)
            val stages = mutableListOf<MigrationStage>()
            val preview = storage.stage(source, zone, OverlapResolution.REQUIRE_CONFIRMATION, onStage = { stages += it })
            assertEquals(listOf(MigrationStage.BACKUP, MigrationStage.IMPORT, MigrationStage.VERIFY), stages)
            assertEquals(1L, preview.records)
            assertEquals(Instant.parse("1969-12-31T16:00:00.123456789Z"), preview.earliest)
            assertFailsWith<Exception> { storage.open() }
            assertIs<StorageStatus.Pending>(storage.inspect())
            assertTrue(Files.list(storage.backups).use { files -> files.anyMatch { sha256(it) == originalHash } })
        }

        StorageManager(directory, listOf(source)).use { storage ->
            assertIs<StorageStatus.Pending>(storage.inspect())
            storage.activate()
            assertEquals(StorageStatus.Active(null), storage.inspect())
            storage.open().use { database ->
                assertEquals("账号🎮", database.accounts().single().name)
                val record = database.records(7).single()
                assertEquals(13L, record.id)
                assertEquals(Instant.parse("1969-12-31T16:00:00.123456789Z"), record.startTime)
                assertEquals(Duration.ofNanos(90_000_000_001), record.duration)
                assertEquals(8u, record.score)
                assertEquals("两行\n备注", record.remark)
                assertEquals(listOf("道具"), record.weapons.map { it.name })
                assertEquals(1, database.recordsInRange(7, record.startTime, record.startTime).size)
                assertEquals(101L, database.createAccount("下一个账号").id)
                database.deleteWeapon(7, 9)
                assertTrue(database.records(7).single().weapons.isEmpty())
                database.deleteAccount(7)
                assertTrue(database.records(7).isEmpty())
            }
        }
        assertEquals(originalHash, sha256(source))
    }

    @Test
    fun changedSourceRequiresNewPreviewAndMissingRemarkKeepsDefault() = runBlocking {
        val directory = temporary.newFolder("target").toPath()
        val source = createLegacy(temporary.newFolder("legacy").toPath(), withRemark = false)
        StorageManager(directory, listOf(source)).use { storage ->
            storage.stage(source, ZoneId.of("UTC"), OverlapResolution.REQUIRE_CONFIRMATION)
            connectLegacyForTest(source).use { database ->
                database.createStatement().use { it.executeUpdate("UPDATE BONUS.ACCOUNTS SET NAME = '更新后的账号'") }
            }
            assertFailsWith<IllegalStateException> { storage.activate() }
            storage.discardPreview()
            storage.stage(source, ZoneId.of("UTC"), OverlapResolution.REQUIRE_CONFIRMATION)
            storage.activate()
            storage.open().use { database ->
                assertEquals("更新后的账号", database.accounts().single().name)
                assertEquals("", database.records(7).single().remark)
            }
        }
    }

    @Test
    fun backupRestoresCommittedRecordsAndPreservesReplacedDatabase() = runBlocking {
        val directory = temporary.newFolder("storage").toPath()
        StorageManager(directory, emptyList()).use { storage ->
            assertEquals(StorageStatus.Empty, storage.inspect())
            storage.createEmpty()
            storage.open().use { database ->
                val account = database.createAccount("测试")
                val weapon = database.createWeapon(account.id, "武器")
                val start = Instant.parse("2026-01-01T00:00:00Z")
                assertFailsWith<IllegalStateException> {
                    database.createRecord(account.id, start, start.plusSeconds(1), Duration.ofSeconds(1), 4u, "", -1)
                }
                assertTrue(database.records(account.id).isEmpty())
                database.createRecord(account.id, start, start.plusSeconds(1), Duration.ofSeconds(1), 4u, "", weapon.id)
            }
            val backup = storage.createBackup(temporary.newFolder("backup").toPath())
            storage.open().use { database -> database.deleteAccount(database.accounts().single().id) }
            storage.restore(backup)
            storage.open().use { database ->
                val account = database.accounts().single()
                assertEquals("武器", database.records(account.id).single().weapons.single().name)
            }
            assertTrue(Files.list(storage.backups).use { files ->
                files.anyMatch { it.fileName.toString().startsWith("before-restore-") }
            })
        }
    }

    @Test
    fun activeMarkerBlocksEmptyCreationWhenDatabaseIsMissing() {
        val directory = temporary.newFolder("missing").toPath()
        StorageManager(directory, emptyList()).use { storage -> storage.createEmpty() }
        Files.delete(directory.resolve("bonus.sqlite"))
        StorageManager(directory, emptyList()).use { storage ->
            assertFailsWith<IllegalStateException> { storage.inspect() }
            assertFailsWith<IllegalStateException> { storage.createEmpty() }
        }
    }

    @Test
    fun cancelledImportAndSecondInstanceLeaveSourceAvailableForRetry() {
        val directory = temporary.newFolder("cancelled").toPath()
        val source = createLegacy(temporary.newFolder("source").toPath(), withRemark = true)
        val digest = sha256(source)
        StorageManager(directory, listOf(source)).use { storage ->
            assertIs<StorageStatus.Legacy>(storage.inspect())
            StorageManager(directory, listOf(source)).use { second ->
                assertFailsWith<IllegalStateException> { second.inspect() }
            }
            var checks = 0
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                storage.stage(source, ZoneId.of("UTC"), OverlapResolution.REQUIRE_CONFIRMATION) {
                    if (++checks == 3) throw kotlinx.coroutines.CancellationException("取消")
                }
            }
            assertEquals(digest, sha256(source))
            assertIs<StorageStatus.Legacy>(storage.inspect())
            val preview = storage.stage(source, ZoneId.of("UTC"), OverlapResolution.REQUIRE_CONFIRMATION)
            assertEquals(1L, preview.records)
            Files.delete(preview.backup)
            assertFailsWith<IllegalStateException> { storage.activate() }
            assertIs<StorageStatus.Pending>(storage.inspect())
        }
    }

    private fun createLegacy(directory: Path, withRemark: Boolean): Path {
        val path = directory.resolve(LEGACY_FILE_NAME)
        val remark = if (withRemark) ", REMARK VARCHAR(500) NOT NULL" else ""
        val remarkValue = if (withRemark) ", '两行\n备注'" else ""
        connectLegacyForTest(path).use { connection ->
            connection.createStatement().use { sql ->
                sql.execute("CREATE SCHEMA BONUS")
                sql.execute("""CREATE TABLE BONUS.ACCOUNTS (
                    ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, NAME VARCHAR(50) NOT NULL,
                    CREATE_TIME TIMESTAMP(9) NOT NULL, LAST_UPDATED_TIME TIMESTAMP(9) NOT NULL)""")
                sql.execute("""CREATE TABLE BONUS.WEAPONS (
                    ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ACCOUNT_ID INT NOT NULL REFERENCES BONUS.ACCOUNTS(ID), NAME VARCHAR(1000) NOT NULL,
                    CREATE_TIME TIMESTAMP(9) NOT NULL, LAST_UPDATED_TIME TIMESTAMP(9) NOT NULL)""")
                sql.execute("""CREATE TABLE BONUS.BONUSRECORDS (
                    ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ACCOUNT_ID INT NOT NULL REFERENCES BONUS.ACCOUNTS(ID),
                    START_TIME TIMESTAMP(9) NOT NULL, END_TIME TIMESTAMP(9) NOT NULL,
                    DURATION BIGINT NOT NULL, SCORE BIGINT NOT NULL,
                    CREATE_TIME TIMESTAMP(9) NOT NULL, LAST_UPDATED_TIME TIMESTAMP(9) NOT NULL$remark)""")
                sql.execute("""CREATE TABLE BONUS.BONUSRECORDWEAPONS (
                    RECORD_ID INT NOT NULL REFERENCES BONUS.BONUSRECORDS(ID),
                    WEAPON_ID INT NOT NULL REFERENCES BONUS.WEAPONS(ID), PRIMARY KEY (RECORD_ID, WEAPON_ID))""")
                val time = "TIMESTAMP '1970-01-01 00:00:00.123456789'"
                sql.execute("INSERT INTO BONUS.ACCOUNTS VALUES (7, '账号🎮', $time, $time)")
                sql.execute("ALTER TABLE BONUS.ACCOUNTS ALTER COLUMN ID RESTART WITH 101")
                sql.execute("INSERT INTO BONUS.WEAPONS VALUES (9, 7, '道具', $time, $time)")
                sql.execute("""INSERT INTO BONUS.BONUSRECORDS VALUES
                    (13, 7, $time, TIMESTAMP '1970-01-01 00:01:30.123456790', 90000000001, 8, $time, $time$remarkValue)""")
                sql.execute("INSERT INTO BONUS.BONUSRECORDWEAPONS VALUES (13, 9)")
            }
        }
        return path
    }

    private fun connectLegacyForTest(path: Path) =
        DriverManager.getConnection("jdbc:h2:file:${path.toAbsolutePath().toString().removeSuffix(".mv.db")}")
}
