package database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import database.entity.AllTables
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.pathString

/**
 * 数据库操作封装类
 *
 * 提供事务管理和数据库操作的统一入口
 */
class DatabaseOperator(val database: Database) {
    fun close() {
    }

    /**
     * 在挂起事务中执行数据库操作（用于协程环境）
     */
    suspend inline fun <T> inSuspendedTransaction(
        context: CoroutineContext? = null,
        transactionIsolation: Int? = null,
        crossinline statement: suspend Transaction.() -> T
    ): T = newSuspendedTransaction(
        context = context,
        db = database,
        transactionIsolation = transactionIsolation,
    ) { statement() }

    /**
     * 在同步事务中执行数据库操作
     */
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

/**
 * 创建数据库连接操作器
 *
 * 使用 HikariCP 连接池和 H2 嵌入式数据库
 *
 * @param dataDir 数据存储目录
 * @param schemaName 数据库 schema 名称
 * @return 数据库操作器实例
 */
fun connectDatabaseOperator(dataDir: Path = DEFAULT_DATA_DIR, schemaName: String): DatabaseOperator {
    val schema = Schema(schemaName)
    // val dialect = H2Dialect()

    val jdbcUrl = "jdbc:h2:${(dataDir / DATA_FILE_NAME).pathString};" +
            "DB_CLOSE_DELAY=-1;" +
            "DB_CLOSE_ON_EXIT=FALSE;" +
            "TRACE_LEVEL_FILE=3;" +
            "AUTO_RECONNECT=TRUE;"

    // val connection

    val config = hikariConfig {
        this.jdbcUrl = jdbcUrl
        driverClassName = "org.h2.Driver"
        // this.threadFactory
        connectionInitSql = "CREATE SCHEMA IF NOT EXISTS $schemaName; SET SCHEMA $schemaName"
        minimumIdle = 1
        maximumPoolSize = 1
    }

    val source = HikariDataSource(config)

    val database = Database.connect(
        source,
        setupConnection = { println("Setup: $it") },
        databaseConfig = DatabaseConfig {
            // set other parameters here
            defaultFetchSize = 100
            //keepLoadedReferencesOutOfTransaction = true
            defaultMaxRetryDelay = 6000
        }
    )


    database.init(schema)

    return DatabaseOperator(database)
}

private inline fun hikariConfig(block: HikariConfig.() -> Unit): HikariConfig = HikariConfig().apply {
    block()
}


/**
 * 初始化数据库 schema 和表结构
 */
fun Database.init(schema: Schema) {
    transaction(this) {
        if (!schema.exists()) {
            SchemaUtils.createSchema(schema)
        }
        SchemaUtils.setSchema(schema)
        SchemaUtils.createMissingTablesAndColumns(
            tables = AllTables,
            inBatch = true,
            withLogs = true,
        )
    }
}
