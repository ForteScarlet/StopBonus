package database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import database.entity.AllTables
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
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
class DatabaseOperator(
    val database: Database,
    private val dataSource: HikariDataSource,
) : AutoCloseable {
    override fun close() {
        dataSource.close()
    }

    /**
     * 在挂起事务中执行数据库操作（用于协程环境）
     */
    suspend inline fun <T> inSuspendedTransaction(
        context: CoroutineContext? = null,
        transactionIsolation: Int? = null,
        crossinline statement: suspend Transaction.() -> T
    ): T = if (context == null) {
        suspendTransaction(
            db = database,
            transactionIsolation = transactionIsolation,
        ) { statement() }
    } else {
        withContext(context) {
            suspendTransaction(
                db = database,
                transactionIsolation = transactionIsolation,
            ) { statement() }
        }
    }

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

    return DatabaseOperator(database, source)
}

private inline fun hikariConfig(block: HikariConfig.() -> Unit): HikariConfig = HikariConfig().apply {
    block()
}


/**
 * 初始化数据库 schema 和表结构。
 *
 * 这里只负责创建首次运行所需的表；已有数据库的结构演进必须通过显式、可审计的迁移完成，
 * 避免启动时自动执行不可逆的 schema 修补。
 */
fun Database.init(schema: Schema) {
    transaction(this) {
        SchemaUtils.createSchema(schema)
        SchemaUtils.setSchema(schema)
        SchemaUtils.create(*AllTables, inBatch = true)
    }
}
