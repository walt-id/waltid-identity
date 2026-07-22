package id.walt.wallet2.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import java.sql.SQLException

private val log = KotlinLogging.logger {}

private fun ensureSqliteParentDir(jdbcUrl: String) {
    val prefix = "jdbc:sqlite:"
    if (!jdbcUrl.startsWith(prefix)) return

    val path = jdbcUrl.removePrefix(prefix).substringBefore('?')
    if (path.isBlank() || path.startsWith(":")) return // e.g. ":memory:"

    val parent = File(path).absoluteFile.parentFile ?: return
    if (!parent.exists() && parent.mkdirs()) {
        log.info { "Created SQLite data directory: $parent" }
    }
}

/**
 * HOCON configuration for the wallet2 persistence layer.
 *
 * Example (SQLite — default):
 * ```hocon
 * wallet2-persistence {
 *   jdbcUrl = "jdbc:sqlite:wallet2.db"
 *   driverClassName = "org.sqlite.JDBC"
 * }
 * ```
 *
 * Example (PostgreSQL):
 * ```hocon
 * wallet2-persistence {
 *   jdbcUrl = "jdbc:postgresql://localhost:5432/wallet2"
 *   driverClassName = "org.postgresql.Driver"
 *   username = "wallet"
 *   password = "secret"
 *   maximumPoolSize = 10
 * }
 * ```
 */
@Serializable
data class Wallet2PersistenceConfig(
    val jdbcUrl: String = "jdbc:sqlite:wallet2.db",
    val driverClassName: String = "org.sqlite.JDBC",
    val username: String = "",
    val password: String = "",
    val maximumPoolSize: Int = 5,
    val minimumIdle: Int = 1,
)

/**
 * Initialises the Exposed [Database] connection pool and creates any missing tables.
 *
 * Call once at application startup before using any Exposed-backed store.
 *
 * @param config Datasource configuration. Defaults to an in-file SQLite database.
 * @return The initialised [Database] instance (also set as Exposed thread-local default).
 */
fun initWallet2Database(
    config: Wallet2PersistenceConfig = Wallet2PersistenceConfig()
): Database {
    log.info { "Initialising wallet2 database: ${config.jdbcUrl}" }

    ensureSqliteParentDir(config.jdbcUrl)

    val hikari = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        driverClassName = config.driverClassName
        if (config.username.isNotBlank()) username = config.username
        if (config.password.isNotBlank()) password = config.password
        maximumPoolSize = config.maximumPoolSize
        minimumIdle = config.minimumIdle
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_SERIALIZABLE"
        if (config.jdbcUrl.startsWith("jdbc:sqlite:")) {
            connectionInitSql = "PRAGMA busy_timeout=30000"
        }
        validate()
    }

    val db = Database.connect(HikariDataSource(hikari))

    synchronized(schemaMigrationLock) {
        createOrMigrateSchema(db, config.jdbcUrl)
    }

    return db
}

private fun createOrMigrateSchema(db: Database, jdbcUrl: String) {
    repeat(SCHEMA_MIGRATION_ATTEMPTS) { attempt ->
        try {
            transaction(db) {
                if (jdbcUrl.startsWith("jdbc:postgresql:")) {
                    exec("SELECT pg_advisory_xact_lock($SCHEMA_MIGRATION_LOCK_ID)")
                }
                SchemaUtils.createMissingTablesAndColumns(*Wallet2Tables.ALL)
            }
            log.info { "wallet2 schema ready" }
            return
        } catch (cause: Exception) {
            if (attempt == SCHEMA_MIGRATION_ATTEMPTS - 1 || !cause.isRetryableSchemaRace()) throw cause
            Thread.sleep(SCHEMA_MIGRATION_RETRY_MILLIS * (attempt + 1))
        }
    }
}

private fun Throwable.isRetryableSchemaRace(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SQLException) {
            if (current.sqlState in setOf("40001", "40P01", "42701", "55P03")) return true
            val message = current.message.orEmpty().lowercase()
            if (listOf(
                    "already exists",
                    "duplicate column",
                    "database is locked",
                    "database is busy",
                    "schema is locked",
                    "table is locked",
                    "no transaction is active",
                ).any(message::contains)
            ) {
                return true
            }
        }
        current = current.cause
    }
    return false
}

private const val SCHEMA_MIGRATION_ATTEMPTS = 5
private const val SCHEMA_MIGRATION_RETRY_MILLIS = 100L
private const val SCHEMA_MIGRATION_LOCK_ID = 0x57414c5432L
private val schemaMigrationLock = Any()
