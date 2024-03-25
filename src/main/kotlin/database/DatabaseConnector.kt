package database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import database.entity.AllTables
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.math.max

class DatabaseOperator(
    val schema: Schema,
    val database: Database,
    private val dataSource: HikariDataSource,
) {
    fun close() {
        dataSource.close()
    }

    suspend inline fun <T> inSuspendedTransaction(
        context: CoroutineContext? = null,
        transactionIsolation: Int? = null,
        crossinline statement: suspend Transaction.() -> T
    ): T = newSuspendedTransaction(
        context = context,
        db = database,
        transactionIsolation = transactionIsolation,
    ) { statement() }

    inline fun <T> inTransaction(
        transactionIsolation: Int = database.transactionManager.defaultIsolationLevel,
        readOnly: Boolean = database.transactionManager.defaultReadOnly,
        crossinline statement: Transaction.() -> T
    ): T = transaction(
        db = database,
        readOnly = readOnly,
        transactionIsolation = transactionIsolation,
    ) { statement() }


}

private val DEFAULT_DATA_DIR = Path("./data")
private const val DATA_FILE_NAME = "bonus.d"

fun connectDatabaseOperator(dataDir: Path = DEFAULT_DATA_DIR, schemaName: String): DatabaseOperator {
    val schema = Schema(schemaName)

    val config = hikariConfig {
        jdbcUrl = "jdbc:h2:file:${(dataDir / DATA_FILE_NAME).pathString}"
        driverClassName = "org.h2.Driver"
        poolName = "BonusDBPool"
        minimumIdle = max(1, Runtime.getRuntime().availableProcessors() / 2)
    }

    val dataSource = HikariDataSource(config)

    val database = Database.connect(
        datasource = dataSource,
        databaseConfig = DatabaseConfig {
            // set other parameters here
            defaultFetchSize = 100
            keepLoadedReferencesOutOfTransaction = true
            defaultMaxRepetitionDelay = 6000
        }
    )

    database.init(schema)

    return DatabaseOperator(schema, database, dataSource)
}

private inline fun hikariConfig(block: HikariConfig.() -> Unit): HikariConfig = HikariConfig().apply {
    block()
}


fun Database.init(schema: Schema) {
    transaction(this) {
        if (!schema.exists()) {
            SchemaUtils.createSchema(schema)
        }
        SchemaUtils.setSchema(schema)
        SchemaUtils.createMissingTablesAndColumns(*AllTables)
    }
}
