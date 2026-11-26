package database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseOperatorTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun closeWaitsForBothConcurrentQueries() = runBlocking {
        val path = temporary.newFile().toPath()
        createSqliteSchema(path)

        val delegate = JdbcSqliteDriver(sqliteUrl(path, "rw"), sqliteProperties())
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val finished = AtomicInteger()
        val closes = AtomicInteger()
        val driver = object : SqlDriver by delegate {
            override fun <R> executeQuery(
                identifier: Int?,
                sql: String,
                mapper: (SqlCursor) -> QueryResult<R>,
                parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?,
            ): QueryResult<R> {
                started.countDown()
                check(release.await(10, SECONDS)) { "查询等待放行超时" }
                return try {
                    delegate.executeQuery(identifier, sql, mapper, parameters, binders)
                } finally {
                    finished.incrementAndGet()
                }
            }

            override fun close() {
                assertEquals(2, finished.get())
                closes.incrementAndGet()
                delegate.close()
            }
        }

        DatabaseOperator(driver).use { database ->
            val reads = List(2) { async { database.accounts() } }
            val closeStarted = CompletableDeferred<Unit>()
            try {
                assertTrue(withContext(Dispatchers.IO) { started.await(5, SECONDS) })
                val close = async(Dispatchers.IO) {
                    closeStarted.complete(Unit)
                    database.close()
                }
                closeStarted.await()
                assertNull(withTimeoutOrNull(100) { close.await() })

                release.countDown()
                reads.awaitAll().forEach { assertTrue(it.isEmpty()) }
                close.await()
                assertFailsWith<IllegalStateException> { database.accounts() }
            } finally {
                release.countDown()
            }
        }
        assertEquals(1, closes.get())
    }

    @Test
    fun concurrentWritesReturnTheirOwnGeneratedIds() = runBlocking {
        val path = temporary.newFile().toPath()
        createSqliteSchema(path)

        DatabaseOperator(path).use { database ->
            val inserted = List(8) { index -> async { database.createAccount("账号 $index") } }.awaitAll()

            assertEquals(inserted.size, inserted.map { it.id }.toSet().size)
            assertEquals(inserted.toSet(), database.accounts().toSet())
        }
    }
}
